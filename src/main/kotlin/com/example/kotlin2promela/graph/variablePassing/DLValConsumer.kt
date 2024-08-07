package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType

open class DLValConsumer(var consumesFrom: DLValProvider<out DLValType>? = null) {
    
    fun link(provider: DLValProvider<out DLValType>) {
        provider.producesFor.add(this)
        consumesFrom = provider
    }
    
    companion object {
        fun createAndLinkChannelConsumer(consumesFrom: DLValProvider<DLChannelValType>): DLValConsumer {
            val me = DLValConsumer(consumesFrom)
            consumesFrom.producesFor.add(me)
            return me
        } 
    }
}