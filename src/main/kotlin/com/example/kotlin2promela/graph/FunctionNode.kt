package com.example.kotlin2promela.graph

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.action.*
import com.example.kotlin2promela.graph.variablePassing.DLActionArgument
import com.example.kotlin2promela.graph.variablePassing.DLChannelParameter
import com.example.kotlin2promela.graph.variablePassing.DLParameter
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class FunctionNode(val id: String, val fqName: String, val parameterList: List<DLParameter>, val psiPointer: SmartPsiElementPointer<KtFunction>): Prom {
    constructor(function: KtFunction) : this(generateId(function), function.fqName?.toString() ?: "lambda", extractParameters(function), function.createSmartPointer()) 
    
    var actionList = mutableListOf<DLAction>()
    var visited = false
    val implicitParameters = mutableMapOf<Int, DLParameter>()
    val calledBy = mutableListOf<CallWithReceiverDLAction>()
    
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

    override fun hashCode(): Int {
        return id.hashCode()
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

    
    
    /**
     * Will result in:
     * ```
     * proctype funID(chan implParam1, mutex implParam2, chan realParam1, chan ret) {
     *     [actions]
     *     ret!0
     * }
     * ```
     */
    override fun toProm(indent: Int): String = buildString { 
        // Function comment and signature
        appendLine("/* function: ${fqName} */")
        appendLine("proctype ${promRefName()}(${paramListProm()}) {")

        // Channel inits
        val channelInits = actionList
            .filterIsInstance<ChannelInitDLAction>()
        if (channelInits.isNotEmpty()) appendLineIndented(1, "/* Channel initializations */")
        channelInits.forEach { append(it.toProm(1)) }
        if (channelInits.isNotEmpty()) appendLine()
        
        // Return channels
        val returnChannelActions = actionList
            .filterIsInstance<CallWithReceiverDLAction>()
        if (returnChannelActions.isNotEmpty()) appendLineIndented(1, "/* Function call return channels */")
        returnChannelActions
            .forEach { appendLineIndented(1, "chan child_${it.offset} = [0] of {int}") }
        if (returnChannelActions.isNotEmpty()) appendLine()
        
        //Sync and async calls
        actionList
            .filter { it is CallWithReceiverDLAction || it is ChannelRecvDLAction || it is ChannelSendDLAction }
            .forEach { action -> 
            append(action.toProm(1))
        }
        appendLineIndented(1, "ret!0")
        appendLine("}")
    }
    
    fun promRefName() = id
    
    
    private fun paramListProm(): String = buildString {
        val myPromParams = implicitParameters.toSortedMap().values + parameterList
        myPromParams.forEach { param ->
            append(param.toProm(), "; ")
        }
        append("chan ret")
    }
}