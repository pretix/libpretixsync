/**
 * Based on https://github.com/advantagefse/json-logic-kotlin
 * Copyright (c) 2019 Advantage FSE
 * MIT License
 *
 * Changes:
 * - Ported from gson to jackson
 */
package eu.pretix.libpretixsync.utils.logic

import com.fasterxml.jackson.databind.ObjectMapper

internal val String?.parse: Any?
    get() = try {
        ObjectMapper().readValue(this, HashMap::class.java)
    } catch (e: Exception) {
        try {
            ObjectMapper().readValue(this, ArrayList::class.java)
        } catch (e: Exception) {
            try {
                ObjectMapper().readValue(this, Number::class.java)
            } catch (e: Exception) {
                try {
                    ObjectMapper().readValue(this, Boolean::class.java)
                } catch (e: Exception) {
                    try {
                        ObjectMapper().readValue(this, String::class.java)
                    } catch (e: Exception) {
                        this
                    }
                }
            }
        }
    }

val Any?.truthy: Boolean
    get() = when (this) {
        null -> false
        is Boolean -> this
        is Number -> toDouble() != 0.0
        is String -> !isEmpty() && this != "[]" && this != "false" && this != "null"
        is Collection<*> -> !isEmpty()
        is Array<*> -> size > 0
        else -> true
    }

internal val List<Any?>.flat: List<Any?>
    get() = mutableListOf<Any?>().apply {
        this@flat.forEach {
            when (it) {
                is List<*> -> addAll(it.flat)
                else -> add(it)
            }
        }
    }

internal val Any?.asList: List<Any?>?
    get() = when (this) {
        is String ->
            if (startsWith("["))
                replace("[", "").replace("]", "").split(",").map { it.trim() }
            else listOf(this)
        is List<*> -> this
        else -> listOf()
    }

internal val List<Any?>.comparableList: List<Comparable<*>?>
    get() = map { if (it is Comparable<*>) it else null }

internal val List<Any?>.doubleList: List<Double>
    get() = map {
        when (it) {
            is Number -> it.toDouble()
            is String -> it.doubleValue
            else -> 0.0
        }
    }

internal val String.intValue: Int
    get() = doubleValue.toInt()

internal val String.doubleValue: Double
    get() = try {
        toDouble()
    } catch (e: NumberFormatException) {
        0.0
    }

internal fun getRecursive(indexes: List<String>, data: List<Any?>): Any? = indexes.getOrNull(0)?.apply {
    val d = data.getOrNull(intValue) as? List<Any?>
    return if (d is List<*>) getRecursive(indexes.subList(1, indexes.size), d) else data.getOrNull(intValue)
}
