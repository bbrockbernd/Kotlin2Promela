package com.example.kotlin2promela

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.kotlin.psi.*

class MyPsiUtils {
    companion object {
        fun getUrl(element: PsiElement): String? {
            if (!element.isPhysical) return null
            val containingFile = if (element is PsiFileSystemItem) element else element.containingFile
            if (containingFile == null) return null
            val virtualFile = containingFile.virtualFile ?: return null
            return if (element is PsiFileSystemItem) virtualFile.url else virtualFile.url + "#" + element.textOffset
        }

        fun getId(element: PsiElement): String? {
            if (!element.isPhysical) return null
            val containingFile = if (element is PsiFileSystemItem) element else element.containingFile
            if (containingFile == null) return null
            val virtualFile = containingFile.virtualFile ?: return null
            val hash = (virtualFile.url.hashCode() + element.textOffset.hashCode()).mod(10000)
            return "${virtualFile.nameWithoutExtension}_${element.textOffset}_$hash"
        }

        fun findAllChildren(
            startElement: PsiElement?,
            condition: (PsiElement) -> Boolean,
            fenceCondition: (PsiElement) -> Boolean,
            pruneOnCondition: Boolean = false,
            includeStart: Boolean = false
        ): List<PsiElement> {
            val foundChildren = mutableListOf<PsiElement>()
            startElement?.accept(object : PsiRecursiveElementVisitor() {
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
        
        fun getArgumentIndex(element: PsiElement): Int {
            val argList = findParent(element, { it is KtValueArgumentList }, { it is KtFile }) as KtCallExpression
            return argList.valueArguments.indexOf(element)
        }

        fun getParameterIndex(element: PsiElement): Int {
            val paramList = findParent(element, { it is KtParameterList }, { it is KtFile }) as KtCallExpression
            return paramList.valueArguments.indexOf(element)
        }
        
        fun findAllChildren(
            startElement: PsiElement,
            pruneOnCondition: Boolean = false,
            includeStart: Boolean = false,
            condition: (PsiElement) -> Boolean
        ): List<PsiElement> {
            return findAllChildren(startElement, condition, { false }, pruneOnCondition, includeStart)
        }

        fun findFunctionDefinitions(file: VirtualFile, project: Project): List<KtFunction> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
            return findAllChildren(psiFile) { ElementFilters.isNamedFunction(it) } as List<KtFunction>
        }
        
        fun findClassDefinitions(file: VirtualFile, project: Project): List<KtClass> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
            return findAllChildren(psiFile) { ElementFilters.isClass(it) } as List<KtClass>
        }

        fun getFunForCall(element: KtCallExpression): KtFunction? {
            val callee = element.calleeExpression
            if (callee is KtNameReferenceExpression) {
                val psiFn = callee.reference?.resolve()
                if (psiFn is KtFunction) return psiFn
            }
            return null
        }

        fun getClassForCall(element: KtCallExpression): KtClass? {
            val callee = element.calleeExpression
            if (callee is KtNameReferenceExpression) {
                val psiClazz = callee.reference?.resolve()
                if (psiClazz is KtClass) return psiClazz
            }
            return null
        }
        
        fun getAsyncBuilderLambda(element: KtCallExpression): KtFunction? {
            return element.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.functionLiteral
        }

        fun findParent(
            startElement: PsiElement,
            condition: (PsiElement) -> Boolean,
            fenceCondition: (PsiElement) -> Boolean
        ): PsiElement? {
            var currentElement: PsiElement? = startElement
            while (currentElement != null) {
                if (fenceCondition(currentElement)) return null
                if (condition(currentElement)) return currentElement
                currentElement = currentElement.parent
            }
            return null
        }

        fun getCapacityForChannelInit(call: KtCallExpression): Int {
            if (call.valueArguments.isNotEmpty()) {
                val valArg = call.valueArguments.first()
                if (valArg is KtValueArgument && valArg.getArgumentExpression() != null) {
                    val text = valArg.getArgumentExpression()!!.text
                    try {
                        return Integer.parseInt(text)
                    } catch (e: NumberFormatException) {
                        return 0
                    }
                }
            }
            return 0
        }
    }
}