package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType

interface DLCallWithArguments : DLAction {
    val args : MutableMap<Int, DLArgument>
    val implArgs: MutableMap<Int, DLPassingArgument>
    fun hasExplArgs() = args.isNotEmpty()
    fun hasImplArgs() = implArgs.isNotEmpty()
    fun hasArgs() = args.isNotEmpty() || implArgs.isNotEmpty()
    override fun getChildActions(): List<DLAction> {
        return args.values.filterIsInstance<DLActionArgument>().map { it.action }
    }

    override fun unNest(): List<DLAction> {
        val actionAccumulator: MutableList<DLAction> = mutableListOf()
        args.forEach { (indexKey, arg) ->
            if (arg !is DLPassingArgument) { 
                val actionArg = arg as DLActionArgument
                val actionPerformedInArgument = actionArg.action
                actionPerformedInArgument.unNest().forEach { actionAccumulator.add(it) }
                
                // variable passing for non unit call action argument
                if (actionPerformedInArgument is CallDLAction && actionPerformedInArgument.returnType !is DLUnitValType 
                    || actionPerformedInArgument is DLPropertyAccessAction && actionPerformedInArgument.obj != null) {
                    val propType = if (this is CallWithCalleeFunDLAction) callee.importantParameters[indexKey]!!.type else DLChannelValType()
                    val newProp = DLProperty(
                        actionPerformedInArgument.offset,
                        actionPerformedInArgument.file,
                        null,
                        false,
                        propType
                    )
                    val passingArgument = DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(newProp))
                    args[indexKey] = passingArgument
                    val propAssignAction = AssignPropertyDLAction(
                        actionPerformedInArgument.file, actionPerformedInArgument.offset,
                        actionPerformedInArgument.performedIn, null, actionArg, newProp
                    )
                    actionAccumulator.add(propAssignAction)
                } else {
                    args.remove(indexKey)
                    actionAccumulator.add(actionPerformedInArgument)
                }
            }
        }
        return actionAccumulator
    }
}