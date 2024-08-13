package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.*
import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLStruct
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class ReversedLinker(val dlGraph: DeadlockGraph) {
    
    private interface Job
    private data class CallArgJob(val callAction: CallDLAction, val argIndex: Int, val dlType: DLValType): Job
    private data class CallRecvArgJob(val callAction: DLCallWithArguments, val dlType: DLValType): Job
    private data class RetArgJob(val retAction: DLReturnAction, val dlType: DLValType): Job
    private data class PropArgJob(val propAssignAction: AssignPropertyDLAction, val dlType: DLValType): Job
    private data class PropAccessArgJob(val propAccessAction: DLPropertyAccessAction, val dlType: DLValType): Job
    private val argumentPositionsTodo = mutableListOf<Job>()

    private fun visitedParam(fn: FunctionNode, paramIndex: Int): Boolean = !visitedSet.add(fn.info.id + paramIndex)
    private fun visitedCall(fn: FunctionNode): Boolean = !visitedSet.add(fn.info.id + "ret")
    private fun visitedProp(propAssignAction: AssignPropertyDLAction): Boolean = !visitedSet.add(propAssignAction.performedIn.info.id + propAssignAction.offset + "prop")
    private val visitedSet = mutableSetOf<String>()
    
    private val structStore = mutableMapOf<String, DLStruct>()
    
    fun link() {
        dlGraph.channelOperations.forEach{ operation -> 
            argumentPositionsTodo.add(CallRecvArgJob(operation as DLCallWithArguments, DLChannelValType()))
        }
        
        while (argumentPositionsTodo.isNotEmpty()) {
            when (val currentJob = argumentPositionsTodo.removeLast()) {
                is CallArgJob -> processCallArgument(currentJob)
                is RetArgJob -> processRetArgument(currentJob)
                is PropArgJob -> processPropArgument(currentJob)
                is PropAccessArgJob -> processPropAccessArgument(currentJob)
                is CallRecvArgJob -> processCallRecvArgument(currentJob)
            }
        }

        dlGraph.getFunctions()
            .filter { it.isConstructor && structStore.contains(it.info.fqName) }
            .forEach { it.returnType = structStore[it.info.fqName]!! }
    }
    
    private fun processCallArgument(argJob: CallArgJob) { 
        val (callAction, argIndex, dlType) = argJob
        val arg = callAction.args[argIndex]
        processArgument(arg, callAction, dlType, {callAction.args[argIndex] = it}) {
            val psiCall = callAction.psiPointer.element!!
            return@processArgument psiCall.valueArguments[argIndex]!!.getArgumentExpression() as KtNameReferenceExpression
        }
    }
    
    private fun processCallRecvArgument(argJob: CallRecvArgJob) {
        val (callAction, dlType) = argJob
        val psiCall = callAction.psiPointer?.element!!

        // If real case (with dotQ and all) process like normal
        if (psiCall.parent is KtDotQualifiedExpression && (psiCall.parent as KtDotQualifiedExpression).selectorExpression == psiCall) { 
            val arg = callAction.args[-1]
            processArgument(arg, callAction, dlType, { callAction.args[-1] = it}) {
                (psiCall.parent as KtDotQualifiedExpression).receiverExpression as KtNameReferenceExpression
            }
        }
        // else link this to self call
        else {
            if (callAction.args[-1] != null) throw IllegalStateException("Receiver was already added to call!")
            val consumer = DLValConsumer()
            callAction.args[-1] = DLPassingArgument(consumer)
            linkThis(callAction, consumer, dlType)
        }
    }

    private fun processRetArgument(retJob: RetArgJob) {
        val (retAction, dlType) = retJob
        val arg = retAction.returning
        processArgument(arg, retAction, dlType, {retAction.returning = it}) {
            val psiReturn = retAction.psiPointer.element!!
            return@processArgument psiReturn.returnedExpression as KtNameReferenceExpression
        }
    }
    
    private fun processPropArgument(propJob: PropArgJob) {
        val (propAction, dlType) = propJob
        val arg = propAction.assigning
        processArgument(arg, propAction, dlType, { propAction.assigning = it}) {
            val psiProperty = propAction.psiPointer?.element!!
            return@processArgument psiProperty.initializer as KtNameReferenceExpression
        }
    }
    
    private fun processPropAccessArgument(propAccessJob: PropAccessArgJob) {
        val (propAccessAction, dlType) = propAccessJob
        val arg = propAccessAction.obj
        processArgument(arg, propAccessAction, dlType, { propAccessAction.obj = it }) {
            val psiDotQ = propAccessAction.psiPointer?.element!!
            val psiReceiver = psiDotQ.receiverExpression
            return@processArgument psiReceiver as KtNameReferenceExpression
        }
    }
    
    private fun processArgument(arg: DLArgument?, action: DLAction, dlType: DLValType, setArgument: (DLArgument) -> Unit, getPsiRefForArg: () -> KtNameReferenceExpression) {
        // if arg is null than it should be a passing argument
        if (arg == null) {
            // insert consumer and resolve origin
            val psiArg = getPsiRefForArg()
            val origin = psiArg.reference?.resolve() as KtNamedDeclaration
            if (origin is KtProperty && origin.isMember || origin is KtParameter && origin.hasValOrVar()) {
                
                // Setup self consumer and prop access
                val selfConsumer = DLValConsumer()
                val self = DLPassingArgument(selfConsumer)
                val selfAccess = DLPropertyAccessAction(psiArg.containingFile.virtualFile.path, psiArg.textOffset, action.performedIn, null, psiArg.getReferencedName(), self)
                setArgument(DLActionArgument(selfAccess))
                
                // Struct consumes from prop in constructor (aka class prop)
                val fqName = origin.containingClass()?.fqName.toString()
                val struct = structStore.computeIfAbsent(fqName) { DLStruct(origin.containingClass()!!) }
                val propName = origin.name!!
                if (!struct.propertyConsumers.contains(propName)) {
                    val structConsumer = DLValConsumer()
                    struct.propertyConsumers[propName] = structConsumer
                    val fn = dlGraph.getOrCreateFunction(origin.containingClass()!!)
                    linkOriginToConsumer(origin, structConsumer, fn , dlType)
                }
                
                // self consumes from receiver parameter (-1) 
                linkThis(action, selfConsumer, struct)
                
            } else {
                val consumer = DLValConsumer()
                setArgument(DLPassingArgument(consumer))
                linkOriginToConsumer(origin, consumer, action.performedIn, dlType)
            }
        } 
            
        // if arg is a call
        else if (arg is DLActionArgument && arg.action is CallWithCalleeFunDLAction) {
            arg.action.returnType = dlType
            arg.action.callee.returnType = dlType
            if (!visitedCall(arg.action.callee) && !arg.action.callee.isConstructor) {
                arg.action.callee.getReturns().forEach { retAction ->
                    argumentPositionsTodo.add(RetArgJob(retAction, dlType))
                }
            }
        }

        // if arg is a property access like someFun(bla.prop) or someFun(otherFun().prop)
        else if (arg is DLActionArgument && arg.action is DLPropertyAccessAction) {
            // Get receiver dlType
            val dotQ = arg.action.psiPointer?.element!!
            val clazzPropRef =
                (dotQ.selectorExpression as KtNameReferenceExpression).reference!!.resolve() as KtNamedDeclaration
            val fqName = clazzPropRef.containingClass()?.fqName.toString()
            val struct = structStore.computeIfAbsent(fqName) { DLStruct(clazzPropRef.containingClass()!!) }
            
            // Set type for property access
            arg.action.type = dlType

            // if selector is new for receiver -> add and create argJob for corresponding arg in constructor
            val propName = arg.action.propertyName
            if (!struct.propertyConsumers.contains(propName)) {
                val structConsumer = DLValConsumer()
                struct.propertyConsumers[propName] = structConsumer
                val fn = dlGraph.getOrCreateFunction(clazzPropRef.containingClass()!!)
                linkOriginToConsumer(clazzPropRef, structConsumer, fn, dlType)
            }

            argumentPositionsTodo.add(PropAccessArgJob(arg.action, struct))

        } else if (arg is DLActionArgument && arg.action is ChannelInitDLAction) return
            
        else {
            throw IllegalStateException("Expected receiver or argument but was $arg, or was already initialized")
        }
    }
    
    
    private fun linkThis(action: DLAction, consumer: DLValConsumer, dlType: DLValType) {
        val usageFun = action.performedIn
        val psiOriginFun = MyPsiUtils.findParent(action.psiPointer?.element!!, { it is KtNamedFunction },  { it is KtFile }) as KtNamedFunction
        val originFun = dlGraph.getOrCreateFunction(psiOriginFun)
        if (!originFun.importantParameters.contains(-1)) {
            originFun.importantParameters[-1] = DLParameter(psiOriginFun.textOffset, psiOriginFun.containingFile.virtualFile.path, null, false, dlType)
            originFun.calledBy.forEach {
                argumentPositionsTodo.add(CallRecvArgJob(it as CallDLAction, dlType))
            }
        }
        val recvArg = originFun.importantParameters[-1]!!
        linkAndFixLambda(originFun, psiOriginFun, recvArg, usageFun, consumer)
    }
    
    private fun linkOriginToConsumer(origin: KtNamedDeclaration, consumer: DLValConsumer, usageFun: FunctionNode, dlType: DLValType) {
        val originPsiParent = MyPsiUtils.findParent(origin, { it is KtFunction || it is KtClass }, { it is KtFile })!!
        
        val originFun = when {
            originPsiParent is KtFunction -> dlGraph.getOrCreateFunction(originPsiParent)
            originPsiParent is KtClass -> dlGraph.getOrCreateFunction(originPsiParent)
            else -> throw IllegalStateException("origin parent not found (this error should be dead code)")
        }

        if (origin is KtParameter) {
            val paramIndex = MyPsiUtils.getParameterIndex(origin)
            val provider = originFun.importantParameters.computeIfAbsent(paramIndex) {
                DLParameter(origin.textOffset, origin.containingFile.virtualFile.path, origin.createSmartPointer(), false, dlType)
            }
            linkAndFixLambda(originFun, origin, provider, usageFun, consumer)
            
            if (!visitedParam(originFun, paramIndex)) {
                originFun.calledBy.forEach { 
                    argumentPositionsTodo.add(CallArgJob(it as CallDLAction, paramIndex, dlType))
                }
            }

        } else if (origin is KtProperty) {
            val propAssign = originFun.getPropertyAssignFor(origin)
            if (propAssign.assignee == null) propAssign.assignee =
                DLProperty(origin.textOffset, origin.containingFile.virtualFile.path, origin.createSmartPointer(), false, dlType)
            val provider = propAssign.assignee!!
            linkAndFixLambda(originFun, origin, provider, usageFun, consumer)
            
            if (!visitedProp(propAssign)) {
                argumentPositionsTodo.add(PropArgJob(propAssign, dlType))
            }

        } else throw IllegalStateException("Should property or parameter but is ${origin?.javaClass?.name}")
    }
    
    
    private fun linkAndFixLambda(
        originFun: FunctionNode,
        originRef: KtNamedDeclaration,
        originProvider: DLValProvider,
        usageFun: FunctionNode,
        usageConsumer: DLValConsumer,
    ) {
        val providerToLink = if (originFun != usageFun) { // If not pass is implicit
            val offset = originRef.textOffset
            val path = dlGraph.BFSDown(originFun, usageFun)
            if (path.isEmpty()) throw IllegalStateException("Path must be at least 1 call")

            path.first().implArgs.computeIfAbsent(offset) {
                DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(originProvider))
            }

            // Hack use chaninit textOffset to differentiate
            path.first().callee.implicitParameters.computeIfAbsent(offset) {
                DLParameter(offset, originRef.containingFile.virtualFile.path, null, false, originProvider.type)
            }

            for (i in 1..path.lastIndex) {
                val param = path[i].performedIn.implicitParameters[offset] as DLParameter
                path[i].implArgs.computeIfAbsent(offset) {
                    DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(param)) 
                }
                path[i].callee.implicitParameters.computeIfAbsent(offset) {
                    DLParameter(offset, originRef.containingFile.virtualFile.path, null, false, originProvider.type)
                }
            }
            path.last().callee.implicitParameters[offset] as DLParameter
        } else originProvider
        
        usageConsumer.link(providerToLink)
    }
}