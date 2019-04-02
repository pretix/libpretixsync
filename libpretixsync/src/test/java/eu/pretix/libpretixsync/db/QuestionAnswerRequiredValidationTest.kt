package eu.pretix.libpretixsync.db

import org.junit.Assert.fail
import org.junit.Test


class QuestionAnswerRequiredValidationTest {

    @Test
    @Throws(AbstractQuestion.ValidationException::class)
    fun testBoolean() {
        val q = Question()
        q.isRequired = true
        q.setJson_data("{\"id\":1,\"question\":{\"en\":\"Test\"},\"type\":\"B\",\"required\":true,\"items\":[1],\"options\":[],\"position\":0,\"ask_during_checkin\":false,\"identifier\":\"ABTBAB8S\",\"dependency_question\":null,\"dependency_value\":null}")
        q.clean_answer("True", q.options)

        try {
            q.clean_answer("False", q.options)
            fail("Expected an ValidationException to be thrown")
        } catch (e: AbstractQuestion.ValidationException) {
        }

    }

    @Test
    @Throws(AbstractQuestion.ValidationException::class)
    fun testText() {
        val q = Question()
        q.setJson_data("{\"id\":1,\"question\":{\"en\":\"Test\"},\"type\":\"T\",\"required\":true,\"items\":[1],\"options\":[],\"position\":0,\"ask_during_checkin\":false,\"identifier\":\"ABTBAB8S\",\"dependency_question\":null,\"dependency_value\":null}")
        q.isRequired = true
        q.clean_answer("True", q.options)
        q.clean_answer("False", q.options)
        try {
            q.clean_answer("", q.options)
            fail("Expected an ValidationException to be thrown")
        } catch (e: AbstractQuestion.ValidationException) {
        }

    }
}