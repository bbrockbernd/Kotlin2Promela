package com.example.kotlin2promela

import com.example.kotlin2promela.graph.processing.GraphBuilder
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType

class TestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val graph = GraphBuilder(e.project!!)
            .setRelevantFiles(getRelevantFiles(e.project!!, null))
            .initGraph()
            .channelFlow()
            .unNestCalls()
            .get()
        println("break")
        
        
    }
    
    private fun getRelevantFiles(project: Project, scope: AnalysisScope?): List<VirtualFile> {
        val relevantFiles = mutableListOf<VirtualFile>()
        ProjectFileIndex.getInstance(project).iterateContent {
            if (it.fileType is KotlinFileType && scope?.contains(it) != false) {
                relevantFiles.add(it)
            }
            true
        }
        return relevantFiles
    }
}