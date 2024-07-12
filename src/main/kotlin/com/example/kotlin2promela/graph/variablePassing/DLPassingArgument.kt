package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType

class DLPassingArgument(override val offset: Int, val consumer: DLValConsumer<out DLValType>) : DLArgument {
}