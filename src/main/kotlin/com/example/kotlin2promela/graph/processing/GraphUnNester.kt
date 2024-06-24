package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.action.DLAction
import com.example.kotlin2promela.graph.action.DLCallWithArguments
import com.example.kotlin2promela.graph.variablePassing.DLActionArgument

class GraphUnNester(val dlGraph: DeadlockGraph) {
    fun unNest() {
        dlGraph.getFunctions().forEach { fn ->
            val newActionList = fn.actionList
                .flatMap { action -> 
                    if (action is DLCallWithArguments) {
                        processCall(action) + action
                    } else {
                        listOf(action)
                    }
                }
            fn.actionList = newActionList.toMutableList()
        }
    }
    
    fun processCall(callWithArgs: DLCallWithArguments): List<DLAction> {
        // Create action list
        val actionList: List<DLAction> = callWithArgs.args.flatMap { arg ->
            if (arg is DLActionArgument) {
                val action = arg.action
                if (action is DLCallWithArguments) return@flatMap listOf(action) + processCall(action)
                return@flatMap listOf(action)
            }
            return@flatMap listOf()
        }
        
        // Remove all Action args
        val nonPassingArgs = callWithArgs.args.filter {it is DLActionArgument}
        nonPassingArgs.forEach { callWithArgs.args.remove(it) }
        return actionList
    }
}