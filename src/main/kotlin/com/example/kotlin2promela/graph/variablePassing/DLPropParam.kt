package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.Prom
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

interface DLPropParam: Prom {
    val offset: Int
    val file: String
    val psiPointer: SmartPsiElementPointer<out PsiElement>?
    val isClassProperty: Boolean
}