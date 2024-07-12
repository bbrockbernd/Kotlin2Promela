package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.*
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtProperty

class AssignPropertyDLAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtProperty>?,
    var assigning: DLArgument?, 
    var assignee: DLParameter?
) : DLAction {
    
    override fun getChildActions(): List<DLAction> {
        if (assigning is DLActionArgument) return listOf((assigning as DLActionArgument).action)
        else return emptyList()
    }

    override fun unNest(): List<DLAction> {
        if (assigning is DLPassingArgument) return listOf()
        else {
            val actionArg = assigning as DLActionArgument
            return actionArg.action.unNest()
        }
    }

    override fun toProm(indent: Int): String = buildString {
        if (assignee is DLChannelProperty && assigning is DLActionArgument) {
            val callAction = (assigning as DLActionArgument).action as CallDLAction
            val prop = assignee as DLChannelProperty
            appendLineIndented(indent, "chan ${prop.promRefName}")
            append(callAction.toProm(indent, prop.promRefName))
        } else if (assignee is DLChannelProperty && assigning is DLPassingArgument) {
            val provider = (assigning as DLPassingArgument).consumer.consumesFrom
            val prop = assignee as DLChannelProperty
            appendLineIndented(indent, "chan ${prop.promRefName} = ${provider!!.promRefName}")
        }
    }
}