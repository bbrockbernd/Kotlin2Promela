package com.example.kotlin2promela.graph.variablePassing.variableTypes

// id = fqName
class Struct(val id: String): DLValType {
    val items = mutableMapOf<String, DLValType>()
}