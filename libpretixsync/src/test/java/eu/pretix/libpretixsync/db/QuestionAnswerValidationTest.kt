package eu.pretix.libpretixsync.db

import eu.pretix.libpretixsync.check.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import eu.pretix.libpretixsync.models.Question as QuestionModel


@RunWith(Parameterized::class)
class QuestionAnswerValidationTest(private val questionType: QuestionType, private val input: String, private val expected: String?) {

    @Test
    fun test() {
        val q = QuestionModel(
            type = questionType,
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

        if (expected == null) {
            try {
                q.clean_answer(input, q.options, false)
                fail("Expected an ValidationException to be thrown")
            } catch (e: QuestionLike.ValidationException) {
            }

        } else {
            try {
                assertEquals(q.clean_answer(input, q.options, false), expected)
            } catch (e: QuestionLike.ValidationException) {
                fail(e.message)
            }

        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
                arrayOf(QuestionType.S, "a", "a"),
                arrayOf(QuestionType.T, "b", "b"),
                arrayOf(QuestionType.S, "", ""),
                arrayOf(QuestionType.N, "3", "3"),
                arrayOf(QuestionType.N, "2.56", "2.56"),
                arrayOf(QuestionType.N, "abc", null),
                arrayOf(QuestionType.B, "True", "True"),
                arrayOf(QuestionType.B, "true", "True"),
                arrayOf(QuestionType.B, "False", "False"),
                arrayOf(QuestionType.B, "false", "False"),
                arrayOf(QuestionType.B, "0", "False"),
                arrayOf(QuestionType.B, "", "False"),
                arrayOf(QuestionType.C, "3", "3"),
                arrayOf(QuestionType.C, "4", null),
                arrayOf(QuestionType.C, "A", null),
                arrayOf(QuestionType.M, "3", "3"),
                arrayOf(QuestionType.M, "12", "12"),
                arrayOf(QuestionType.M, "3,12", "3,12"),
                arrayOf(QuestionType.M, "3,12,6", null),
                arrayOf(QuestionType.M, "6", null),
                arrayOf(QuestionType.D, "2018-01-19", "2018-01-19"),
                arrayOf(QuestionType.D, "2016-02-29", "2016-02-29"),
                arrayOf(QuestionType.D, "2017-02-29", null),
                arrayOf(QuestionType.D, "fooobar", null),
                arrayOf(QuestionType.H, "12:20", "12:20"),
                arrayOf(QuestionType.H, "25:30", null),
                arrayOf(QuestionType.H, "Foo", null),
                arrayOf(QuestionType.W, "2018-01-19T12:20", "2018-01-19T12:20"),
                arrayOf(QuestionType.W, "2016-02-29T14:30", "2016-02-29T14:30"),
                arrayOf(QuestionType.W, "2016-02-29T25:59", null),
                arrayOf(QuestionType.W, "2017-02-01", null),
                arrayOf(QuestionType.W, "fooobar", null),
                arrayOf(QuestionType.EMAIL, "foobar@example.org", "foobar@example.org"),
                arrayOf(QuestionType.EMAIL, "foobar", null),
                arrayOf(QuestionType.EMAIL, "foobar@example.com.k", null)
        )
    }
}