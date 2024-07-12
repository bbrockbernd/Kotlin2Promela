package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.Prom
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

interface DLAction: Prom {
    val file: String
    val offset: Int
    val performedIn: FunctionNode
    val psiPointer: SmartPsiElementPointer<out PsiElement>?
    fun getChildActions(): List<DLAction>
    fun getDescendants(): List<DLAction> = getChildActions().let{ actions -> actions.flatMap { it.getDescendants() } + actions }
    fun hasChild(dlAction: DLAction): Boolean = getChildActions().contains(dlAction)
    fun getParent(): DLAction? = performedIn.getParentFor(this)
    fun unNest(): List<DLAction>
}