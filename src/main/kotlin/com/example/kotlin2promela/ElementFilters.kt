package com.example.kotlin2promela

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*

class ElementFilters {
    companion object {
        
        fun isNamedFunction(psiElement: PsiElement) = psiElement is KtNamedFunction
        fun isFunction(psiElement: PsiElement) = psiElement is KtFunction
        fun isClass(psiElement: PsiElement) = psiElement is KtClass
        fun isCall(psiElement: PsiElement) = psiElement is KtCallExpression
        fun isReturn(psiElement: PsiElement) = psiElement is KtReturnExpression
        fun isProperty(psiElement: PsiElement) = psiElement is KtProperty
        fun isDotQualified(psiElement: PsiElement) = psiElement is KtDotQualifiedExpression
        
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
        
        fun isSendCall(el: PsiElement): Boolean {
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression) {
                    if (callee.getReferencedName() == "send") {
                        // TODO fix this actually check fq name!
                        return true
                    }
                }
            }
            return false
        }

        /**
         * Doesn't check actual type
         */
        fun isSendUsage(refexpr: KtNameReferenceExpression): Boolean {
            val parent = refexpr.parent
            if (parent is KtDotQualifiedExpression) {
                val selector = parent.selectorExpression
                return selector?.let { isSendCall(it) } == true
            }
            return false
        }
        
        fun isReceiveCall(el: PsiElement): Boolean {
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression) {
                    if (callee.getReferencedName() == "receive") {
                        return true
                    }
                }
            }
            return false
        }
        
        /**
         * Doesn't check actual type
         */
        fun isReceiveUsage(refexpr: KtNameReferenceExpression): Boolean {
            val parent = refexpr.parent
            if (parent is KtDotQualifiedExpression) {
                val selector = parent.selectorExpression
                return selector?.let { isReceiveCall(it) } == true
            }
            return false
        }
        
        fun isReturnUsage(refexpr: KtNameReferenceExpression): Boolean {
            val parent = refexpr.parent
            return parent is KtReturnExpression
        }
        
        fun isChannelParameter(param: KtParameter): Boolean {
            if (param.typeReference?.nameForReceiverLabel() == "Channel") {
                val typeEl = param.typeReference!!.typeElement
                return isChannelTypeElement(typeEl)
            }
            return false
        }
        
        fun isChannelTypeElement(typeEl: KtTypeElement?): Boolean {
            if (typeEl is KtUserType) {
                val refExpr = typeEl.referenceExpression
                if (refExpr is KtNameReferenceExpression) {
                    val typeDef = refExpr.reference?.resolve()
                    if (typeDef is KtClass) {
                        return typeDef.fqName.toString() == "kotlinx.coroutines.channels.Channel"
                    }
                }
            }
            return false
        }
        
        fun isChannelReturnType(el: KtTypeReference): Boolean {
            if (el.nameForReceiverLabel() == "Channel") {
                val typeEl = el.typeElement
                return isChannelTypeElement(typeEl)
            }
            return false
        }
        
        fun isUsageValueArgument(el: KtNameReferenceExpression): Boolean = el.parent is KtValueArgument
        
    }
}