package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtProperty

class DLProperty(
    override val offset: Int,
    override val file: String,
    override val psiPointer: SmartPsiElementPointer<KtProperty>?,
    override val isClassProperty: Boolean, 
    override val type: DLValType
) : DLPropParam() {
    
    override fun toProm(indent: Int): String = promRefName

    override val promRefName = "ch_$offset"
}
    
