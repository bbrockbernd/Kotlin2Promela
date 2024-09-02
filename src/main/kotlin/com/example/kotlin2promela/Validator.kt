package com.example.kotlin2promela

import ai.grazie.utils.capitalize
import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.ModelGenerator
import com.example.kotlin2promela.graph.processing.GraphBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException


class Validator(val project: Project) {
    enum class ErrorType {
        NoError,
        KotlinCompileError,
        KotlinRuntimeError,
        GraphError,
        WriteToPromError,
        InvalidModelError,
        UnknownResultError
    }

    @Serializable
    data class Configuration(
        val deadlock: Boolean,
        val nFunctions: Int,
        val nCoroutines: Int,
        val nChannels: Int,
        val nClasses: Int
    )
    
    private var df = mapOf(
        "name" to emptyList<String>(),
        "nFunctions" to emptyList<Int>(),
        "nCoroutines" to emptyList<Int>(),
        "nChannels" to emptyList<Int>(),
        "nClasses" to emptyList<Int>(),
        "should" to emptyList<Boolean>(),
        "error" to emptyList<ErrorType>(),
        "actual" to emptyList<Boolean>(),
        "detected" to emptyList<Boolean>()
    ).toDataFrame()
    
    fun validate(files: List<VirtualFile>) = runBlocking {
        VerboseLogger.enabled = true
        files.filter{it.name.contains("test")}
//            .filter{it.name.contains("test101.kt")}
            .forEachIndexed { index, file ->
                println("Runnin test ${file.name}...")
                runTest(file) 
                
                if(index % 20 == 0) df.takeLast(20).print()
            }
        val file = File("/Users/Bob.Brockbernd/IdeaProjects/DeadlockTestRepo/results.csv")
        df.writeCSV(file)
        println("Done")
    }
    
    private suspend fun runTest(vFile: VirtualFile) {
        // Parse settings and set conf
        val testName = vFile.name.split(".").first()
        val contents = VfsUtil.loadText(vFile)
        val jsonString = contents.lines()[1]
        val conf = Json.decodeFromString<Configuration>(jsonString)

        // Does the test compile?
        val buildSuccess = doesItBuild(testName)
        if (!buildSuccess) { 
            df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.KotlinCompileError, false, false)
            return
        }

        // Does the test deadlock?
        val actualDL = try {
            doesItDeadlock(vFile)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.KotlinRuntimeError, false, false)
            return
        }
        
        // Translation error?
        val graph = try {
            generateGraph(vFile)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.GraphError, actualDL, false)
            return
        }


        graph.getFunctions()
            .filter { it.calledBy.isEmpty() && it.importantParameters.isEmpty() && it.implicitParameters.isEmpty() }
            .forEach {
                // generate model for root node, might turn into a modelError
                val model = try {
                    ModelGenerator(graph).generateForEntryPoint(it)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.WriteToPromError, actualDL, false)
                    return@forEach
                }
                
                println("------------------------------MODEL--------------------------------")
                println(model)

                val modelResults = try {
                    executeModel(model)
                } catch (e: CancellationException) { throw e } catch (e: IllegalArgumentException) {
                    df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.InvalidModelError, actualDL, false)
                    return@forEach
                } catch (e: IllegalStateException) {
                    df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.NoError, actualDL, true)
                    return@forEach
                }
                
                val detectedDeadlock = try {
                    checkModelDeadlockResult(modelResults)
                } catch (e: CancellationException) { throw e } catch (e: IllegalStateException) {
                    df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.UnknownResultError, actualDL, false)
                    return@forEach
                }
                df = df.append(testName, conf.nFunctions, conf.nCoroutines, conf.nChannels, conf.nClasses, conf.deadlock, ErrorType.NoError, actualDL, detectedDeadlock)
            }
    }
    
    private fun generateGraph(vFile: VirtualFile): DeadlockGraph {
        return GraphBuilder(project)
            .setRelevantFiles(listOf(vFile))
            .initGraph()
            .channelFlow()
            .unNestCalls()
            .pruneSyncCalls()
            .get()
    }
    
    // true = deadlock
    private fun checkModelDeadlockResult(spinOutput: String): Boolean {
        val first = spinOutput.lines()[0]
        if (first.contains("invalid end state")) return true
        val second = spinOutput.lines()[1]
        if (first.trim().isEmpty() && second.contains("Spin Version")) return false
        throw IllegalStateException("SPIN result not recognised")
    }
    
    private suspend fun doesItDeadlock(vFile: VirtualFile): Boolean = coroutineScope {
        val testName = vFile.name.split(".").first()
        
        val runProcessBuilder = ProcessBuilder()
        runProcessBuilder.command(listOf(
            "/Users/Bob.Brockbernd/Library/Java/JavaVirtualMachines/corretto-17.0.10/Contents/Home/bin/java",
            "-classpath",
            "/Users/Bob.Brockbernd/IdeaProjects/DeadlockTestRepo/build/classes/kotlin/main" +
                    ":/Users/Bob.Brockbernd/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.10/9120f9dd349d5c67fa6710c4049c3128ef6b51db/kotlin-stdlib-2.0.10.jar" +
                    ":/Users/Bob.Brockbernd/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.1/bb0e192bd7c2b6b8217440d36e9758e377e450/kotlinx-coroutines-core-jvm-1.8.1.jar" +
                    ":/Users/Bob.Brockbernd/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/23.0.0/8cc20c07506ec18e0834947b84a864bfc094484e/annotations-23.0.0.jar",
            "org.example.generated.$testName.${testName.capitalize()}Kt",
        ))
        runProcessBuilder.redirectErrorStream(true)
        val runProcess = runProcessBuilder.start()
        val timer = launch { 
            delay(10000) 
            runProcess.destroy()
        }
        runProcess.awaitExit()
        timer.cancelAndJoin()
        val exitVal = runProcess.exitValue()
        if (exitVal == 0) return@coroutineScope false
        if (exitVal == 143) return@coroutineScope true
        throw IllegalStateException("Execution error")
    }
    
    private suspend fun doesItBuild(testName: String): Boolean {
        val buildProcessBuilder = ProcessBuilder()
        buildProcessBuilder.command(listOf(
            "./gradlew",
            "build",
            "-PkotlinFile=generated/$testName.kt"
        ))
        buildProcessBuilder.redirectErrorStream(true)
        buildProcessBuilder.directory(File("/Users/Bob.Brockbernd/IdeaProjects/DeadlockTestRepo"))
        val buildProcess = buildProcessBuilder.start()
        buildProcess.awaitExit()
        val exitVal = buildProcess.exitValue()
        return exitVal == 0
    }
    
    private suspend fun executeModel(model: String): String = coroutineScope {
        // Prepare spin executable
        val inputStream = this@Validator.javaClass.getResourceAsStream("/executables/spin")
            ?: throw NullPointerException("Cannot find spin executable")
        val spinFile = File.createTempFile("spin", null)
        inputStream.use { Files.copy(it, spinFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        spinFile.setExecutable(true)

        // write model to file
        val modelFile = File.createTempFile("model${model.hashCode()}", ".pml")
        modelFile.writeText(model)

        // run spin
        val tempWorkingDir = Files.createTempDirectory("spin_dir${model.hashCode()}")
        val processBuilder = ProcessBuilder()
        processBuilder.command(listOf(
            spinFile.absolutePath,
            "-run",
            "-DVECTORSZ=4508",
            "-m10000000",
            "-w26",
            modelFile.absolutePath,
            "-f"
        ))
        processBuilder.directory(tempWorkingDir.toFile())
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        val timer = launch {
            delay(60000)
            process.destroy()
        }
        process.awaitExit()
        timer.cancelAndJoin()
        val exitVal = process.exitValue()
        if (exitVal == 143) throw IllegalStateException("Spin was not finished after 60 seconds")
        if (exitVal != 0) throw IllegalArgumentException("Invalid model")
        // get results
        val result = process.inputStream.bufferedReader().readText()
        return@coroutineScope result
    }
}