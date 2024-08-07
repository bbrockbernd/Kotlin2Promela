package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtParameter

class DLChannelParameter(
    override val offset: Int,
    override val file: String,
    override val psiPointer: SmartPsiElementPointer<KtParameter>?, 
    override val isClassProperty: Boolean
) : DLPropParam, DLValProvider<DLChannelValType>() {
    override fun toProm(indent: Int): String = "chan ${promRefName}"
    override val promRefName = "ch$offset" 
}