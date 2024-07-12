package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLArgument
import com.example.kotlin2promela.graph.variablePassing.DLValProvider
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression

class CallDLAction (
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    callee: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtCallExpression>,
) : CallWithCalleeFunDLAction(callee) {
    override val args = mutableListOf<DLArgument>()
    
    var returnType: DLValType = DLUnitValType()

    // TODO original implementation created buffered child chan [1]. WHY?
    override fun toProm(indent: Int): String {
        return toProm(indent, "0")
    }
    
    fun toProm(indent: Int, recRef: String) = buildString {
        appendLineIndented(indent, "run ${callee.promRefName()}(${promArgs()})")
        appendLineIndented(indent, "child_$offset?$recRef")
    }
}