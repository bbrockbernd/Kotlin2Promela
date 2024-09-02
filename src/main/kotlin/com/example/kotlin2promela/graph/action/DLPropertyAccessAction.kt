package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.VerboseLogger
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

class DLPropertyAccessAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtDotQualifiedExpression>?,
    val propertyName: String,
    var obj: DLArgument?
) : DLAction {
    var type: DLValType = DLUnitValType()
    
//    fun toKeep(): Boolean {
//        if (type is DLUnitValType) return false
//        if (obj == null) return false
//        if (obj is DLActionArgument && (obj as DLActionArgument).action is DLPropertyAccessAction) 
//            return ((obj as DLActionArgument).action as DLPropertyAccessAction).toKeep()
//        return true
//    }
    
    override fun getChildActions(): List<DLAction> {
        if (obj is DLActionArgument) return listOf((obj as DLActionArgument).action)
        else return emptyList()
    }

    override fun unNest(): List<DLAction> {
        // Nothing to unnest
        if (obj is DLPassingArgument || obj == null) return emptyList()
        
        // Un-nesting receiver expression
        val action = (obj as DLActionArgument).action
        val actionList = action.unNest().toMutableList()

        // If out of scope access nothing
        if (action is OutOfScopeCallDLAction) {
            obj = null
            return actionList
        }
        
        val newPropType = when {
            action is CallDLAction -> action.returnType
            action is DLPropertyAccessAction -> action.type
            else -> {
                VerboseLogger.log("During unnest of property access: action was not a call nor property access")
                type
            }
        }
        
        // Create prop to accept value in and pass as receiver argument
        val newProp = DLProperty(action.offset, action.file, null, false, newPropType)
        val passingArgument = DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(newProp))
        val propAssignAction = AssignPropertyDLAction(action.file, action.offset, action.performedIn, null, obj, newProp)
        obj = passingArgument
        actionList.add(propAssignAction)
        return actionList
    }

    override fun toProm(indent: Int): String = buildString {
        if (obj is DLPassingArgument) {
            val passingArgument = obj as DLPassingArgument
            val objRefName = passingArgument.consumer.consumesFrom!!.promRefName
            append("$objRefName.$propertyName")
        }
        else append("/* ERROR PROPERTY ACCESS */")
    }
}