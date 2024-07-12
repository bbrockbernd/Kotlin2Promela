package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.*
import com.example.kotlin2promela.graph.variablePassing.DLActionArgument
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class GraphInitializer(val project: Project, val dlGraph: DeadlockGraph, val relevantFiles: List<VirtualFile>) {
    fun intialize() {
        println("----INITIALIZE----")
        val totalFiles = relevantFiles.size
        
        relevantFiles.forEachIndexed { ind, file ->
            println("Handling file $ind/$totalFiles")
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
            { ElementFilters.isCall(it) || ElementFilters.isReturn(it) || ElementFilters.isProperty(it)},
            { ElementFilters.isFunction(it) },
            pruneOnCondition = true,
            includeStart = true
        ).forEach { expr ->
            val action = processExpression(expr as KtExpression, funNode)
            action?.let { funNode.actionList.add(it) }
        }

        // Order actions
        funNode.actionList.sortBy { it.offset }
        return funNode
    }
    
    private fun exploreExpression(expr: PsiElement?, containingFun: FunctionNode): DLAction? {
        val call = MyPsiUtils.findAllChildren(
            expr,
            { ElementFilters.isCall(it) || ElementFilters.isProperty(it) },
            { ElementFilters.isFunction(it) },
            pruneOnCondition = true,
            includeStart = true
        ).firstOrNull()
        if (call == null) return null
        return processExpression(call as KtCallExpression, containingFun)
    }
    
    private fun processExpression(expr: KtExpression, containingFun: FunctionNode): DLAction? {
        // TODO this will probably break when calling a lambda
        return when {
            ElementFilters.isLaunchBuilder(expr) || ElementFilters.isAsyncBuilder(expr) -> processAsyncCall(expr as KtCallExpression, containingFun)
            ElementFilters.isChannelInit(expr) -> processChannelInit(expr as KtCallExpression, containingFun)
            ElementFilters.isReturn(expr) -> processReturn(expr as KtReturnExpression, containingFun)
            ElementFilters.isProperty(expr) -> processPropertyAssignment(expr as KtProperty, containingFun)
            else -> processCall(expr as KtCallExpression, containingFun)
        }
    }
    
    private fun processPropertyAssignment(prop: KtProperty, containingFun: FunctionNode): DLAction? {
        // TODO Delegate expression
        val propValue = prop.initializer
        val propAction = AssignPropertyDLAction(prop.containingFile.virtualFile.path, prop.textOffset, containingFun, prop.createSmartPointer(), null, null)
        exploreExpression(propValue, containingFun)?.let{ propAction.assigning = DLActionArgument(it) }
        return propAction
    }
    
    private fun processReturn(ret: KtReturnExpression, containingFun: FunctionNode): DLAction? {
        val retValue = ret.returnedExpression ?: return null
        val retAction = DLReturnAction(ret.containingFile.virtualFile.path, ret.textOffset, containingFun, ret.createSmartPointer(), null)
        exploreExpression(retValue, containingFun)?.let{ retAction.returning = DLActionArgument(it) }
        return retAction
    }

    private fun processChannelInit(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        val capacity = MyPsiUtils.getCapacityForChannelInit(call)
        val channelInit = ChannelInitDLAction(MyPsiUtils.getId(call)!!, call.containingFile.virtualFile.path, call.textOffset, callerFun, call.createSmartPointer(), capacity)
        dlGraph.channelInits.add(channelInit)
        return channelInit
    }

    private fun processAsyncCall(call: KtCallExpression, callerFun: FunctionNode): DLAction? {
        val ktFunction = MyPsiUtils.getAsyncBuilderLambda(call) ?: return null
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
        val ktFunction = MyPsiUtils.getFunForCall(call) 

        // Create in or out of scope callAction
        val callAction = if (ktFunction != null && relevantFiles.contains(ktFunction.containingFile.virtualFile)) {
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
                exploreExpression(arg, callerFun)?.let{ callAction.args.add(DLActionArgument(it)) }
            }
        }
        return callAction
    }
}