package com.example.kotlin2promela

import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument

class GraphBuilder(private val project: Project) {
    private var relevantFiles: List<VirtualFile> = emptyList()
    private var urlToVirtualFileMap: MutableMap<String, VirtualFile> = mutableMapOf()
    private val dlGraph = DeadlockGraph()

    fun setRelevantFiles(relevantFiles: List<VirtualFile>): GraphBuilder {
        this.relevantFiles = relevantFiles
        relevantFiles.forEach { urlToVirtualFileMap[it.url] = it }
        return this
    }

    fun buildGraph(): DeadlockGraph {
        relevantFiles.forEach { file ->
            MyPsiUtils.findFunctionDefinitions(file, project).forEach { fn -> 
                exploreFunctionDeclaration(fn)
            }
        }
        return dlGraph
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
        return when {
            ElementFilters.isLaunchBuilder(call) || ElementFilters.isAsyncBuilder(call) -> 
                processAsyncCall(call, callerFun)
            
            ElementFilters.isChannelInit(call) -> 
                ChannelInitDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun)
            
            else -> processCall(call, callerFun)
        }
    }    
    
    private fun processAsyncCall(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        val ktFunction = MyPsiUtils.getAsyncBuilderLambda(call)!!
        if (relevantFiles.contains(ktFunction.containingFile.virtualFile)) {
            val calledFunNode = exploreFunctionDeclaration(ktFunction)
            return AsyncCallDLAction(
                call.containingFile.virtualFile.path,
                call.textOffset,
                callerFun,
                calledFunNode
            )
        }
        return null
    }
    
    private fun processCall(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        val ktFunction = MyPsiUtils.getFunForCall(call)!!
        val callAction = if (relevantFiles.contains(ktFunction.containingFile.virtualFile)) {
            val calledFunNode = exploreFunctionDeclaration(ktFunction)
            CallDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, calledFunNode)
        } else {
            OutOfScopeCallDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun)
        }
            // For funCall args and lambda args
            call.valueArguments.forEach { arg ->
                if (arg is KtLambdaArgument) {
                    val receivingFun = exploreFunctionDeclaration(arg.getLambdaExpression()!!.functionLiteral)
                    val lamCall =
                        CallDLAction(arg.containingFile.virtualFile.path, arg.textOffset, callerFun, receivingFun)
                    callAction.args.add(lamCall)

                } else {
                    MyPsiUtils.findAllChildren(
                        arg,
                        { ElementFilters.isCall(it) },
                        { ElementFilters.isFunction(it) },
                        pruneOnCondition = true,
                        includeStart = true
                    ).forEach { nestedCall ->
                        val action = exploreFunctionCall(nestedCall as KtCallExpression, callerFun)
                        action?.let { callAction.args.add(it) }
                    }
                }
            }
            return callAction
    }
    
}