package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType

interface DLCallWithArguments : DLAction {
    val args : MutableList<DLArgument>
    val implArgs: MutableMap<Int, DLPassingArgument>
    fun hasExplArgs() = args.isNotEmpty()
    fun hasImplArgs() = implArgs.isNotEmpty()
    fun hasArgs() = args.isNotEmpty() || implArgs.isNotEmpty()
    override fun getChildActions(): List<DLAction> {
        return args.filterIsInstance<DLActionArgument>().map { it.action }
    }

    override fun unNest(): List<DLAction> {
        val actionAccumulator: MutableList<DLAction> = mutableListOf()
        val newArgs: MutableList<DLPassingArgument> = mutableListOf()
        args.forEach { arg ->
            if (arg is DLPassingArgument) { 
                newArgs.add(arg)
            } else {
                val actionArg = arg as DLActionArgument
                val actionPerformedInArgument = actionArg.action
                actionPerformedInArgument.unNest().forEach { actionAccumulator.add(it) }
                
                // variable passing for non unit call action argument
                if (actionPerformedInArgument is CallDLAction && actionPerformedInArgument.returnType !is DLUnitValType) {
                    val newProp = DLChannelProperty(actionPerformedInArgument.offset, actionPerformedInArgument.file, null)
                    val passingArgument = DLPassingArgument(actionPerformedInArgument.offset, DLValConsumer.createAndLinkChannelConsumer(newProp))
                    newArgs.add(passingArgument)
                    val propAssignAction = AssignPropertyDLAction(actionPerformedInArgument.file, actionPerformedInArgument.offset,
                        actionPerformedInArgument.performedIn, null, actionArg, newProp)
                    actionAccumulator.add(propAssignAction)
                } else {
                    actionAccumulator.add(actionPerformedInArgument)
                }
            }
        }
        args.clear()
        args.addAll(newArgs)
        return actionAccumulator
    }
}