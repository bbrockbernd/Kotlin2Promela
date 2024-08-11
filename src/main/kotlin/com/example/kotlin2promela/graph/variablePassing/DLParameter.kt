package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtParameter

class DLParameter(
    override val offset: Int,
    override val file: String,
    override val psiPointer: SmartPsiElementPointer<KtParameter>?, 
    override val isClassProperty: Boolean,
    override val type: DLValType,
) : DLPropParam() {
    override fun toProm(indent: Int): String = "chan ${promRefName}"
    override val promRefName = "ch$offset" 
}