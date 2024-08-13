package com.example.kotlin2promela.graph.variablePassing.variableTypes

import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.variablePassing.DLValConsumer
import org.jetbrains.kotlin.psi.KtClass

class DLStruct(val id: String, val fqName: String): DLValType {
    constructor(clazz: KtClass) : this("T_${MyPsiUtils.getId(clazz)}", clazz.fqName!!.toString())
    val propertyConsumers = mutableMapOf<String, DLValConsumer>()
    override fun promType(): String = id
    override fun getAllPrimitivePaths(name: String): List<String> = 
        propertyConsumers.flatMap { (n, consumer) -> consumer.consumesFrom!!.type.getAllPrimitivePaths(n).map { "$name.$it" } }

    override fun getAllPrimitivePaths(): List<String> =
        propertyConsumers.flatMap { (n, consumer) -> consumer.consumesFrom!!.type.getAllPrimitivePaths(n) }
}