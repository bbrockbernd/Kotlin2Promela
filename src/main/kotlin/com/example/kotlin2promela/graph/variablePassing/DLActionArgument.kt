package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.action.DLAction

class DLActionArgument(val action: DLAction) : DLArgument {
    override val offset = action.offset
}