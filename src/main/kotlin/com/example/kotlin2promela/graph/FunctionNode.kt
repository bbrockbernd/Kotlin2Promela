package com.example.kotlin2promela.graph

import com.example.kotlin2promela.ElementFilters
import com.example.kotlin2promela.MyPsiUtils
import com.example.kotlin2promela.graph.action.*
import com.example.kotlin2promela.graph.variablePassing.DLChannelParameter
import com.example.kotlin2promela.graph.variablePassing.DLPropParam
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLChannelValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLUnitValType
import com.example.kotlin2promela.graph.variablePassing.variableTypes.DLValType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.util.isAnonymousFunction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer


class FunctionNode(
    val info: FunctionInfo,
    val parameterList: List<DLPropParam>,
    var returnType: DLValType,
    val psiPointer: SmartPsiElementPointer<KtNamedDeclaration>,
    val isConstructor: Boolean = false,
): Prom {
    var actionList = mutableListOf<DLAction>()
    var visited = false
    val implicitParameters = mutableMapOf<Int, DLPropParam>()
    val calledBy = mutableListOf<CallWithCalleeFunDLAction>()

    constructor(function: KtFunction) : this(
        FunctionInfo(generateId(function), extractFqName(function), "file", 0),
        extractParameters(function),
        extractReturnType(function),
        function.createSmartPointer()
    ) 
    
    constructor(clazz: KtClass) : this(
        FunctionInfo(generateId(clazz), clazz.name!!, "file", 0),
        extractParameters(clazz),
        DLUnitValType(), // return type will be added in linking phase for constructors
        clazz.createSmartPointer()
    )
    
    
    fun getParentFor(dlAction: DLAction): DLAction? = getActions<DLAction>().firstOrNull { it.hasChild(dlAction) }
    fun getChildNodes(): List<FunctionNode> = getCallsToChildNodes().map { it.callee }
    
    inline fun <reified T: DLAction> getActions(): List<T> = actionList
        .flatMap { it.getDescendants() + it }
        .filterIsInstance<T>()
    
    fun getCallsToChildNodes() = getActions<CallWithCalleeFunDLAction>()
    fun getChanInits() = getActions<ChannelInitDLAction>() 
    fun getReturns() = getActions<DLReturnAction>()
    fun getCallsWithArguments() = getActions<DLCallWithArguments>()

    override fun equals(other: Any?): Boolean {
        if (other !is FunctionNode) return false
        return info.id == other.info.id
    }
    
    fun getCallFor(element: KtCallExpression): DLCallWithArguments {
        return getCallsWithArguments().first { it.offset == element.textOffset }
    }
    
    fun getReturnFor(element: KtReturnExpression): DLReturnAction {
        return getReturns().first { it.offset == element.textOffset }
    }

    override fun hashCode(): Int {
        return info.id.hashCode()
    }

    companion object {
        fun generateId(function: KtFunction): String {
            if (function is KtConstructor<*>) {
                val cls = function.getContainingClassOrObject()
                if (cls !is KtClass) throw IllegalStateException("Constructor of object..? for file ${function.getContainingFile().name}")
                return generateId(cls)
            }
            return "fun_${extractFunName(function)}_${MyPsiUtils.getId(function)!!}" 
        }

        fun generateId(clazz: KtClass): String {
            val clazzName = clazz.name!!
            return "prim_constr_${clazzName}_${MyPsiUtils.getId(clazz)!!}"
        }
        fun extractFqName(function: KtFunction): String = function.fqName?.toString() ?: "lambda" 
        fun extractFunName(function: KtFunction): String {
            if(function.isAnonymousFunction || function.name == "<anonymous>" || function.name == null) return "lambda"
            return function.name!!
        }
        
        fun extractParameters(clazz: KtClass): List<DLPropParam> {
            if (clazz.primaryConstructor == null) return emptyList()
            return extractParameters(clazz.primaryConstructor!!)
        }
        
        fun extractParameters(function: KtFunction): List<DLPropParam> {
            return function.valueParameters
                .filter { ElementFilters.isChannelParameter(it)}
                .map { DLChannelParameter(it.textOffset, it.containingFile.virtualFile.path, it.createSmartPointer(), it.hasValOrVar())}
        }
        
        fun extractReturnType(function: KtFunction): DLValType {
            return if (function.typeReference != null && ElementFilters.isChannelReturnType(function.typeReference!!)) {
                DLChannelValType()
            } else {
                DLUnitValType()
            }
        }
    }

    
    
    /**
     * Will result in:
     * ```
     * proctype funID(chan implParam1, mutex implParam2, chan realParam1, chan ret) {
     *     [actions]
     *     ret!0
     * }
     * ```
     */
    override fun toProm(indent: Int): String = buildString { 
        // Function comment and signature
        appendLine("/* function: ${info.fqName} */")
        appendLine("proctype ${promRefName()}(${paramListProm()}) {")
        
        // Return channels
        val returnChannelActions = getCallsToChildNodes()
        if (returnChannelActions.isNotEmpty()) appendLineIndented(1, "/* Function call return channels */")
        returnChannelActions
            .forEach { appendLineIndented(1, "chan child_${it.offset} = [0] of {int}") }
        if (returnChannelActions.isNotEmpty()) appendLine()

        //Sync and async calls
        actionList
//            .filter { it is CallWithCalleeFunDLAction || it is ChannelRecvDLAction || it is ChannelSendDLAction }
            .forEach { action -> 
            append(action.toProm(1))
        }
        
        if (getReturns().isEmpty()) appendLineIndented(1, "ret!0")
        appendLineIndented(1, "end: skip")
        appendLine("}")
    }
    
    fun promRefName() = info.id
    
    
    private fun paramListProm(): String = buildString {
        val myPromParams = implicitParameters.toSortedMap().values + parameterList
        myPromParams.forEach { param ->
            append(param.toProm(), "; ")
        }
        append("chan ret")
    }
    
    data class FunctionInfo(val id: String, val fqName: String, val file: String, val offset: Int)
}