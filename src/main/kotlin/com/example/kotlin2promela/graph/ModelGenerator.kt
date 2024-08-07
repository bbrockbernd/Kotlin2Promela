package com.example.kotlin2promela.graph

import com.example.kotlin2promela.graph.action.ChannelInitDLAction

class ModelGenerator(private val dlGraph: DeadlockGraph) {
    
    private val funSet = mutableSetOf<FunctionNode>()
    private val chanSet = mutableSetOf<ChannelInitDLAction>()
    
    fun generateForEntryPoint(fn: FunctionNode): String = buildString {   
        exploreFun(fn)
        collectChannels()
        
        chanSet.forEach{
            append(it.promGlobal(4))
            appendLine()
        }
        
        appendLine("init {")
        appendLine("    chan c = [0] of {int}")
        appendLine("    run ${fn.promRefName()}(c)")
        appendLine("    run receiver(c)")
        appendLine("}")
        appendLine()
        funSet.forEach { 
            append(it.toProm())
            appendLine()
        }
        
        appendLine("proctype receiver(chan c) {")
        appendLine("    c?0")
        appendLine("}")
        
    }
    
    private fun exploreFun(fn: FunctionNode) {
        if (funSet.contains(fn)) return
        funSet.add(fn)
        fn.getChildNodes().forEach { exploreFun(it) }
    }
    
    private fun collectChannels() = chanSet.addAll(funSet.flatMap { it.getChanInits() })
    
    
}