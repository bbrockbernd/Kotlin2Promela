package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.variablePassing.DLArgument
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

class DLPropertyAccessAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    override val psiPointer: SmartPsiElementPointer<out PsiElement>?,
    val propertyName: String,
    var obj: DLArgument?
) : DLAction {
    
    override fun getChildActions(): List<DLAction> = listOf() 

    override fun unNest(): List<DLAction> {
        TODO("Not yet implemented")
    }

    override fun toProm(indent: Int): String {
        TODO("Not yet implemented")
    }
}