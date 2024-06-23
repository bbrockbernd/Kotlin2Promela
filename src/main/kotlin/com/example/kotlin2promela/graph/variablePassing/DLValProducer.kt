package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelVal
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLVal

open class  DLValProducer<T: DLVal> {
    val producesFor: MutableList<DLValConsumer<T>> = mutableListOf()
    
    companion object {
        fun getChannelProducer(): DLValProducer<DLChannelVal> = DLValProducer()
    }
}