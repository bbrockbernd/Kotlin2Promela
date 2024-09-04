package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.VerboseLogger
import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.*
import com.example.kotlin2promela.graph.variablePassing.DLActionArgument
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.isAbstract

class GraphInitializer(val project: Project, val dlGraph: DeadlockGraph, val relevantFiles: List<VirtualFile>) {
    fun intialize() {
        VerboseLogger.log("----INITIALIZE----")
        val totalFiles = relevantFiles.size
        
        relevantFiles.forEachIndexed { ind, file ->
            VerboseLogger.log("Handling file $ind/$totalFiles")
            
            MyPsiUtils.findClassDefinitions(file, project).forEach { clazz ->
                if (!clazz.isInterface() && !clazz.isAbstract()) exploreFunctionOrClassDeclaration(clazz)
            }
                     
            MyPsiUtils.findFunctionDefinitions(file, project)
                .filter { it !is KtConstructor<*> }
                .forEach { exploreFunctionOrClassDeclaration(it) }
        }
    }
    
    // Starting point for class exploration
    private fun exploreFunctionOrClassDeclaration(clazz: KtClass): FunctionNode {
        if (clazz.isInterface() || clazz.isAbstract()) throw IllegalArgumentException("Cannot create function node for abstract class or interface ${clazz.name}")
        val funNode = dlGraph.getOrCreateFunction(clazz)
        if (funNode.visited) return funNode
        funNode.visited = true
        
        // add class properties
        clazz.body?.properties?.forEach { prop ->
            val dlProp = processPropertyAssignment(prop, funNode)
            dlProp?.let { funNode.actionList.add(it) }
        }
        return funNode
    }

    // Starting point for function exploration
    private fun exploreFunctionOrClassDeclaration(fn: KtFunction): FunctionNode {
        if (fn is KtConstructor<*>) return exploreFunctionOrClassDeclaration(fn.containingClass()!!)
        // init
        val funNode = dlGraph.getOrCreateFunction(fn)
        if (funNode.visited) return funNode
        funNode.visited = true

        // Check all relevant elements in body
        MyPsiUtils.findAllChildren(
            fn.bodyExpression,
            { ElementFilters.isCall(it) || ElementFilters.isReturn(it) || ElementFilters.isProperty(it) || ElementFilters.isDotQualified(it) },
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
    
    // Explores any psi element
    private fun exploreExpression(expr: PsiElement?, containingFun: FunctionNode): DLAction? {
        val calls = MyPsiUtils.findAllChildren(
            expr,
            { ElementFilters.isCall(it) || ElementFilters.isDotQualified(it) },
            { ElementFilters.isFunction(it) },
            pruneOnCondition = true,
            includeStart = true
        )
        
        // Hacks for complex assign constructs, if only one call is made it is returned e.g. val bla = call()
        // if more calls are made its probably a val bla = if (joe) call() else otherCall()
        // we do not support this but treat it as if they are all called but do not assign
        if (calls.size == 1) {
            return processExpression(calls[0] as KtExpression, containingFun)
        }
        calls.forEach { call -> 
            val action = processExpression(call as KtExpression, containingFun)
            action?.let { containingFun.actionList.add(it) }
        }
        return null
    }
    
    private fun processExpression(expr: KtExpression, containingFun: FunctionNode): DLAction? {
        // TODO this will probably break when calling a lambda
        return when {
            ElementFilters.isLaunchBuilder(expr) || ElementFilters.isAsyncBuilder(expr) -> processAsyncCall(expr as KtCallExpression, containingFun)
            ElementFilters.isChannelInit(expr) -> processChannelInit(expr as KtCallExpression, containingFun)
            ElementFilters.isReturn(expr) -> processReturn(expr as KtReturnExpression, containingFun)
            ElementFilters.isProperty(expr) -> processPropertyAssignment(expr as KtProperty, containingFun)
            ElementFilters.isDotQualified(expr) -> processDotQualified(expr as KtDotQualifiedExpression, containingFun)
            else -> processCall(expr as KtCallExpression, containingFun)
        }
    }
    
    private fun processDotQualified(dotQ: KtDotQualifiedExpression, containingFun: FunctionNode): DLAction? {
        val receiverAction = exploreExpression(dotQ.receiverExpression, containingFun)
        
        val selector = dotQ.selectorExpression
        val action = if (selector is KtCallExpression && (ElementFilters.isLaunchBuilder(selector) || ElementFilters.isAsyncBuilder(selector))) {
            processAsyncCall(selector, containingFun)
        } else if (selector is KtCallExpression) {
            val callAction = processCall(selector, containingFun) as DLCallWithArguments
            receiverAction?.let { callAction.args[-1] = DLActionArgument(it) }
            callAction
        } else if (selector is KtNameReferenceExpression) {
            val receiverArgument = receiverAction?.let { DLActionArgument(it) }
            val propAccessAction = DLPropertyAccessAction(dotQ.containingFile.virtualFile.path, selector.textOffset, containingFun, dotQ.createSmartPointer(), selector.getReferencedName(), receiverArgument)
            propAccessAction
        } else {
            throw IllegalStateException("Expected class prop selection or functino call but is something else for file " +
                    "${dotQ.containingFile.virtualFile.path} and element text ${dotQ.selectorExpression?.text}")
        }
        return action
    }
    
    private fun processPropertyAssignment(prop: KtProperty, containingFun: FunctionNode): DLAction? {
        // TODO Delegate expression
        val propValue = prop.initializer
        val propAction = AssignPropertyDLAction(prop.containingFile.virtualFile.path, prop.textOffset, containingFun, prop.createSmartPointer(), null, null)
        val assigning = exploreExpression(propValue, containingFun) ?: return propAction
        propAction.assigning = DLActionArgument(assigning) 
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
            val calledFunNode = exploreFunctionOrClassDeclaration(ktFunction)
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
        val ktClass = MyPsiUtils.getClassForCall(call)
        // Create in or out of scope callAction
        val callAction = if (ktFunction != null && relevantFiles.contains(ktFunction.containingFile.virtualFile)) {
            val calledFunNode = exploreFunctionOrClassDeclaration(ktFunction)
            CallDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, calledFunNode, call.createSmartPointer())
        } else if (ElementFilters.isSendCall(call)) {
            val send = ChannelSendDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, call.createSmartPointer())
            dlGraph.channelOperations.add(send)
            send
        } else if (ElementFilters.isReceiveCall(call)) {
            val receive = ChannelRecvDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, call.createSmartPointer())
            dlGraph.channelOperations.add(receive)
            receive
        } else if (ktClass != null && relevantFiles.contains(ktClass.containingFile.virtualFile)) {
            val calledFunNode = exploreFunctionOrClassDeclaration(ktClass)
            CallDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, calledFunNode, call.createSmartPointer())
        } else {
            OutOfScopeCallDLAction(call.containingFile.virtualFile.path, call.textOffset, callerFun, call.createSmartPointer())
        }

        // Process arguments
        call.valueArguments.forEachIndexed { index, arg ->
            // TODO fix: Hack for lambda arguments (inline maybe??)
            if (arg is KtLambdaArgument) {
                val receivingFun = exploreFunctionOrClassDeclaration(arg.getLambdaExpression()!!.functionLiteral)
                val lamCall =
                    CallDLAction(arg.containingFile.virtualFile.path, arg.textOffset, callerFun, receivingFun, call.createSmartPointer())
                callAction.args[index] = DLActionArgument(lamCall)
            } else {
                exploreExpression(arg, callerFun)?.let{ callAction.args[index] = DLActionArgument(it) }
            }
        }
        return callAction
    }
}