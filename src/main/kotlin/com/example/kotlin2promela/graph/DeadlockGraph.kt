package com.example.kotlin2promela.graph

import com.example.kotlin2promela.graph.action.CallWithReceiverDLAction
import com.example.kotlin2promela.graph.action.ChannelInitDLAction
import org.jetbrains.kotlin.psi.KtFunction
import java.util.*

class DeadlockGraph {
    private val funMap = mutableMapOf<String, FunctionNode>()
    val channelInits = mutableListOf<ChannelInitDLAction>()


    fun getOrCreateFunction(func: KtFunction): FunctionNode {
        val id = FunctionNode.generateId(func)
        return funMap.getOrPut(id) {
            val node = FunctionNode(func)
            node
        }
    }
    
    fun removeFunction(id: String) {
        assert(funMap[id]?.calledBy?.isEmpty() == true && funMap[id]?.getChildNodes()?.isEmpty() == true)
        funMap.remove(id)
    }
    
    fun removeFunction(fn: FunctionNode) {
        removeFunction(fn.id)
    }
    
    fun getFunctions(): List<FunctionNode> {
        return funMap.values.toList()
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
    
}