package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtProperty

class DLChannelProperty(
    override val offset: Int,
    override val file: String,
    override val psiPointer: SmartPsiElementPointer<KtProperty>?,
    override val isClassProperty: Boolean
) : DLPropParam, DLValProvider<DLChannelValType>() {
    
    override fun toProm(indent: Int): String = promRefName

    override val promRefName = "ch_$offset"
}
    
