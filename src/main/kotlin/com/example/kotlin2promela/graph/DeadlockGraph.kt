package com.example.kotlin2promela.graph

import com.example.kotlin2promela.graph.action.CallWithReceiverDLAction
import com.example.kotlin2promela.graph.action.ChannelInitDLAction
import org.jetbrains.kotlin.psi.KtFunction
import java.util.*

class DeadlockGraph {
    private val funMap = mutableMapOf<String, FunctionNode>()
    private val fileMap = mutableMapOf<String, MutableSet<FunctionNode>>()
    val channelInits = mutableListOf<ChannelInitDLAction>()


    fun getOrCreateFunction(func: KtFunction): FunctionNode {
        val id = FunctionNode.generateId(func)
        val filePath = func.containingFile.virtualFile.path
        return funMap.getOrPut(id) {
            val node = FunctionNode(func)
            addToFileMap(node, filePath)
            node
        }
    }
    
    fun BFSDown(fromParent: FunctionNode, toChild: FunctionNode): List<CallWithReceiverDLAction> {
        clearVisited()
        
        data class ToVisit(val nodeToVisit: FunctionNode, val callPath: List<CallWithReceiverDLAction>)
        val queue: Queue<ToVisit> = LinkedList()
        queue.add(ToVisit(fromParent, listOf()))
        while (queue.isNotEmpty()) {
            val currentTask = queue.poll()
            val currentNode = currentTask.nodeToVisit
            if (currentNode == toChild) return currentTask.callPath
            currentNode.visited = true
            
            currentNode.getCallsToChildNodes()
                .filter { !it.receiving.visited }
                .forEach { queue.add(ToVisit(it.receiving, currentTask.callPath + it)) }
            
        }
        return listOf()
    }
    
    fun clearVisited() {
        funMap.values.forEach { it.visited = false }
    }
    
    private fun addToFileMap(fn: FunctionNode, filePath: String) {
        val set = fileMap.getOrPut(filePath) { mutableSetOf() }
        set.add(fn)
    }
    
}