package com.example.kotlin2promela.graph

import com.example.kotlin2promela.graph.action.CallWithCalleeFunDLAction
import com.example.kotlin2promela.graph.action.ChannelInitDLAction
import com.example.kotlin2promela.graph.action.DLAction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import java.util.*

class DeadlockGraph {
    private val funMap = mutableMapOf<String, FunctionNode>()
    val channelInits = mutableListOf<ChannelInitDLAction>()
    val channelOperations = mutableListOf<DLAction>() // sends and receives (possible close and cancel would go here as well)


    fun getOrCreateFunction(func: KtFunction): FunctionNode {
        val id = FunctionNode.generateId(func)
        return funMap.getOrPut(id) {
            val node = FunctionNode(func)
            node
        }
    }
    
    fun getOrCreateFunction(clazz: KtClass): FunctionNode {
        val id = FunctionNode.generateId(clazz)
        return funMap.getOrPut(id) {
            val node = FunctionNode(clazz)
            node
        }
    }
    
    fun getFunctionById(funcId: String): FunctionNode? = funMap[funcId]
    
    fun removeFunction(id: String) {
        assert(funMap[id]?.calledBy?.isEmpty() == true && funMap[id]?.getChildNodes()?.isEmpty() == true)
        funMap.remove(id)
    }
    
    fun removeFunction(fn: FunctionNode) {
        removeFunction(fn.info.id)
    }
    
    fun getFunctions(): List<FunctionNode> {
        return funMap.values.toList()
    }
    
    fun BFSDown(fromParent: FunctionNode, toChild: FunctionNode): List<CallWithCalleeFunDLAction> {
        clearVisited()
        
        data class ToVisit(val nodeToVisit: FunctionNode, val callPath: List<CallWithCalleeFunDLAction>)
        val queue: Queue<ToVisit> = LinkedList()
        queue.add(ToVisit(fromParent, listOf()))
        while (queue.isNotEmpty()) {
            val currentTask = queue.poll()
            val currentNode = currentTask.nodeToVisit
            if (currentNode == toChild) return currentTask.callPath
            currentNode.visited = true
            
            currentNode.getCallsToChildNodes()
                .filter { !it.callee.visited }
                .forEach { queue.add(ToVisit(it.callee, currentTask.callPath + it)) }
            
        }
        return listOf()
    }
    
    fun clearVisited() {
        funMap.values.forEach { it.visited = false }
    }
    
}