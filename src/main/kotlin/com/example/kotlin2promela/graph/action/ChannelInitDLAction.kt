package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression

class ChannelInitDLAction(
    val id: String,
    override val file: String, 
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<KtCallExpression>,
    val channelCapacity: Int
) : DLAction {
    override fun getChildActions(): List<DLAction> = emptyList()
    override fun unNest(): List<DLAction> = listOf()

    override fun toProm(indent: Int): String = ""
    
    fun promGlobal(nChans: Int): String = buildString {
        appendLine("chan glob_$globalRefName[$nChans] = [${channelCapacity}] of {int}")
        appendLine("int glob_${globalRefName}_counter = 0")
        appendLine("inline new_$globalRefName(ch) {")
        appendLineIndented(1, "atomic {")
        appendLineIndented(2, "ch = glob_$globalRefName[glob_${globalRefName}_counter]")
        appendLineIndented(2, "glob_${globalRefName}_counter = glob_${globalRefName}_counter + 1")
        appendLineIndented(2, "assert (glob_${globalRefName}_counter < $nChans)")
        appendLineIndented(1, "}")
        appendLine("}")
    }
    
    val globalRefName = "ch_$id"
}