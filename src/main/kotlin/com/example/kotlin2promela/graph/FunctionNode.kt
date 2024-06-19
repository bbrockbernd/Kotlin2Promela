package com.example.kotlin2promela.graph

import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.action.CallDLAction
import com.example.kotlin2promela.graph.action.DLAction
import com.example.kotlin2promela.graph.action.DLCallWithArguments
import org.jetbrains.kotlin.psi.KtFunction

class FunctionNode(val id: String, val fqName: String) {
    var visited = false
    constructor(function: KtFunction) : this(generateId(function), function.fqName.toString()) 
    
    val actionList = mutableListOf<DLAction>()
    fun getChildNodes(): List<FunctionNode> {
        return extractNodesFromActionList(actionList)
    }
    private fun extractNodesFromActionList(list: List<DLAction>): List<FunctionNode> {
        return list
            .filterIsInstance<DLCallWithArguments>()
            .flatMap { 
                if (it is CallDLAction) listOf(it.receiving) + extractNodesFromActionList(it.args) 
                else extractNodesFromActionList(it.args)
            }
    }
    
    companion object {
        fun generateId(function: KtFunction): String {
            return MyPsiUtils.getUrl(function)!!
        }
    }
}