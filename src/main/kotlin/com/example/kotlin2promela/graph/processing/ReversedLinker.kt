package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.action.CallDLAction
import com.example.kotlin2promela.graph.action.CallWithCalleeFunDLAction
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument
import com.example.kotlin2promela.graph.variablePassing.DLValConsumer
import com.example.kotlin2promela.graph.variablePassing.DLValProvider
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression

private const val RECEIVER = -1

class ReversedLinker(val dlGraph: DeadlockGraph) {
    
    private val visited = mutableListOf<DLValProvider<*>>()
    
    fun link() {
        dlGraph.channelOperations.forEach{ operation -> 
            backTrackCall(operation as CallDLAction, RECEIVER, DLChannelValType())
        }
    }
    
    // index -1 = receiver
    private fun backTrackCall(callAction: CallDLAction, argIndex: Int, dlType: DLValType) { 
        val arg = callAction.args[argIndex]
        
        
        if (arg is CallWithCalleeFunDLAction) {
            // goto return of fun that is called
            arg.returnType = dlType
            
        } else if (arg == null) {
            // insert consumer and resolve origin
            val psiCall = callAction.psiPointer.element!!
            val psiArg = psiCall.valueArguments[argIndex]!!.getArgumentExpression() as KtNameReferenceExpression
            val consumer = DLValConsumer()
            callAction.args[argIndex] = DLPassingArgument(consumer)
            backTrackUsage(psiArg, consumer)
        } else {
            throw IllegalStateException("Expected receiver or argument but was $arg, or was already initialized")
        }
    }
    
    private fun backTrackReturn(ret: KtReturnExpression) {
        
    }
    
    private fun backTrackUsage(ref: KtNameReferenceExpression, consumer: DLValConsumer) {
        val origin = ref.reference?.resolve()
        if (origin != null) throw IllegalStateException("Cannot resolve reference")
        
    }
}