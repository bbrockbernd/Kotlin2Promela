package com.example.kotlin2promela.graph.variablePassing.variableTypes

import com.example.kotlin2promela.graph.variablePassing.DLValConsumer

// id = fqName
class DLStruct(val id: String): DLValType {
    val propertyConsumers = mutableMapOf<String, DLValConsumer>()
}