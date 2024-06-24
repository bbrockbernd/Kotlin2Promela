package com.example.kotlin2promela.graph

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.action.CallWithReceiverDLAction
import com.example.kotlin2promela.graph.action.DLAction
import com.example.kotlin2promela.graph.action.DLCallWithArguments
import com.example.kotlin2promela.graph.variablePassing.DLActionArgument
import com.example.kotlin2promela.graph.variablePassing.DLChannelParameter
import com.example.kotlin2promela.graph.variablePassing.DLParameter
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class FunctionNode(val id: String, val fqName: String, val parameterList: List<DLParameter>, val psiPointer: SmartPsiElementPointer<KtFunction>) {
    constructor(function: KtFunction) : this(generateId(function), function.fqName.toString(), extractParameters(function), function.createSmartPointer()) 
    
    val actionList = mutableListOf<DLAction>()
    var visited = false
    val implicitParameters = mutableMapOf<Int, DLParameter>()
    
    fun getChildNodes(): List<FunctionNode> {
        return extractNodesFromActionList(actionList)
    }
    private fun extractNodesFromActionList(list: List<DLAction>): List<FunctionNode> {
        return list
            .filterIsInstance<DLCallWithArguments>()
            .flatMap { call ->
                val actionListFromArgs = call.args.filterIsInstance<DLActionArgument>().map { it.action }
                if (call is CallWithReceiverDLAction) listOf(call.receiving) + extractNodesFromActionList(actionListFromArgs) 
                else extractNodesFromActionList(actionListFromArgs)
            }
    }
    
    fun getCallsToChildNodes(): List<CallWithReceiverDLAction> {
        return extractCallsToChildNodes(actionList)
    }

    private fun extractCallsToChildNodes(list: List<DLAction>): List<CallWithReceiverDLAction> {
        return list
            .filterIsInstance<DLCallWithArguments>()
            .flatMap { call ->
                val actionListFromArgs = call.args.filterIsInstance<DLActionArgument>().map { it.action }
                if (call is CallWithReceiverDLAction) listOf(call) + extractCallsToChildNodes(actionListFromArgs)
                else extractCallsToChildNodes(actionListFromArgs)
            }
    }
    override fun equals(other: Any?): Boolean {
        if (other !is FunctionNode) return false
        return id == other.id
    }
    
    fun getCallFor(element: KtCallExpression): DLCallWithArguments {
        return getCallsWithArguments().first { it.offset == element.textOffset }
    }
    
    fun getCallsWithArguments(): List<DLCallWithArguments> {
        return extractCallsWithArguments(actionList)
    }

    private fun extractCallsWithArguments(list: List<DLAction>): List<DLCallWithArguments> {
        return list
            .filterIsInstance<DLCallWithArguments>()
            .flatMap { call ->
                val actionListFromArgs = call.args.filterIsInstance<DLActionArgument>().map { it.action }
                listOf(call) + extractCallsToChildNodes(actionListFromArgs)
            }
    }
    
    companion object {
        fun generateId(function: KtFunction): String {
            return MyPsiUtils.getUrl(function)!!
        }
        
        fun extractParameters(function: KtFunction): List<DLParameter> {
            return function.valueParameters
                .filter { ElementFilters.isChannelParameter(it)}
                .map { DLChannelParameter(it.textOffset, it.containingFile.virtualFile.path, it.createSmartPointer())}
        }
        
    }
}