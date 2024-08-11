package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType

abstract class  DLValProvider {
    val producesFor: MutableList<DLValConsumer> = mutableListOf()
    abstract val promRefName: String
    abstract val type: DLValType
}