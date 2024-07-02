package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.Prom
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

interface DLAction: Prom {
    val file: String
    val offset: Int
    val performedIn: FunctionNode
    val psiPointer: SmartPsiElementPointer<out PsiElement>
}