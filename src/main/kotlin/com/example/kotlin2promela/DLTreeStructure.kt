package com.example.kotlin2promela

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.openapi.project.Project
import com.intellij.slicer.SliceRootNode

class DLTreeStructure(private val project: Project, private val sliceRootNode: SliceRootNode): AbstractTreeStructureBase(project) {
    override fun getRootElement(): Any = sliceRootNode

    override fun commit() {}

    override fun hasSomethingToCommit(): Boolean = false

    override fun getProviders(): MutableList<TreeStructureProvider>? = mutableListOf() 
}