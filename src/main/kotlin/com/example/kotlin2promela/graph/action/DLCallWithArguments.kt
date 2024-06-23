package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.variablePassing.DLArgument
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument

interface DLCallWithArguments : DLAction {
    val args : MutableList<DLArgument>
    val implArgs: MutableMap<Int, DLPassingArgument>
    fun hasExplArgs() = args.isNotEmpty()
    fun hasImplArgs() = implArgs.isNotEmpty()
    fun hasArgs() = args.isNotEmpty() || implArgs.isNotEmpty()
    
}