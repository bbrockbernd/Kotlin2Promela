package com.example.kotlin2promela.graph.variablePassing.variableTypes

class DLUnitValType: DLValType {
    override fun promType(): String = "int"
    override fun getAllPrimitivePaths(name: String): List<String> = listOf(name)
    override fun getAllPrimitivePaths(): List<String> = listOf()
}