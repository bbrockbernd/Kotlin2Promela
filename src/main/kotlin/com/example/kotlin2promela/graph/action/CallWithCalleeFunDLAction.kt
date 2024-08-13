package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType

abstract class CallWithCalleeFunDLAction(val callee: FunctionNode): DLCallWithArguments {
    init {
        callee.calledBy.add(this)
    }
    override val implArgs: MutableMap<Int, DLPassingArgument> = mutableMapOf()

    var returnType: DLValType = DLUnitValType()
    
    fun promArgs() = buildString {
        val myPromArgs = implArgs.toSortedMap().values + args.toSortedMap().values
        myPromArgs.filterIsInstance<DLPassingArgument>().forEach { arg ->
            append(arg.consumer.consumesFrom?.promRefName, ", ")
        }
        append("child_${offset}")
    }
}