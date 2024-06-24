package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.*
import com.example.kotlin2promela.graph.variablePassing.DLActionArgument
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class GraphInitializer(val project: Project, val dlGraph: DeadlockGraph, val relevantFiles: List<VirtualFile>) {
    fun intialize() {
        relevantFiles.forEach { file ->
            MyPsiUtils.findFunctionDefinitions(file, project).forEach { fn ->
                exploreFunctionDeclaration(fn)
            }
        }
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