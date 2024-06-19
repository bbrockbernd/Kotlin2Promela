package com.example.kotlin2promela

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class MyPsiUtils {
    companion object {
        fun getUrl(element: PsiElement): String? {
            if (!element.isPhysical) return null
            val containingFile = if (element is PsiFileSystemItem) element else element.containingFile
            if (containingFile == null) return null
            val virtualFile = containingFile.virtualFile ?: return null
            return if (element is PsiFileSystemItem) virtualFile.url else virtualFile.url + "#" + element.textOffset
        }
        
        fun findAllChildren(
            startElement: PsiElement?,
            condition: (PsiElement) -> Boolean,
            fenceCondition: (PsiElement) -> Boolean,
            pruneOnCondition: Boolean = false,
            includeStart: Boolean = false
        ): List<PsiElement> {
            val foundChildren = mutableListOf<PsiElement>()
            startElement?.accept(object: PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if ((startElement != element) && fenceCondition(element)) return
                    if ((startElement != element || includeStart) && condition(element)) {
                        foundChildren.add(element)
                        if (pruneOnCondition) return
                    }
                    super.visitElement(element)
                }
            })
            return foundChildren
        }
        
        fun findAllChildren(startElement: PsiElement, pruneOnCondition: Boolean = false, includeStart: Boolean = false, condition: (PsiElement) -> Boolean): List<PsiElement>{
            return findAllChildren(startElement, condition, { false }, pruneOnCondition, includeStart)
        }

        fun findFunctionDefinitions(file: VirtualFile, project: Project): List<KtFunction> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
            return findAllChildren(psiFile) { ElementFilters.isNamedFunction(it) } as List<KtFunction>
        }
        
        fun getFunForCall(element: KtCallExpression): KtFunction? {
            val callee = element.calleeExpression
            if (callee is KtNameReferenceExpression) {
                val psiFn = callee.reference?.resolve()
                if (psiFn is KtFunction) return psiFn
            }
            return null
        }
        
        fun getAsyncBuilderLambda(element: KtCallExpression): KtFunction? {
            return element.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.functionLiteral
        }
    }
}