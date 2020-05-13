/**
 * Based on https://github.com/advantagefse/json-logic-kotlin
 * Copyright (c) 2019 Advantage FSE
 * MIT License
 *
 * Changes:
 * - Test against current official tests
 * - Get rid of all the toString/fromString conversions while evaluating
 * - Do not return strings but native types
 * - Depth-first recursion for custom operators (like JS and Python implementations)
 */
package eu.pretix.libpretixsync.utils.logic

/**
 * Kotlin native implementation of http://jsonlogic.com/
 */
class JsonLogic {

    /**
     * Apply logic on data and get a result for all supported operations http://jsonlogic.com/operations.html
     *
     * @param logic the logic as a json encoded string
     * @param data the data as a json encoded string
     * @param safe if true an exception is returned as false else exceptions are thrown
     * @return evaluation result
     */
    @JvmOverloads
    fun apply(logic: String?, data: String? = null, safe: Boolean = true) =
            evaluateSafe(logic.parse, data.parse, safe)

    /**
     * Apply logic on data and get a result
     *
     * @param logic the logic
     * @param data the data
     * @param safe if true an exception is returned as false else exceptions are thrown
     * @return evaluation result
     */
    @JvmOverloads
    fun apply(logic: Any?, data: Any? = null, safe: Boolean = true) = evaluateSafe(logic, data, safe)

    /**
     * Apply logic on data and get a result
     *
     * @param logic the logic
     * @param data the data
     * @param safe if true an exception is returned as false else exceptions are thrown
     * @return evaluation result
     */
    fun applyString(logic: String?, data: Any? = null, safe: Boolean = true) = evaluateSafe(logic.parse, data, safe)

    /**
     * Add new operations http://jsonlogic.com/add_operation.html
     *
     * @param operator the operator to be added
     * @param lambda the operation tha handles the operator
     */
    fun addOperation(operator: String, lambda: (List<Any?>?, Any?) -> Any?) = customOperations.put(operator, lambda)

    private fun evaluateSafe(logic: Any?, data: Any? = null, safe: Boolean = true) = if (safe) {
        try {
            evaluate(logic, data)
        } catch (e: kotlin.NotImplementedError) {
            false
        } catch (e: java.lang.Exception) {
            false
        }
    } else evaluate(logic, data)

    private fun evaluate(logic: Any?, data: Any? = null): Any? {
        if (logic !is Map<*, *>) return logic
        if (logic.isNullOrEmpty()) return data
        val operator = logic.keys.firstOrNull()
        val values = logic[operator]
        return if (customOperations.keys.contains(operator))
            customOperations[operator]?.invoke(when (values) {
                is List<*> -> values.map { evaluate(it, data) }
                is Map<*, *> -> evaluate(values, data)
                else -> evaluate(listOf(values), data)
            }.asList, data)
        else if (specialArrayOperations.keys.contains(operator))
            specialArrayOperations[operator]?.invoke(values.asList, data)
        else (operations[operator] ?: TODO("operator \"$operator\"")).invoke(when (values) {
            is List<*> -> values.map { evaluate(it, data) }
            is Map<*, *> -> evaluate(values, data)
            else -> evaluate(listOf(values), data)
        }.asList, data)
    }

    private val customOperations = mutableMapOf<String, (List<Any?>?, Any?) -> Any?>()

    private val operations = mapOf<String, (List<Any?>?, Any?) -> Any?>(
            "var" to { l, d -> getVar(d, l) },
            "missing" to { l, d -> missing(d, l) },
            "missing_some" to { l, d -> missingSome(d, l) },
            "==" to { l, _ -> with(l?.comparableList) { compare(this?.getOrNull(0), this?.getOrNull(1)) == 0 } },
            "===" to { l, _ -> with(l?.comparableList) { compareStrict(this?.getOrNull(0), this?.getOrNull(1)) == 0 } },
            "!=" to { l, _ -> with(l?.comparableList) { compare(this?.getOrNull(0), this?.getOrNull(1)) != 0 } },
            "!==" to { l, _ -> with(l?.comparableList) { compareStrict(this?.getOrNull(0), this?.getOrNull(1)) != 0 } },
            ">" to { l, _ -> l.compareListOfThree { a, b -> a > b } },
            ">=" to { l, _ -> l.compareListOfThree { a, b -> a >= b } },
            "<" to { l, _ -> l.compareListOfThree { a, b -> a < b } },
            "<=" to { l, _ -> l.compareListOfThree { a, b -> a <= b } },
            "!" to { l, _ -> !l?.getOrNull(0).truthy },
            "!!" to { l, _ -> l?.getOrNull(0).truthy },
            "%" to { l, _ ->
                with(l?.doubleList ?: listOf()) {
                    if (size > 1) this[0] % this[1] else null
                }
            },
            "and" to { l, _ ->
                if (l?.all { it is Boolean } == true) l.all { it.truthy }
                else (l?.firstOrNull { !it.truthy } ?: l?.last())
            },
            "or" to { l, _ ->
                if (l?.all { it is Boolean } == true) l.firstOrNull { it.truthy } != null
                else (l?.firstOrNull { it.truthy } ?: l?.last())
            },
            "?:" to { l, _ -> l?.recursiveIf },
            "if" to { l, _ -> l?.recursiveIf },
            "log" to { l, _ -> l?.getOrNull(0) },
            "in" to { l, _ ->
                val first = l?.getOrNull(0)
                val second = l?.getOrNull(1)
                when (second) {
                    is String -> second.contains(first.toString())
                    is List<*> -> second.contains(first)
                    else -> false
                }
            },
            "cat" to { l, _ ->
                l?.map { if (it is Number && it.toDouble() == it.toInt().toDouble()) it.toInt() else it }
                        ?.joinToString("")
            },
            "+" to { l, _ -> l?.doubleList?.sum() },
            "*" to { l, _ -> l?.doubleList?.reduce { sum, cur -> sum * cur } },
            "-" to { l, _ ->
                with(l?.doubleList ?: listOf()) {
                    when (size) {
                        0 -> null
                        1 -> -this[0]
                        else -> this[0] - this[1]
                    }
                }
            },
            "/" to { l, _ -> with(l?.doubleList ?: listOf()) { this[0] / this[1] } },
            "min" to { l, _ -> l?.filter { it is Number }?.minBy { (it as Number).toDouble() } },
            "max" to { l, _ -> l?.filter { it is Number }?.maxBy { (it as Number).toDouble() } },
            "merge" to { l, _ -> l?.flat },
            "substr" to { l, _ ->
                val str = l?.getOrNull(0).toString()
                val a = l?.getOrNull(1).toString().intValue
                val b = if (l?.size ?: 0 > 2) l?.getOrNull(2).toString().intValue else 0
                when (l?.size) {
                    2 -> if (a > 0) str.substring(a) else str.substring(str.length + a)
                    3 -> when {
                        a >= 0 && b > 0 -> str.substring(a, a + b)
                        a >= 0 && b < 0 -> str.substring(a, str.length + b)
                        a < 0 && b < 0 -> str.substring(str.length + a, str.length + b)
                        a < 0 -> str.substring(str.length + a)
                        else -> null
                    }
                    else -> null
                }
            }
    )

    private val specialArrayOperations = mapOf<String, (List<Any?>?, Any?) -> Any?>(
            "map" to { l, d ->
                if (d == null) listOf<Any>()
                else {
                    val data = evaluate(l?.getOrNull(0), d) as? List<*>
                    (data?.map { evaluate(l?.getOrNull(1), it) } ?: listOf<Any>())
                }
            },
            "filter" to { l, d ->
                if (d == null) listOf<Any>()
                else {
                    val data = evaluate(l?.getOrNull(0), d) as? List<*>
                    (data?.filter { evaluate(l?.getOrNull(1), it).truthy }
                            ?: listOf<Any>())
                }
            },
            "all" to { l, d ->
                if (d == null) false
                else {
                    val data = evaluate(l?.getOrNull(0), d) as? List<*>
                    if (data.isNullOrEmpty()) {
                        false
                    } else {
                        data.all { evaluate(l?.getOrNull(1), it).truthy }
                    }
                }
            },
            "none" to { l, d ->
                if (d == null) true
                else {
                    val data = evaluate(l?.getOrNull(0), d) as? List<*>
                    (data?.none { evaluate(l?.getOrNull(1), it).truthy }
                            ?: true)
                }
            },
            "some" to { l, d ->
                if (d == null) listOf<Any>()
                else {
                    val data = evaluate(l?.getOrNull(0), d) as? List<*>
                    (data?.any { evaluate(l?.getOrNull(1), it).truthy }
                            ?: false)
                }
            },
            "reduce" to { l, d ->
                if (d == null) 0.0
                else {
                    val data = evaluate(l?.getOrNull(0), d) as? List<Any?>
                    val logic = l?.getOrNull(1)
                    val initial: Double = if (l != null && l.size > 2) l.getOrNull(2).toString().doubleValue else 0.0
                    data?.fold(initial) { sum, cur ->
                        evaluate(logic, mapOf("current" to cur, "accumulator" to sum)).toString().doubleValue
                    }
                }
            }
    )

    private fun getVar(data: Any?, values: Any?): Any? {
        var value: Any? = data
        val varName = if (values is List<*>) values.getOrNull(0).toString() else values.toString()
        when (value) {
            is List<*> -> {
                val indexParts = varName.split(".")
                value = if (indexParts.size == 1) value[indexParts[0].intValue] else getRecursive(indexParts, value)
            }
            is Map<*, *> -> varName.split(".").forEach {
                value = (value as? Map<*, *>)?.get(it)
            }
        }
        if ((value == data || value == null) && values is List<*> && values.size > 1) {
            return values.getOrNull(1)
        }
        return value
    }

    private val List<Any?>.recursiveIf: Any?
        get() = when (size) {
            0 -> null
            1 -> getOrNull(0)
            2 -> if (getOrNull(0).truthy) getOrNull(1) else null
            3 -> if (getOrNull(0).truthy) getOrNull(1) else getOrNull(2)
            else -> if (getOrNull(0).truthy) getOrNull(1) else subList(2, size).recursiveIf
        }

    private fun missing(data: Any?, vars: List<Any?>?) = arrayListOf<Any?>().apply {
        vars?.forEach { if (getVar(data, it) == null) add(it) }
    }

    private fun missingSome(data: Any?, vars: List<Any?>?): List<Any?> {
        val min = vars?.getOrNull(0)?.toString()?.intValue ?: 0
        val keys = vars?.getOrNull(1) as? List<Any?> ?: listOf()
        val missing = missing(data, keys)
        return if (keys.size - missing.size >= min) listOf() else missing
    }

    private fun compare(a: Comparable<*>?, b: Comparable<*>?) = when {
        a is Number && b is Number -> compareValues(a.toDouble(), b.toDouble())
        a is String && b is Number -> compareValues(a.doubleValue, b.toDouble())
        a is Number && b is String -> compareValues(a.toDouble(), b.doubleValue)
        a is String && b is String -> compareValues(a, b)
        a is Boolean || b is Boolean -> compareValues(a.truthy, b.truthy)
        else -> compareValues(a, b)
    }

    private fun compareStrict(a: Comparable<*>?, b: Comparable<*>?) = when {
        a is Number && b is Number -> compareValues(a.toDouble(), b.toDouble())
        a is String && b is String -> compareValues(a, b)
        else -> -1
    }

    private fun List<Any?>?.compareListOfThree(operator: (Int, Int) -> Boolean) = with(this?.comparableList) {
        when {
            this?.size == 2 -> operator(compare(this.getOrNull(0), this.getOrNull(1)), 0)
            this?.size == 3 -> operator(compare(this.getOrNull(0), this.getOrNull(1)), 0)
                    && operator(compare(this.getOrNull(1), this.getOrNull(2)), 0)
            else -> false
        }
    }
}