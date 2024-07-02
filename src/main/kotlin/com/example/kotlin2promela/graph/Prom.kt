package com.example.kotlin2promela.graph

interface Prom {
    fun toProm(indent: Int = 0): String
    fun StringBuilder.appendLineIndented(indent: Int, string: String): StringBuilder {
        this.append(" ".repeat(indent * 4))
        this.appendLine(string)
        return this
    }
}