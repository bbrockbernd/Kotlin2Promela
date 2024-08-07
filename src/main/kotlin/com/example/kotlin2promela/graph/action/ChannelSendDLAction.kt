package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLArgument
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument
import com.example.kotlin2promela.graph.variablePassing.DLValConsumer
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression

class ChannelSendDLAction(override val file: String, override val offset: Int, override val performedIn: FunctionNode,
                          override val psiPointer: SmartPsiElementPointer<KtCallExpression>
) : DLCallWithArguments, DLValConsumer() {
    override val args = mutableMapOf<Int, DLArgument>()
    override val implArgs: MutableMap<Int, DLPassingArgument> = mutableMapOf()
    override fun toProm(indent: Int): String = 
        buildString { appendLineIndented(indent, "${consumesFrom?.promRefName ?: "ERROR" }!0") }
}