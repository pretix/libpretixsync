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

        q.cleanAnswer("True", q.options, false)

        try {
            q.cleanAnswer("False", q.options, false)
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
            q.cleanAnswer("False", q.options, true)
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

        q.cleanAnswer("True", q.options, false)
        q.cleanAnswer("False", q.options, false)
        try {
            q.cleanAnswer("", q.options, false)
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
            q.cleanAnswer("", q.options, true)
        } catch (e: QuestionLike.ValidationException) {
            fail("Expected that a ValidationException is not thrown")
        }
    }
}