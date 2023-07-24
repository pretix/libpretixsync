package eu.pretix.libpretixsync.db

import org.junit.Assert.fail
import org.junit.Test


class QuestionAnswerRequiredValidationTest {

    @Test
    @Throws(QuestionLike.ValidationException::class)
    fun testBoolean() {
        val q = Question()
        q.isRequired = true
        q.setJson_data("{\"id\":1,\"question\":{\"en\":\"Test\"},\"type\":\"B\",\"required\":true,\"items\":[1],\"options\":[],\"position\":0,\"ask_during_checkin\":false,\"identifier\":\"ABTBAB8S\",\"dependency_question\":null,\"dependency_value\":null}")
        q.clean_answer("True", q.options, false)

        try {
            q.clean_answer("False", q.options, false)
            fail("Expected an ValidationException to be thrown")
        } catch (e: QuestionLike.ValidationException) {
        }

    }

    @Test
    fun testBooleanWithOptionalOverride() {
        val q = Question()
        q.isRequired = true
        q.setJson_data("{\"id\":1,\"question\":{\"en\":\"Test\"},\"type\":\"B\",\"required\":true,\"items\":[1],\"options\":[],\"position\":0,\"ask_during_checkin\":false,\"identifier\":\"ABTBAB8S\",\"dependency_question\":null,\"dependency_value\":null}")
        try {
            q.clean_answer("False", q.options, true)
        } catch (e: QuestionLike.ValidationException) {
            fail("Expected that a ValidationException is not thrown")
        }
    }

    @Test
    @Throws(QuestionLike.ValidationException::class)
    fun testText() {
        val q = Question()
        q.setJson_data("{\"id\":1,\"question\":{\"en\":\"Test\"},\"type\":\"T\",\"required\":true,\"items\":[1],\"options\":[],\"position\":0,\"ask_during_checkin\":false,\"identifier\":\"ABTBAB8S\",\"dependency_question\":null,\"dependency_value\":null}")
        q.isRequired = true
        q.clean_answer("True", q.options, false)
        q.clean_answer("False", q.options, false)
        try {
            q.clean_answer("", q.options, false)
            fail("Expected an ValidationException to be thrown")
        } catch (e: QuestionLike.ValidationException) {
        }
    }

    @Test
    fun testTextWithOptionalOverride() {
        val q = Question()
        q.setJson_data("{\"id\":1,\"question\":{\"en\":\"Test\"},\"type\":\"T\",\"required\":true,\"items\":[1],\"options\":[],\"position\":0,\"ask_during_checkin\":false,\"identifier\":\"ABTBAB8S\",\"dependency_question\":null,\"dependency_value\":null}")
        q.isRequired = true
        try {
            q.clean_answer("", q.options, true)
        } catch (e: QuestionLike.ValidationException) {
            fail("Expected that a ValidationException is not thrown")
        }
    }
}