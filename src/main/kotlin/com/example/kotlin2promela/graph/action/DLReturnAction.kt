package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtReturnExpression

class DLReturnAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtReturnExpression>,
    var returning: DLArgument?
) : DLAction {
    override fun getChildActions(): List<DLAction> {
        if (returning is DLActionArgument) return listOf((returning as DLActionArgument).action)
        else return emptyList()
    }

    override fun unNest(): List<DLAction> {
        // Nothing to unnest
        if (returning is DLPassingArgument || returning == null) return listOf()
        
        // Un-nesting returned expression
        val actionToReturn = (returning as DLActionArgument).action
        val actionList = mutableListOf<DLAction>()
        actionToReturn.unNest().forEach { actionList.add(it) }
        
        // If out of scope return nothing.
        if (actionToReturn is OutOfScopeCallDLAction) {
            returning = null
            return actionList
        }
        
        // Create prop to receive value in and return
        val newProp = DLProperty(actionToReturn.offset, actionToReturn.file, null, false, DLChannelValType())
        val passingArgument = DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(newProp))
        val propAssignAction = AssignPropertyDLAction(actionToReturn.file, actionToReturn.offset, actionToReturn.performedIn, null, returning, newProp)
        returning = passingArgument
        actionList.add(propAssignAction)
        return actionList
    }

    override fun toProm(indent: Int): String = buildString {
        if (returning is DLPassingArgument) appendLineIndented(indent, "ret!${(returning as DLPassingArgument).consumer.consumesFrom!!.promRefName}")
        else if (returning == null)  appendLineIndented(indent, "ret!0")
        appendLineIndented(indent, "goto end")
    }
}