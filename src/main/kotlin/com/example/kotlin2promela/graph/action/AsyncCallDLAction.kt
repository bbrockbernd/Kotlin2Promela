package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLArgument
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression

class AsyncCallDLAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    receiving: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtCallExpression>, 
) : CallWithCalleeFunDLAction(receiving) {
    override val args: MutableMap<Int, DLArgument> = mutableMapOf()
    
    // TODO original implementation created buffered child chan [1]. WHY?
    override fun toProm(indent: Int): String = buildString { 
        appendLineIndented(indent, "run ${callee.promRefName()}(${promArgs()})")
        appendLineIndented(indent, "run receiver(child_$offset)")
    }
}