package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument

abstract class CallWithReceiverDLAction(val receiving: FunctionNode): DLCallWithArguments {
    init {
        receiving.calledBy.add(this)
    }
    override val implArgs: MutableMap<Int, DLPassingArgument> = mutableMapOf()
}