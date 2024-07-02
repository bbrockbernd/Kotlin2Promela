package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLArgument
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression

class CallDLAction (
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    receiving: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtCallExpression>,
) : CallWithReceiverDLAction(receiving) {
    override val args = mutableListOf<DLArgument>()

    // TODO original implementation created buffered child chan [1]. WHY?
    override fun toProm(indent: Int): String = buildString {
        appendLineIndented(indent, "run ${receiving.promRefName()}(${promArgs()})")
        appendLineIndented(indent, "child_$offset?0")
    }
}