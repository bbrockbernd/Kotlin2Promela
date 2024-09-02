package com.example.kotlin2promela

import com.example.kotlin2promela.graph.ModelGenerator
import com.example.kotlin2promela.graph.processing.GraphBuilder
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Validator(e.project!!).validate(getRelevantFiles(e.project!!, null))
    }
    
    private fun validateAll(e: AnActionEvent) {
        val graph = GraphBuilder(e.project!!)
            .setRelevantFiles(getRelevantFiles(e.project!!, null))
            .initGraph()
            .channelFlow()
            .unNestCalls()
            .pruneSyncCalls()
            .get()

        graph.getFunctions()
            .filter { it.calledBy.isEmpty() && it.importantParameters.isEmpty() && it.implicitParameters.isEmpty() }
            .forEach {
                val model = ModelGenerator(graph).generateForEntryPoint(it)
                VerboseLogger.log("---------------------------------MODEL-------------------------------------------")
                VerboseLogger.log(model)
                VerboseLogger.log("---------------------------------------------------------------------------------")
                executeModel(model)
            }
        VerboseLogger.log("Done")
    }
    
    private fun getRelevantFiles(project: Project, scope: AnalysisScope?): List<VirtualFile> {
        val relevantFiles = mutableListOf<VirtualFile>()
        ProjectFileIndex.getInstance(project).iterateContent {
            if (it.fileType is KotlinFileType && scope?.contains(it) != false) {
                relevantFiles.add(it)
            }
            true
        }
        return relevantFiles
    }
    
    private fun executeModel(model: String) {
        // Prepare spin executable
        val inputStream = this.javaClass.getResourceAsStream("/executables/spin")
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
        
        // get results
        val result = process.inputStream.bufferedReader().readText()
        VerboseLogger.log(result)
    }
}