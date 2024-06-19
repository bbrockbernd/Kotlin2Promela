package com.example.kotlin2promela

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

class ElementFilters {
    companion object {
        
        fun isNamedFunction(psiElement: PsiElement) = psiElement is KtNamedFunction
        fun isFunction(psiElement: PsiElement) = psiElement is KtFunction
        fun isCall(psiElement: PsiElement) = psiElement is KtCallExpression
        
        fun runBlockingBuilderDeclaration(psiElement: PsiElement) =
            psiElement is KtNamedFunction && psiElement.fqName?.toString() == "kotlinx.coroutines.runBlocking" 
        fun launchBuilderDeclaration(psiElement: PsiElement) = 
            psiElement is KtNamedFunction && psiElement.fqName?.toString() == "kotlinx.coroutines.launch" 
        fun asyncBuilderDeclaration(psiElement: PsiElement) =
            psiElement is KtNamedFunction && psiElement.fqName?.toString() == "kotlinx.coroutines.async" 
        fun channelConstructor(psiElement: PsiElement) =
            psiElement is KtNamedFunction && psiElement.fqName?.toString() == "kotlinx.coroutines.channels.Channel"
        
        fun isLaunchBuilder(el: PsiElement): Boolean {
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "launch") {
                        val funDef = callee.reference?.resolve()
                        return funDef?.let { launchBuilderDeclaration(it) } == true
                    }
                }
            }
            return false
        }

        fun isAsyncBuilder(el: PsiElement): Boolean {
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "async") {
                        val funDef = callee.reference?.resolve()
                        return funDef?.let { asyncBuilderDeclaration(it) } == true
                    }
                }
            }
            return false
        }
        
        fun isChannelInit(el: PsiElement): Boolean {
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression) {
                    if (callee.getReferencedName() == "Channel") {
                        val funDef = callee.reference?.resolve()
                        return funDef?.let { channelConstructor(it) } == true
                    }
                }
            }
            return false
        }
        
        
    }
}