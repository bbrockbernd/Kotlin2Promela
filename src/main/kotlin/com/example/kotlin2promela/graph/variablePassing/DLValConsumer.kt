package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelVal
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLVal

open class DLValConsumer<T: DLVal>(var consumesFrom: DLValProducer<T>? = null) {
    
    companion object {
        fun createEmptyChannelConsumer(): DLValConsumer<DLChannelVal> = DLValConsumer()
        
        fun createAndLinkChannelConsumer(consumesFrom: DLValProducer<DLChannelVal>): DLValConsumer<DLChannelVal> {
            val me = DLValConsumer(consumesFrom)
            consumesFrom.producesFor.add(me)
            return me
        } 
    }
}