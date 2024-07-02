package com.example.kotlin2promela.graph

class ModelGenerator(private val dlGraph: DeadlockGraph) {
    
    private val funSet = mutableSetOf<FunctionNode>()
    
    fun generateForEntryPoint(fn: FunctionNode): String = buildString {   
        exploreFun(fn)
        appendLine("init {")
        appendLine("    chan c = [0] of {int}")
        appendLine("    run ${fn.promRefName()}()")
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
    
    
    
}