package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.*
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

class DLReturnAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<out PsiElement>,
    var returning: DLArgument?
) : DLAction {
    override fun getChildActions(): List<DLAction> {
        if (returning is DLActionArgument) return listOf((returning as DLActionArgument).action)
        else return emptyList()
    }

    override fun unNest(): List<DLAction> {
        if (returning is DLPassingArgument || returning == null) return listOf()
        else {
            val actionToReturn = (returning as DLActionArgument).action
            val actionList = mutableListOf<DLAction>()
            actionToReturn.unNest().forEach { actionList.add(it) }
            
            val newProp = DLChannelProperty(actionToReturn.offset, actionToReturn.file, null)
            val passingArgument = DLPassingArgument(actionToReturn.offset, DLValConsumer.createAndLinkChannelConsumer(newProp))
            val propAssignAction = AssignPropertyDLAction(actionToReturn.file, actionToReturn.offset, actionToReturn.performedIn, null, returning, newProp)
            returning = passingArgument
            actionList.add(propAssignAction)
            return actionList
        }
    }

    override fun toProm(indent: Int): String = buildString {
        if (returning is DLPassingArgument) appendLineIndented(indent, "ret!${(returning as DLPassingArgument).consumer.consumesFrom!!.promRefName}")
        else if (returning == null)  appendLineIndented(indent, "ret!0")
        appendLineIndented(indent, "goto end")
    }
}