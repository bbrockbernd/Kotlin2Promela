package com.example.kotlin2promela.graph.action

abstract class DLCallWithArguments : DLAction {
    abstract val args : MutableList<DLAction>
    fun hasArguments() = args.isNotEmpty()
}