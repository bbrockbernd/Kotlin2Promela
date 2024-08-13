package com.example.kotlin2promela.graph.variablePassing.variableTypes

interface DLValType {
    fun promType(): String
    fun getAllPrimitivePaths(name: String): List<String>
    fun getAllPrimitivePaths(): List<String>
}