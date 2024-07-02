package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelVal
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLVal

abstract class  DLValProducer<T: DLVal> {
    val producesFor: MutableList<DLValConsumer<T>> = mutableListOf()
    abstract fun promRefName(): String
}