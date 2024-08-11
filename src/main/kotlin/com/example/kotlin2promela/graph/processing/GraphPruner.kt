package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.action.AssignPropertyDLAction
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType

class GraphPruner(private val dlGraph: DeadlockGraph) {
    
    fun prune() {
        println("----PRUNING----")
        // Remove calls that do not pass concurrency primitives
        dlGraph.getFunctions().forEach { fn -> 
            if (fn.importantParameters.isEmpty() && fn.implicitParameters.isEmpty() && fn.returnType is DLUnitValType) {
                fn.calledBy.forEach { call -> 
                    if (!call.performedIn.actionList.remove(call)) {
                       val propAss = call.performedIn.actionList
                           .filterIsInstance<AssignPropertyDLAction>()
                           .first { it.hasChild(call) }
                        call.performedIn.actionList.remove(propAss)
                    }
                }
                fn.calledBy.clear()
            }
        }
        
        // Remove functions that are not called and do not call (after call removal above obv)
        dlGraph.getFunctions().forEach { fn ->
            // TODO support deadlock in same function
            if (fn.calledBy.isEmpty() && fn.getChildNodes().isEmpty()) {
                dlGraph.removeFunction(fn)
            }
        }
    }
}