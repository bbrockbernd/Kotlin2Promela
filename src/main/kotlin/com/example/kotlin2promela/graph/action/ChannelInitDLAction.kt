package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLValProducer
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelVal
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtProperty

class ChannelInitDLAction(
    override val file: String, 
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtProperty>
) : DLAction, DLValProducer<DLChannelVal>() {
    override fun toProm(indent: Int): String = 
        buildString { appendLineIndented(indent, "chan ${promRefName()} = [0] of {int}") }
    override fun promRefName() = "ch$offset"
}