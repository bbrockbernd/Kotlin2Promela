package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode

class CallDLAction (
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode,
    val receiving: FunctionNode
) : DLCallWithArguments() {
    override val args = mutableListOf<DLAction>()
}