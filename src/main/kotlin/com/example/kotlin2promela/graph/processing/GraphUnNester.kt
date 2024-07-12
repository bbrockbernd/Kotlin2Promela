package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.graph.DeadlockGraph

class GraphUnNester(val dlGraph: DeadlockGraph) {
    fun unNest() {
        println("---UN-NESTING-ACTIONS----")
        val totalFuns = dlGraph.getFunctions().size
        dlGraph.getFunctions().forEachIndexed { ind, fn ->
            println("fn $ind/$totalFuns")
            val newActionList = fn.actionList
                .flatMap { it.unNest() + it }
            fn.actionList = newActionList.toMutableList()
        }
    }
}