package eu.pretix.libpretixsync.utils

import com.fasterxml.jackson.databind.ObjectMapper
import eu.pretix.libpretixsync.test.readResource
import eu.pretix.libpretixsync.utils.logic.*
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Based on https://github.com/advantagefse/json-logic-kotlin
 * Copyright (c) 2019 Advantage FSE
 * MIT License
 *
 * Changes:
 * - Backport to JUnit4
 * - Bundle official tests
 */

class JsonLogicTests {

    fun clean(i: String): String {
        return "([0-9])\\.0(,|]|$)".toRegex().replace(i, "$1$2")
    }

    @Test
    fun officialTests() {
        val tests = ObjectMapper().readValue(readResource("jsonlogic/tests.json"), List::class.java)
        val l = JsonLogic()
        for (t in tests) {
            when (t) {
                is List<*> -> {
                    System.out.println(t.toString() + " - " + ObjectMapper().writeValueAsString(t[0]))
                    val result = l.apply(
                            ObjectMapper().writeValueAsString(t[0]),
                            ObjectMapper().writeValueAsString(t[1])
                    )
                    var expected = t[2]
                    if (result is Double && expected is Int) {
                        expected = expected.toString().toDouble()
                    }
                    if (result is List<*> && result.firstOrNull() is Double && expected is List<*> && expected.firstOrNull() is Int) {
                        expected = expected.map { it.toString().toDouble() }
                    }
                    if (result is List<*> && result.isEmpty() && expected is List<*> && expected.isEmpty()) {
                        expected = result
                    }
                    assertEquals(
                            expected,
                            result
                    )
                }
                is String -> {
                }
                else -> {
                    assert(false)
                }
            }
        }
    }

    @Test
    fun simple() {
        val jsonLogic = JsonLogic()
        val result = jsonLogic.apply("{\"==\":[1,1]}")
        assertEquals(true, result)
    }

    @Test
    fun compoundString() {
        val jsonLogic = JsonLogic()
        val result = jsonLogic.apply(
                "{\"and\" : [" +
                        "    { \">\" : [3,1] }," +
                        "    { \"<\" : [1,3] }" +
                        "] }"
        )
        assertEquals(true, result)
    }

    @Test
    fun compound() {
        val jsonLogic = JsonLogic()
        val logic = mapOf(
                "and" to listOf(
                        mapOf(">" to listOf(3, 1)),
                        mapOf("<" to listOf(1, 3))
                )
        )
        val result = jsonLogic.apply(logic)
        assertEquals(true, result)
    }

    @Test
    fun dataString() {
        val jsonLogic = JsonLogic()
        val result = jsonLogic.apply(
                "{ \"var\" : [\"a\"] }", // Rule
                "{ \"a\" : 1.2, \"b\" : 2 }" // Data
        )
        assertEquals(1.2, result)
    }

    @Test
    fun data() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("var" to listOf("a"))
        val data = mapOf("a" to 1, "b" to 2)
        val result = jsonLogic.apply(logic, data)
        assertEquals(1, result)
    }

    @Test
    fun array() {
        val jsonLogic = JsonLogic()
        val result = jsonLogic.apply(
                "{\"var\" : 1 }", // Rule
                "[ \"apple\", \"banana\", \"carrot\" ]" // Data
        )
        assertEquals("banana", result)
    }

    @Test
    fun customOperation1() {
        fun plus(list: List<Any?>?, @Suppress("UNUSED_PARAMETER") data: Any?): Any? {
            try {
                if (list != null && list.size > 1) return list[0].toString().toDouble() + list[1].toString().toDouble()
            } catch (e: Exception) {
            }
            return null
        }

        val jsonLogic = JsonLogic()
        jsonLogic.addOperation("plus", ::plus)
        val result = jsonLogic.apply("{\"plus\":[23, 19]}", null)
        assertEquals(42.0, result)
    }

    @Test
    fun customOperation2() {
        val jsonLogic = JsonLogic()
        jsonLogic.addOperation("sqrt") { l, _ ->
            try {
                if (l != null && l.size > 0) Math.sqrt(l[0].toString().toDouble())
                else null
            } catch (e: Exception) {
                null
            }
        }
        val result = jsonLogic.apply("{\"sqrt\":\"9\"}")
        assertEquals(3.0, result)
    }

    @Test
    fun customOperation3() {
        val jsonLogic = JsonLogic()
        jsonLogic.addOperation("Math.random") { _, _ -> Math.random() }
        val result = jsonLogic.apply("{\"Math.random\":[]}", "{}")
        assert(result as Double in 0.0..1.0)
    }

    @Test
    fun customOperation4() {
        val jsonLogic = JsonLogic()
        jsonLogic.addOperation("pow") { l, _ ->
            try {
                if (l != null && l.size > 1) Math.pow(l[0].toString().toDouble(), l[1].toString().toDouble())
                else null
            } catch (e: Exception) {
                null
            }
        }
        val result = jsonLogic.apply(mapOf("pow" to listOf(3, 2)))
        assertEquals(9.0, result)
    }

    @Test(expected = NotImplementedError::class)
    fun unknownCustomOperation() {
        val jsonLogic = JsonLogic()
        jsonLogic.apply(mapOf("hello" to listOf(1, 2, 3)), safe = false)
    }

    @Test
    fun unknownCustomOperationSafe() {
        val jsonLogic = JsonLogic()
        val result = jsonLogic.apply(mapOf("hello" to listOf(1, 2, 3)), safe = true)
        assertEquals(false, result)
    }

    @Test
    fun stringComparisonBug() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("===" to listOf(mapOf("var" to listOf("a")), "two"))
        val data = mapOf("a" to "one")
        val result = jsonLogic.apply(logic, data)
        assertEquals(false, result)
    }

    @Test
    fun nullTest() {
        val jsonLogic = JsonLogic()
        val result = jsonLogic.apply(null)
        assertEquals(null, result)
    }

    @Test
    fun log() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("log" to "apple")
        val result = jsonLogic.apply(logic)
        assertEquals("apple", result)
    }

    @Test
    fun truthyNull() {
        assertEquals(false, null.truthy)
    }

    @Test
    fun truthyString() {
        assertEquals(true, "hello".truthy)
    }

    @Test
    fun truthyArray() {
        assertEquals(true, arrayOf(1).truthy)
    }

    @Test
    fun truthyEmptyArray() {
        assertEquals(false, emptyArray<Int>().truthy)
    }

    @Test
    fun truthyOther() {
        class Other()
        assertEquals(true, Other().truthy)
    }

    @Test
    fun doubleListOther() {
        class Other()

        val jsonLogic = JsonLogic()
        val logic = mapOf("+" to listOf(1, 2, Other()))
        val result = jsonLogic.apply(logic)
        assertEquals(3.0, result)
    }

    @Test
    fun doubleValueException() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("+" to listOf(1, 2, "hello"))
        val result = jsonLogic.apply(logic)
        assertEquals(3.0, result)
    }

    @Test
    fun inOther() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("in" to 1)
        val result = jsonLogic.apply(logic)
        assertEquals(false, result)
    }

    @Test
    fun minusMore() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("-" to listOf(2, 1, 1))
        val result = jsonLogic.apply(logic)
        assertEquals(1.0, result)
    }

    @Test
    fun minusNone() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("-" to listOf<Int>())
        val result = jsonLogic.apply(logic)
        assertEquals(null, result)
    }

    @Test
    fun compareOther() {
        class Other()

        val jsonLogic = JsonLogic()
        val logic = mapOf("==" to listOf(1, Other()))
        val result = jsonLogic.apply(logic)
        assertEquals(false, result)
    }

    @Test
    fun compareAnyComparables() {
        class Other() : Comparable<Other> {
            override fun compareTo(other: Other) = 0
        }

        val jsonLogic = JsonLogic()
        val logic = mapOf("==" to listOf(Other(), Other()))
        val result = jsonLogic.apply(logic)
        assertEquals(true, result)
    }

    @Test
    fun compareWithOtherComparable() {
        class Other() : Comparable<Other> {
            override fun compareTo(other: Other) = 0
        }

        val jsonLogic = JsonLogic()
        val logic = mapOf("==" to listOf(1, Other()))
        val result = jsonLogic.apply(logic)
        assertEquals(false, result)
    }

    @Test
    fun compareNullWithOtherComparable() {
        class Other() : Comparable<Other> {
            override fun compareTo(other: Other) = 0
        }

        val jsonLogic = JsonLogic()
        val logic = mapOf("==" to listOf(null, Other()))
        val result = jsonLogic.apply(logic)
        assertEquals(false, result)
    }

    @Test
    fun compareNulls() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("==" to listOf(null, null))
        val result = jsonLogic.apply(logic)
        assertEquals(true, result)
    }

    @Test
    fun compareListOfMore() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("<" to listOf(1, 2, 3, 4))
        val result = jsonLogic.apply(logic)
        assertEquals(false, result)
    }

    @Test
    fun substrMoreParams() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("substr" to listOf("jsonlogic", 4, 5, 6))
        val result = jsonLogic.apply(logic)
        assertEquals(null, result)
    }

    @Test
    fun substrExtreme() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("substr" to listOf("jsonlogic", 0, 0))
        val result = jsonLogic.apply(logic)
        assertEquals(null, result)
    }

    @Test
    fun invalidList() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("none" to 1)
        val result = jsonLogic.apply(logic)
        assertEquals(true, result)
    }

    @Test
    fun getInvalidVar() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("var" to "a.")
        val data = mapOf("a" to mapOf("b" to 1))
        val result = jsonLogic.apply(logic, data)
        assertEquals(null, result)
    }

    @Test
    fun compareBooleanWithString() {
        val jsonLogic = JsonLogic()
        val logic = mapOf("==" to listOf("true", true))
        val result = jsonLogic.apply(logic)
        assertEquals(true, result)
    }
}