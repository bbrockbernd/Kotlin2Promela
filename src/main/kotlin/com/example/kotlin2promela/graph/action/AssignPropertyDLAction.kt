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
    var assigning: DLArgument?, // right
    var assignee: DLProperty?,  // left
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
        // Channel init (constructor) is assigned to channel property -> val a = Channel<>()
        if (assignee is DLProperty && assigning is DLActionArgument && (assigning as DLActionArgument).action is ChannelInitDLAction) {
            val chanInitAction = (assigning as DLActionArgument).action as ChannelInitDLAction
            val prop = assignee as DLProperty
            appendLineIndented(indent, "chan ${prop.promRefName}")
            appendLineIndented(indent, "new_${chanInitAction.globalRefName}(${prop.promRefName})")
        }
            
        // return from call is assigned to property -> val a = getChannel()
        else if (assignee is DLProperty && assigning is DLActionArgument && (assigning as DLActionArgument).action is CallDLAction) {
            val callAction = (assigning as DLActionArgument).action as CallDLAction
            val prop = assignee as DLProperty
            appendLineIndented(indent, "${prop.type.promType()} ${prop.promRefName}")
            append(callAction.toProm(indent, prop.promRefName))
        }

        // property access is assigned to new property -> val a = bla.chan
        else if (assignee is DLProperty && assigning is DLActionArgument && (assigning as DLActionArgument).action is DLPropertyAccessAction) {
            val propAccessAction = (assigning as DLActionArgument).action as DLPropertyAccessAction
            val prop = assignee as DLProperty
            appendLineIndented(indent, "${prop.type.promType()} ${prop.promRefName}")
            
            prop.type.getAllPrimitivePaths()
                .map{if (it.isNotEmpty()) ".$it" else it}
                .forEach {
                    appendLineIndented(indent, "${prop.promRefName}$it = ${propAccessAction.toProm(indent)}$it")
            }
        }
            
        //Property is assigned to property -> val a: Channel = b
        else if (assignee is DLProperty && assigning is DLPassingArgument) {
            val provider = (assigning as DLPassingArgument).consumer.consumesFrom
            val prop = assignee as DLProperty
            appendLineIndented(indent, "chan ${prop.promRefName} = ${provider!!.promRefName}")
        }
    }
}