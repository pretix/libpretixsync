package eu.pretix.libpretixsync.db

import eu.pretix.libpretixsync.check.QuestionType
import org.junit.Assert.fail
import org.junit.Test
import eu.pretix.libpretixsync.models.Question as QuestionModel


class QuestionAnswerRequiredValidationTest {

    @Test
    @Throws(QuestionLike.ValidationException::class)
    fun testBoolean() {
        val q = QuestionModel(
            question = "Test",
            type = QuestionType.B,
            required = true,
            identifier = "ABTBAB8S",
            eventSlug = "test",
            id = 1L,
            serverId = 1L,
            position = 0L,
        )

        q.clean_answer("True", q.options, false)

        try {
            q.clean_answer("False", q.options, false)
            fail("Expected an ValidationException to be thrown")
        } catch (e: QuestionLike.ValidationException) {
        }

    }

    @Test
    fun testBooleanWithOptionalOverride() {
        val q = QuestionModel(
            question = "Test",
            type = QuestionType.B,
            required = true,
            identifier = "ABTBAB8S",
            eventSlug = "test",
            id = 1L,
            serverId = 1L,
            position = 0L,
        )

        try {
            q.clean_answer("False", q.options, true)
        } catch (e: QuestionLike.ValidationException) {
            fail("Expected that a ValidationException is not thrown")
        }
    }

    @Test
    @Throws(QuestionLike.ValidationException::class)
    fun testText() {
        val q = QuestionModel(
            question = "Test",
            type = QuestionType.T,
            required = true,
            identifier = "ABTBAB8S",
            eventSlug = "test",
            id = 1L,
            serverId = 1L,
            position = 0L,
        )

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
        val q = QuestionModel(
            question = "Test",
            type = QuestionType.T,
            required = true,
            identifier = "ABTBAB8S",
            eventSlug = "test",
            id = 1L,
            serverId = 1L,
            position = 0L,
        )

        try {
            q.clean_answer("", q.options, true)
        } catch (e: QuestionLike.ValidationException) {
            fail("Expected that a ValidationException is not thrown")
        }
    }
}