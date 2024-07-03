package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.graph.DeadlockGraph

class GraphPruner(private val dlGraph: DeadlockGraph) {
    
    fun prune() {
        println("----PRUNING----")
        // Remove calls that do not pass concurrency primitives
        dlGraph.getFunctions().forEach { fn -> 
            if (fn.parameterList.isEmpty() && fn.implicitParameters.isEmpty()) {
                fn.calledBy.forEach { call -> 
                    call.performedIn.actionList.remove(call)
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