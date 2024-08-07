//package com.example.kotlin2promela.graph.action
//
//import com.example.kotlin2promela.graph.FunctionNode
//import com.example.kotlin2promela.graph.variablePassing.DLActionArgument
//import com.example.kotlin2promela.graph.variablePassing.DLActionSelector
//import com.example.kotlin2promela.graph.variablePassing.DLArgument
//import com.example.kotlin2promela.graph.variablePassing.DLSelector
//import com.intellij.psi.PsiElement
//import com.intellij.psi.SmartPsiElementPointer
//
//class DotQDLAction(
//    override val file: String,
//    override val offset: Int,
//    override val performedIn: FunctionNode,
//    override val psiPointer: SmartPsiElementPointer<out PsiElement>?,
//    var left: DLArgument?,
//    var right: DLSelector?
//) : DLAction {
//    
//    override fun getChildActions(): List<DLAction> {
//        val actions = mutableListOf<DLAction>()
//        if (left is DLActionArgument) actions.add((left as DLActionArgument).action)
//        if (right is DLSelector) actions.add((right as DLActionSelector).action)
//        return actions
//    }
//
//    override fun unNest(): List<DLAction> {
//        TODO("Not yet implemented")
//    }
//
//    override fun toProm(indent: Int): String {
//        TODO("Not yet implemented")
//    }
//}