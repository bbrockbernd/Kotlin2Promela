package com.example.kotlin2promela.graph.processing

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.DeadlockGraph
import com.example.kotlin2promela.graph.FunctionNode
import com.example.kotlin2promela.graph.action.AssignPropertyDLAction
import com.example.kotlin2promela.graph.action.CallDLAction
import com.example.kotlin2promela.graph.action.ChannelRecvDLAction
import com.example.kotlin2promela.graph.action.ChannelSendDLAction
import com.example.kotlin2promela.graph.variablePassing.*
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.*

class GraphChannelLinker(val dlGraph: DeadlockGraph) {

    fun link() {
        println("----LINK-CHANNELS----")
        linkInits()
        linkParams()
        linkCallReturnValues()
    }

    // Link properties and/or mark functions that are not in properties to correctly unnest later.
    private fun linkCallReturnValues() {
        dlGraph.getFunctions().forEach { fn ->
            if (fn.returnType !is DLUnitValType) {
                fn.calledBy.filterIsInstance<CallDLAction>().forEach { call ->
                    // Mark call as important
                    call.returnType = fn.returnType
                    // if call is party of property -> Link property
                    val callParent = call.getParent()
                    if (callParent is AssignPropertyDLAction) {
                        val provider = DLProperty(callParent.offset, callParent.file, callParent.psiPointer, false, DLChannelValType())
                        callParent.assignee = provider
                        val ktProp: KtProperty = callParent.psiPointer!!.element!!
                        ReferencesSearch.search(ktProp).findAll().forEach { usage ->
                            if (usage.element !is KtNameReferenceExpression) {
                                throw IllegalStateException("Expected name reference but is: ${usage.element.javaClass.name}")
                            }
                            processUsage(usage.element as KtNameReferenceExpression, provider, call.performedIn, provider.offset)
                        }
                    }
                }
            }
        }
    }

    private fun linkInits() {
        dlGraph.channelInits.forEach { chanInit ->
            if (chanInit.getParent() is AssignPropertyDLAction) {
                val chanPropAssignment = chanInit.getParent() as AssignPropertyDLAction
                val prop = DLProperty(chanPropAssignment.offset, chanPropAssignment.file, chanPropAssignment.psiPointer, false, DLChannelValType())
                chanPropAssignment.assignee = prop
                
                val psiElement = chanPropAssignment.psiPointer?.element!!
                ReferencesSearch.search(psiElement).findAll().forEach { usage ->
                    if (usage.element !is KtNameReferenceExpression) {
                        throw IllegalStateException("Expected name reference but is: ${usage.element.javaClass.name}")
                    }
                    processUsage(
                        usage.element as KtNameReferenceExpression,
                        prop,
                        chanInit.performedIn,
                        chanInit.offset
                    )
                }
            }
        }
    }
    
    private fun linkParams() {
        dlGraph.getFunctions().forEach { fn ->
            fn.importantParameters.values.filterIsInstance<DLParameter>().forEach { chanParam ->
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
        chanProd: DLValProvider,
        prodFun: FunctionNode,
        offset: Int
    ) {
        // First check if this usage is in same function
        val usagePsiFun = MyPsiUtils.findParent(usage, { it is KtFunction }, { it is KtFile })!! as KtFunction
        val usageFun = dlGraph.getOrCreateFunction(usagePsiFun)

        val chanProducer = if (prodFun != usageFun) { // If not pass is implicit
            val path = dlGraph.BFSDown(prodFun, usageFun)
            if (path.isEmpty()) {
                throw IllegalStateException("Path must be at least 1 call") 
            }

            path.first().implArgs.computeIfAbsent(offset) { _ ->
                DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(chanProd))
            }

            // Hack use chaninit textOffset to differentiate
            path.first().callee.implicitParameters.computeIfAbsent(offset) {
                DLParameter(
                    offset,
                    usage.containingFile.virtualFile.path,
                    null,
                    false,
                    DLChannelValType()
                )
            }

            for (i in 1..path.lastIndex) {
                val param = path[i].performedIn.implicitParameters[offset] as DLParameter
                path[i].implArgs.computeIfAbsent(offset) { DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(param)) }
                path[i].callee.implicitParameters.computeIfAbsent(offset) { _ ->
                    DLParameter(
                        offset,
                        usage.containingFile.virtualFile.path,
                        null,
                        false,
                        DLChannelValType()
                    )
                }
            }
            path.last().callee.implicitParameters[offset] as DLParameter
        } else chanProd


        if (ElementFilters.isUsageValueArgument(usage)) {
            val psiCall = MyPsiUtils.findParent(usage, { it is KtCallExpression }, { it is KtFile }) as KtCallExpression
            val dlCall = usageFun.getCallFor(psiCall)
            val argIndex = MyPsiUtils.getArgumentIndex(usage)
            dlCall.args[argIndex] = DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(chanProducer))
        } else if (ElementFilters.isReturnUsage(usage)) {
            val psiReturn = MyPsiUtils.findParent(usage, { it is KtReturnExpression }, { it is KtFile }) as KtReturnExpression
            val dlReturn = usageFun.getReturnFor(psiReturn)
            dlReturn.returning = DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(chanProducer))
        } else if (ElementFilters.isSendUsage(usage)) {
            val dotQual = usage.parent as KtDotQualifiedExpression
            val callExpr = dotQual.selectorExpression as KtCallExpression
            val dlCall = usageFun.getCallFor(callExpr)
            if (dlCall !is ChannelSendDLAction) throw IllegalStateException("Expected channel send call")
            dlCall.args[-1] = DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(chanProducer))
        } else if (ElementFilters.isReceiveUsage(usage)) {
            val dotQual = usage.parent as KtDotQualifiedExpression
            val callExpr = dotQual.selectorExpression as KtCallExpression
            val dlCall = usageFun.getCallFor(callExpr)
            if (dlCall !is ChannelRecvDLAction) throw IllegalStateException("Expected channel receive call")
            dlCall.args[-1] = DLPassingArgument(DLValConsumer.createAndLinkChannelConsumer(chanProducer))
        }
    }
}