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
        GraphChannelLinker(dlGraph).link()
        return this
    }
    
    fun unNestCalls(): GraphBuilder {
        GraphUnNester(dlGraph).unNest()
        return this
    }

}



// SLICER shite for when I change my mind

//val params = SliceAnalysisParams()
//params.dataFlowToThis = false
//params.scope = AnalysisScope(project)
//params.showInstanceDereferences = true
//
//val rootUsage = LanguageSlicing.getProvider(it.psiPointer.element!!).createRootUsage(it.psiPointer.element!!, params)
//val sliceRootNode = SliceRootNode(project, DuplicateMap(), rootUsage)
//val treeStructure = DLTreeStructure(project, sliceRootNode)
//
//val sliceProvider = KotlinSliceProvider()
//val results = sliceProvider.leafAnalyzer.calcLeafExpressions(sliceRootNode, treeStructure, sliceProvider.leafAnalyzer.createMap())
