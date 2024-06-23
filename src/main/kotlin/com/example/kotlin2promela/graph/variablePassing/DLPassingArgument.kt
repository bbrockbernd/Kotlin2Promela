package com.example.kotlin2promela.graph.variablePassing

import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLVal

class DLPassingArgument(override val offset: Int, val consumer: DLValConsumer<out DLVal>) : DLArgument {
}