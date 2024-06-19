package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode

interface DLAction {
    val file: String
    val offset: Int
    val performedIn: FunctionNode
}