package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.ChannelRecvDLAction
import com.example.kotlin2promela.graph.action.ChannelSendDLAction
import com.example.kotlin2promela.graph.variablePassing.DLChannelParameter
import com.example.kotlin2promela.graph.variablePassing.DLPassingArgument
import com.example.kotlin2promela.graph.variablePassing.DLValConsumer
import com.example.kotlin2promela.graph.variablePassing.DLValProducer
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelVal
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.*

class GraphChannelLinker(val dlGraph: DeadlockGraph) {

    fun link() {
        println("----LINK-CHANNELS----")
        dlGraph.channelInits.forEach { chanInit ->
            val psiElement = chanInit.psiPointer.element!!
            ReferencesSearch.search(psiElement).findAll().forEach { usage ->
                if (usage.element !is KtNameReferenceExpression) {
                    throw IllegalStateException("Expected name reference but is: ${usage.element.javaClass.name}")
                }
                processUsage(
                    usage.element as KtNameReferenceExpression,
                    chanInit,
                    chanInit.performedIn,
                    chanInit.offset
                )
            }
        }
        dlGraph.getFunctions().forEach { fn ->
            fn.parameterList.filterIsInstance<DLChannelParameter>().forEach { chanParam ->
                val psiElement = chanParam.psiPointer?.element!!
                ReferencesSearch.search(psiElement).findAll().forEach { usage ->
                    if (usage.element !is KtNameReferenceExpression) {
                        throw IllegalStateException("Expected name reference but is: ${usage.element.javaClass.name}")
                    }
                    processUsage(usage.element as KtNameReferenceExpression, chanParam, fn, chanParam.offset)
                }
            }
        }
    }

    private fun processUsage(
        usage: KtNameReferenceExpression,
        chanProd: DLValProducer<DLChannelVal>,
        prodFun: FunctionNode,
        offset: Int
    ) {
        // First check if this usage is in same function
        val usagePsiFun = MyPsiUtils.findParent(usage, { it is KtFunction }, { it is KtFile })!! as KtFunction
        val usageFun = dlGraph.getOrCreateFunction(usagePsiFun)

        val chanProducer = if (prodFun != usageFun) { // If not pass is implicit
            val path = dlGraph.BFSDown(prodFun, usageFun)
            if (path.isEmpty()) throw IllegalStateException("Path must be at least 1 call")

            path.first().implArgs.computeIfAbsent(offset) { _ ->
                DLPassingArgument(offset, DLValConsumer.createAndLinkChannelConsumer(chanProd))
            }

            // Hack use chaninit textOffset to differentiate
            path.first().receiving.implicitParameters.computeIfAbsent(offset) {
                DLChannelParameter(
                    offset,
                    usage.containingFile.virtualFile.path,
                    null
                )
            }

            for (i in 1..path.lastIndex) {
                val param = path[i].performedIn.implicitParameters[offset] as DLChannelParameter
                path[i].implArgs.computeIfAbsent(offset) {
                    DLPassingArgument(
                        offset,
                        DLValConsumer.createAndLinkChannelConsumer(param)
                    )
                }
                path[i].receiving.implicitParameters.computeIfAbsent(offset) { _ ->
                    DLChannelParameter(
                        offset,
                        usage.containingFile.virtualFile.path,
                        null
                    )
                }
            }
            path.last().receiving.implicitParameters[offset] as DLChannelParameter
        } else chanProd



        if (ElementFilters.isUsageValueArgument(usage)) {
            val psiCall = MyPsiUtils.findParent(usage, { it is KtCallExpression }, { it is KtFile }) as KtCallExpression
            val dlCall = usageFun.getCallFor(psiCall)
            dlCall.args.add(
                DLPassingArgument(
                    usage.textOffset,
                    DLValConsumer.createAndLinkChannelConsumer(chanProducer)
                )
            )
        } else if (ElementFilters.isSendUsage(usage)) {
            val dotQual = usage.parent as KtDotQualifiedExpression
            val callExpr = dotQual.selectorExpression as KtCallExpression
            val dlCall = usageFun.getCallFor(callExpr)
            if (dlCall !is ChannelSendDLAction) throw IllegalStateException("Expected channel send call")
            dlCall.consumesFrom = chanProducer
            chanProducer.producesFor.add(dlCall)
        } else if (ElementFilters.isReceiveUsage(usage)) {
            val dotQual = usage.parent as KtDotQualifiedExpression
            val callExpr = dotQual.selectorExpression as KtCallExpression
            val dlCall = usageFun.getCallFor(callExpr)
            if (dlCall !is ChannelRecvDLAction) throw IllegalStateException("Expected channel receive call")
            dlCall.consumesFrom = chanProducer
            chanProducer.producesFor.add(dlCall)
        }
    }
}