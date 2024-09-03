package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLArgument
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression

class ChannelRecvDLAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtCallExpression>, 
) : DLCallWithArguments {
    override val returnType: DLValType = DLUnitValType()
    override val args = mutableMapOf<Int, DLArgument>()
    override val implArgs: MutableMap<Int, DLPassingArgument> = mutableMapOf()
    override fun toProm(indent: Int): String = buildString { 
        val provider = (args[-1] as DLPassingArgument).consumer.consumesFrom!!
        appendLineIndented(indent, "${provider.promRefName}?0") 
    }
}