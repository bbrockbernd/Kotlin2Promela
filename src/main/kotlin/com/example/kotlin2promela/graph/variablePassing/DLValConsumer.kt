package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType

open class DLValConsumer<T: DLValType>(var consumesFrom: DLValProvider<T>? = null) {
    
    companion object {
        fun createEmptyChannelConsumer(): DLValConsumer<DLChannelValType> = DLValConsumer()
        
        fun createAndLinkChannelConsumer(consumesFrom: DLValProvider<DLChannelValType>): DLValConsumer<DLChannelValType> {
            val me = DLValConsumer(consumesFrom)
            consumesFrom.producesFor.add(me)
            return me
        } 
    }
}