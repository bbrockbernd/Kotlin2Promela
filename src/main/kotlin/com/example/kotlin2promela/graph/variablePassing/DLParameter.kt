package com.example.kotlin2promela.graph.variablePassing

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtParameter

interface DLParameter {
    val offset: Int
    val file: String
    val psiPointer: SmartPsiElementPointer<KtParameter>?
}