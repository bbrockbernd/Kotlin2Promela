package com.example.kotlin2promela.graph.variablePassing.variableTypes

class DLChannelValType: DLValType {
    override fun promType(): String = "chan"
    override fun getAllPrimitivePaths(name: String): List<String> = listOf(name)
    override fun getAllPrimitivePaths(): List<String> = listOf()
        
}