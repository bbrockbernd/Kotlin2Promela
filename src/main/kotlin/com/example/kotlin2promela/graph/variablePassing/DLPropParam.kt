package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.Prom
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

abstract class DLPropParam: Prom, DLValProvider() {
    abstract val offset: Int
    abstract val file: String
    abstract val psiPointer: SmartPsiElementPointer<out PsiElement>?
    abstract val isClassProperty: Boolean
}