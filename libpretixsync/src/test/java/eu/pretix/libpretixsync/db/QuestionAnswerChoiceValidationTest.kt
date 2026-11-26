package eu.pretix.libpretixsync.db

import eu.pretix.libpretixsync.check.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import eu.pretix.libpretixsync.models.Question as QuestionModel


class QuestionAnswerChoiceValidationTest {

    @Test
    fun testSingleChoice() {
        val q = QuestionModel(
            type = QuestionType.C,
            required = false,
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
            assertEquals(q.cleanAnswer("nonempty", listOf(q.options!![0]), false), "3")
        } catch (e: QuestionLike.ValidationException) {
            fail(e.message)
        }
    }

    @Test
    fun testMultipleChoice() {
        val q = QuestionModel(
            type = QuestionType.M,
            required = false,
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
            assertEquals(q.cleanAnswer("ignored", q.options!!, false), "12,3")
        } catch (e: QuestionLike.ValidationException) {
            fail(e.message)
        }
    }
}