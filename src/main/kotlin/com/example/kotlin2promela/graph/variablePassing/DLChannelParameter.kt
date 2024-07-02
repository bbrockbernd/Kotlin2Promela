package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelVal
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtParameter

class DLChannelParameter(
    override val offset: Int,
    override val file: String,
    override val psiPointer: SmartPsiElementPointer<KtParameter>?
) : DLParameter, DLValProducer<DLChannelVal>() {
    override fun toProm(indent: Int): String = "chan ${promRefName()}"
    override fun promRefName() = "ch$offset" 
}