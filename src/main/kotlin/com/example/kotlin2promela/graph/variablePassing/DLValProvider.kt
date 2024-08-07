package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType

abstract class  DLValProvider<T: DLValType> {
    val producesFor: MutableList<DLValConsumer> = mutableListOf()
    abstract val promRefName: String
}