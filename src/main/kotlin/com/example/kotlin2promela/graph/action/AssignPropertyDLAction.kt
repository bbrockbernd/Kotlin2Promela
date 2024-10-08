package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
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
        if (assigning is DLPassingArgument || assigning == null) return listOf()
        else {
            val actionAccumulator = mutableListOf<DLAction>()
            val action = (assigning as DLActionArgument).action
            
            // Nested call but assign is not interesting
            if (action is DLCallWithArguments) {
                val call = (assigning as DLActionArgument).action as DLCallWithArguments
                if (call.returnType is DLUnitValType) {
                    actionAccumulator.add(call)
                    assigning = null
                }
            }
            
            return action.unNest() + actionAccumulator
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
            if (propAccessAction.obj == null || propAccessAction.type is DLUnitValType) return ""
            val prop = assignee as DLProperty
            appendLineIndented(indent, "${prop.type.promType()} ${prop.promRefName}")
            
            prop.type.getAllPrimitivePaths(prop.promRefName)
                .zip(prop.type.getAllPrimitivePaths(propAccessAction.toProm(indent)))
                .forEach { (left, right) -> appendLineIndented(indent, "$left = $right") }
        }
            
        //Property is assigned to property -> val a: Channel = b
        else if (assignee is DLProperty && assigning is DLPassingArgument) {
            val provider = (assigning as DLPassingArgument).consumer.consumesFrom
            val prop = assignee as DLProperty
            appendLineIndented(indent, "chan ${prop.promRefName} = ${provider!!.promRefName}")
        }
    }
}