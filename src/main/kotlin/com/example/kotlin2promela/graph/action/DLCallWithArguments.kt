package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType

interface DLCallWithArguments : DLAction {
    val args : MutableMap<Int, DLArgument>
    val implArgs: MutableMap<Int, DLPassingArgument>
    val returnType: DLValType
    fun hasExplArgs() = args.isNotEmpty()
    fun hasImplArgs() = implArgs.isNotEmpty()
    fun hasArgs() = args.isNotEmpty() || implArgs.isNotEmpty()
    override fun getChildActions(): List<DLAction> {
        return args.values.filterIsInstance<DLActionArgument>().map { it.action }
    }

    override fun unNest(): List<DLAction> {
        val actionAccumulator: MutableList<DLAction> = mutableListOf()
        args.keys.toList().forEach { indexKey ->
            val arg = args[indexKey]
            if (arg !is DLPassingArgument) { 
                val actionArg = arg as DLActionArgument
                val actionPerformedInArgument = actionArg.action
                actionPerformedInArgument.unNest().forEach { actionAccumulator.add(it) }
                
                // if action in argument is a call and does not return unit
                // or action is property access and receiver is non null and the property type is non unit
                if (actionPerformedInArgument is CallDLAction && actionPerformedInArgument.returnType !is DLUnitValType 
                    || actionPerformedInArgument is DLPropertyAccessAction && actionPerformedInArgument.obj != null && actionPerformedInArgument.type !is DLUnitValType) {
                    val propType = if (this is CallWithCalleeFunDLAction) {
                        if (callee.importantParameters[indexKey] == null) throw IllegalStateException("HUH WHERE MY PARAMETERS AT?")
                        callee.importantParameters[indexKey]!!.type
                    } else DLChannelValType()
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
                } else if (actionPerformedInArgument is DLCallWithArguments) {
                    args.remove(indexKey)
                    actionAccumulator.add(actionPerformedInArgument)
                } else {
                    args.remove(indexKey)
                }
            }
        }
        return actionAccumulator
    }
}