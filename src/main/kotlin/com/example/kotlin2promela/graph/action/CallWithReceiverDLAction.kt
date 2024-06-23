package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument

abstract class CallWithReceiverDLAction: DLCallWithArguments {
    abstract val receiving: FunctionNode
    override val implArgs: MutableMap<Int, DLPassingArgument> = mutableMapOf()
}