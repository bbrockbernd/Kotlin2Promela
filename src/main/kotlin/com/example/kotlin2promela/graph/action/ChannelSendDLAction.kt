package com.example.kotlin2promela.graph.action

import com.example.kotlin2promela.graph.FunctionNode

class ChannelSendDLAction(override val file: String, override val offset: Int, override val performedIn: FunctionNode) : DLAction{
}