package com.example.kotlin2promela.graph

import com.example.kotlin2promela.graph.action.ChannelInitDLAction
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLStruct
import java.util.*

class ModelGenerator(private val dlGraph: DeadlockGraph) {
    
    private val funSet = mutableSetOf<FunctionNode>()
    private val chanSet = mutableSetOf<ChannelInitDLAction>()
    
    fun generateForEntryPoint(fn: FunctionNode): String = buildString {   
        exploreFun(fn)
        collectChannels()
        funSet
            .filter { it.isConstructor }
            .map { it.returnType }
            .filterIsInstance<DLStruct>()
            .topologicalSortedTypes { it.propertyConsumers.values
                .map { tp -> tp.consumesFrom?.type!! }
                .filterIsInstance<DLStruct>()
            }
            .forEach {
                appendLine("/* class ${it.fqName} */")
                appendLine("typedef ${it.promType()} {")
                it.propertyConsumers.forEach { (name, consumer) ->
                    appendLine("    ${consumer.consumesFrom!!.type.promType()} $name")
                }
                appendLine("}")
            }
        
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
    
    class TypeDep<T>(val dlType: T) {
        val children: MutableList<TypeDep<T>> = mutableListOf()
        var parents: MutableList<TypeDep<T>> = mutableListOf()
    }
    
    private inline fun <T> List<T>.topologicalSortedTypes(getChildren: (T) -> List<T> ): List<T> {
        // construct type tree
        val map: MutableMap<T, TypeDep<T>> = mutableMapOf()
        this.forEach { currentType -> map[currentType] = TypeDep(currentType) }
        this.forEach { currentType -> 
            getChildren(currentType).forEach { childType -> 
                    map[currentType]!!.children.add(map[childType]!!)
                    map[childType]!!.parents.add(map[currentType]!!)
                }
        }
        
        val toVisit: MutableList<TypeDep<T>> = map.values.filter { it.parents.isEmpty() }.toMutableList()
        val sorted: MutableList<TypeDep<T>> = mutableListOf()
        
        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeLast()
            sorted.add(current)
            current.children
                .toList()
                .forEach { child ->
                    current.children.remove(child)
                    child.parents.remove(current)
                    if (child.parents.isEmpty()) toVisit.add(0, child)
            }
        }
        return sorted.reversed().map { it.dlType }
    }
    
    private fun collectChannels() = chanSet.addAll(funSet.flatMap { it.getChanInits() })
}