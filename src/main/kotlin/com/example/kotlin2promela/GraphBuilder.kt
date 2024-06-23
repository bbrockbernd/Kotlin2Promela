package com.example.kotlin2promela

import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.*
import com.example.kotlin2promela.graph.variablePassing.DLActionArgument
import com.example.kotlin2promela.graph.variablePassing.DLChannelParameter
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument
import com.example.kotlin2promela.graph.variablePassing.DLValConsumer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class GraphBuilder(private val project: Project) {
    private var relevantFiles: List<VirtualFile> = emptyList()
    private var urlToVirtualFileMap: MutableMap<String, VirtualFile> = mutableMapOf()
    private val dlGraph = DeadlockGraph()
    
    fun get(): DeadlockGraph {
        return dlGraph
    }

    fun setRelevantFiles(relevantFiles: List<VirtualFile>): GraphBuilder {
        this.relevantFiles = relevantFiles
        relevantFiles.forEach { urlToVirtualFileMap[it.url] = it }
        return this
    }
    
    fun channelFlow(): GraphBuilder {
        dlGraph.channelInits.forEach { chanInit ->
            val psiElement = chanInit.psiPointer.element!!
            ReferencesSearch.search(psiElement).findAll().forEach { usage ->
                if (usage.element !is KtNameReferenceExpression) { throw IllegalStateException("Expected name reference but is: ${usage.element.javaClass.name}") }
                processUsage(usage.element as KtNameReferenceExpression, chanInit)
            }
        }
        return this
    }
    
    private fun processUsage(usage: KtNameReferenceExpression, chanInit: ChannelInitDLAction) {
        // First check if this usage is in same function
        val usagePsiFun = MyPsiUtils.findParent(usage, { it is KtFunction }, { it is KtFile })!! as KtFunction
        val usageFun = dlGraph.getOrCreateFunction(usagePsiFun)
        val originFun = chanInit.performedIn
        
        val valProducer = if (originFun != usageFun) { // If not pass is implicit
            val path = dlGraph.BFSDown(originFun, usageFun)
            if (path.isEmpty()) throw IllegalStateException("Path must be at least 1 call")

            path.first().implArgs.computeIfAbsent(chanInit.offset) { _ -> 
                DLPassingArgument(chanInit.offset, DLValConsumer.createAndLinkChannelConsumer(chanInit))
            }
            // Hack use chaninit textOffset to differentiate
            path.first().receiving.implicitParameters.computeIfAbsent(chanInit.offset) { _ ->
                DLChannelParameter(
                    chanInit.offset,
                    usage.containingFile.virtualFile.path,
                    null
                )
            }
            
            for (i in 1..path.lastIndex) {
                val param = path[i].performedIn.implicitParameters[chanInit.offset] as DLChannelParameter
                path[i].implArgs.computeIfAbsent(chanInit.offset) { _ ->
                    DLPassingArgument(chanInit.offset, DLValConsumer.createAndLinkChannelConsumer(param))
                }
                path[i].receiving.implicitParameters.computeIfAbsent(chanInit.offset) { _ ->
                    DLChannelParameter(
                        chanInit.offset,
                        usage.containingFile.virtualFile.path,
                        null
                    )
                }
            }
            path.last().receiving.implicitParameters[chanInit.offset] as DLChannelParameter
        } else chanInit
        
        
        when {
//                ElementFilters.isSendUsage(usage) -> processSendAction(usage, usageContainer)
//                ElementFilters.isReceiveUsage(usage) -> processReceiveAction(usage, usageContainer)
        }
        
    }
    
    

    fun initGraph(): GraphBuilder {
        relevantFiles.forEach { file ->
            MyPsiUtils.findFunctionDefinitions(file, project).forEach { fn -> 
                exploreFunctionDeclaration(fn)
            }
        }
        return this
    }
    
    private fun exploreFunctionDeclaration(fn: KtFunction): FunctionNode {
        // init
        val funNode = dlGraph.getOrCreateFunction(fn)
        if (funNode.visited) return funNode
        funNode.visited = true

        // Check all relevant elements in body
        MyPsiUtils.findAllChildren(
            fn.bodyExpression, 
            { ElementFilters.isCall(it) },
            { ElementFilters.isFunction(it) },
            pruneOnCondition = true,
            includeStart = true
        ).forEach { call ->
            val action = exploreFunctionCall(call as KtCallExpression, funNode)
            action?.let { funNode.actionList.add(it) }
        }

        // Order actions
        funNode.actionList.sortBy { it.offset }
        return funNode
    }
    
    private fun exploreFunctionCall(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        // TODO this will probably break when calling a lambda
        return when {
            ElementFilters.isLaunchBuilder(call) || ElementFilters.isAsyncBuilder(call) -> processAsyncCall(call, callerFun)
            ElementFilters.isChannelInit(call) -> processChannelInit(call, callerFun)
            else -> processCall(call, callerFun)
        }
    }    
    
    private fun processChannelInit(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        if (call.parent is KtProperty) {
            val channelInit = ChannelInitDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, (call.parent as KtProperty).createSmartPointer())
            dlGraph.channelInits.add(channelInit)
            return channelInit
        }
        return null
    }
    
    private fun processAsyncCall(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        val ktFunction = MyPsiUtils.getAsyncBuilderLambda(call)!!
        if (relevantFiles.contains(ktFunction.containingFile.virtualFile)) {
            val calledFunNode = exploreFunctionDeclaration(ktFunction)
            return AsyncCallDLAction(
                call.containingFile.virtualFile.path,
                call.textOffset,
                callerFun,
                calledFunNode,
                call.createSmartPointer(),
            )
        }
        return null
    }
    
    private fun processCall(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        val ktFunction = MyPsiUtils.getFunForCall(call)!!
        
        // Create in or out of scope callAction
        val callAction = if (relevantFiles.contains(ktFunction.containingFile.virtualFile)) {
            val calledFunNode = exploreFunctionDeclaration(ktFunction)
            CallDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, calledFunNode, call.createSmartPointer())
        } else if (ElementFilters.isSendCall(call)) {
            ChannelSendDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, call.createSmartPointer())
        } else if (ElementFilters.isReceiveCall(call)) {
            ChannelRecvDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, call.createSmartPointer())
        } else {
            OutOfScopeCallDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, call.createSmartPointer())
        }
        
        // Process arguments
        call.valueArguments.forEach { arg ->
            
            // TODO fix: Hack for lambda arguments (inline maybe??)
            if (arg is KtLambdaArgument) {
                val receivingFun = exploreFunctionDeclaration(arg.getLambdaExpression()!!.functionLiteral)
                val lamCall =
                    CallDLAction(arg.containingFile.virtualFile.path, arg.textOffset, callerFun, receivingFun, call.createSmartPointer())
                callAction.args.add(DLActionArgument(lamCall))

            } else {
                MyPsiUtils.findAllChildren(
                    arg,
                    { ElementFilters.isCall(it) },
                    { ElementFilters.isFunction(it) },
                    pruneOnCondition = true,
                    includeStart = true
                ).forEach { nestedCall ->
                    val action = exploreFunctionCall(nestedCall as KtCallExpression, callerFun)
                    
                    action?.let { callAction.args.add(DLActionArgument(it)) }
                }
            }
        }
        return callAction
    }
}



// SLICER shite for when I change my mind

//val params = SliceAnalysisParams()
//params.dataFlowToThis = false
//params.scope = AnalysisScope(project)
//params.showInstanceDereferences = true
//
//val rootUsage = LanguageSlicing.getProvider(it.psiPointer.element!!).createRootUsage(it.psiPointer.element!!, params)
//val sliceRootNode = SliceRootNode(project, DuplicateMap(), rootUsage)
//val treeStructure = DLTreeStructure(project, sliceRootNode)
//
//val sliceProvider = KotlinSliceProvider()
//val results = sliceProvider.leafAnalyzer.calcLeafExpressions(sliceRootNode, treeStructure, sliceProvider.leafAnalyzer.createMap())
