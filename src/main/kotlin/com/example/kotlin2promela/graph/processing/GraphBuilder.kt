package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.graph.DeadlockGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class GraphBuilder(private val project: Project) {
    private var relevantFiles: List<VirtualFile> = emptyList()
    private var urlToVirtualFileMap: MutableMap<String, VirtualFile> = mutableMapOf()
    private val dlGraph = DeadlockGraph()
    
    fun get(): DeadlockGraph {
        return dlGraph
    }

    fun setRelevantFiles(relevantFiles: List<VirtualFile>): GraphBuilder {
        this.relevantFiles = relevantFiles
        relevantFiles.forEach { urlToVirtualFileMap[it.url] = it }
        return this
    }

    fun initGraph(): GraphBuilder {
        GraphInitializer(project, dlGraph, relevantFiles).intialize()
        return this
    }
    
    fun channelFlow(): GraphBuilder {
        ReversedLinker(dlGraph).link()
        return this
    }
    
    fun unNestCalls(): GraphBuilder {
        GraphUnNester(dlGraph).unNest()
        return this
    }
    
    fun pruneSyncCalls(): GraphBuilder {
        GraphPruner(dlGraph).prune()
        return this
    }
}
