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

        q.cleanAnswer("True", emptyList(), false)

        try {
            q.cleanAnswer("False", emptyList(), false)
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
            q.cleanAnswer("False", emptyList(), true)
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

        q.cleanAnswer("True", emptyList(), false)
        q.cleanAnswer("False", emptyList(), false)
        try {
            q.cleanAnswer("", emptyList(), false)
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
            q.cleanAnswer("", emptyList(), true)
        } catch (e: QuestionLike.ValidationException) {
            fail("Expected that a ValidationException is not thrown")
        }
    }

    @Test
    fun testChoiceLooksAtOptions() {
        val q = QuestionModel(
            type = QuestionType.C,
            required = true,
            question = "Test",
            identifier = "ABTBAB8S",
            askDuringCheckIn = false,
            options = listOf(
                QuestionOption(3L, 2, "AAA1", "A"),
                QuestionOption(12L, 1, "BBB2", "B"),
            ),
            id = 1L,
            serverId = 1L,
            eventSlug = "",
            position = 0L,
        )

        try {
            q.cleanAnswer("3", emptyList(), false)
            fail("Expected an ValidationException to be thrown")
        } catch (e: QuestionLike.ValidationException) {
        }
    }
}