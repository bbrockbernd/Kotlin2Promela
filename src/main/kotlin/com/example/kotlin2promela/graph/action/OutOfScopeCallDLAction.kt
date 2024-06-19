package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode

class OutOfScopeCallDLAction(
    override val file: String,
    override val offset: Int,
    override val performedIn: FunctionNode
) : DLCallWithArguments() {
    override val args = mutableListOf<DLAction>()
}