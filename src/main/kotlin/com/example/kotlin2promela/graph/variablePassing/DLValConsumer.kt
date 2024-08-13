package com.example.kotlin2promela.graph.variablePassing

open class DLValConsumer(var consumesFrom: DLValProvider? = null) {
    
    fun link(provider: DLValProvider) {
        provider.producesFor.add(this)
        consumesFrom = provider
    }
    
    companion object {
        fun createAndLinkChannelConsumer(consumesFrom: DLValProvider): DLValConsumer {
            val me = DLValConsumer(consumesFrom)
            consumesFrom.producesFor.add(me)
            return me
        } 
    }
}