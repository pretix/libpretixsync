package eu.pretix.libpretixsync.db

import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.utils.EmailValidator
import java.math.BigDecimal
import java.text.ParseException
import java.text.SimpleDateFormat

abstract class QuestionLike {
    class ValidationException(msg: String?) : Exception(msg)

    abstract val type: QuestionType

    abstract val question: String

    abstract val identifier: String

    abstract val options: List<QuestionOption>?

    abstract fun requiresAnswer(): Boolean

    open val default: String?
        get() = null

    open val valid_date_min: Long?
        get() = null

    open val valid_date_max: Long?
        get() = null

    open val valid_datetime_min: Long?
        get() = null

    open val valid_datetime_max: Long?
        get() = null

    open val dependency: QuestionLike?
        get() = null

    open val dependencyValues: List<String>?
        get() = ArrayList()

    @Throws(ValidationException::class)
    open fun warn_answer(
        answer: String?,
        opts: List<QuestionOption>?,
        allAnswersAreOptional: Boolean
    ) {
    }

    @Throws(ValidationException::class)
    fun clean_answer(
        answer: String?,
        opts: List<QuestionOption>?,
        allAnswersAreOptional: Boolean
    ): String {
        val type = type
        if (!allAnswersAreOptional && requiresAnswer()) {
            if (type == QuestionType.B) {
                if (answer != "True" && answer != "true") {
                    throw ValidationException("Question is required")
                }
            } else if (answer == null || answer.trim { it <= ' ' } == "") {
                throw ValidationException("Question is required")
            }
        } else if ((answer == null || answer.trim { it <= ' ' } == "") && type != QuestionType.B) {
            return ""
        }
        if (answer!!.startsWith("file:///") && type != QuestionType.F) {
            // sorry!
            throw ValidationException("Question is not a file field")
        }

        if (type == QuestionType.N) {
            try {
                return BigDecimal(answer).toPlainString()
            } catch (e: NumberFormatException) {
                throw ValidationException("Invalid number supplied")
            }
        } else if (type == QuestionType.F) {
            if (!answer.startsWith("file:///") && !answer.startsWith("http")) {
                throw ValidationException("Invalid file path supplied")
            }
        } else if (type == QuestionType.EMAIL) {
            if (!(EmailValidator()).isValidEmail(answer)) {
                throw ValidationException("Invalid email address supplied")
            }
        } else if (type == QuestionType.B) {
            return if (answer == "True" || answer == "true") "True" else "False"
        } else if (type == QuestionType.C) {
            for (o in opts ?: emptyList()) {
                if (o.server_id.toString() == answer) {
                    return answer
                }
            }
            throw ValidationException("Invalid choice supplied")
        } else if (type == QuestionType.M) {
            val validChoices: MutableSet<String> = HashSet()
            for (o in opts ?: emptyList()) {
                validChoices.add(o.server_id.toString())
            }
            for (a in answer.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                if (!validChoices.contains(a)) {
                    throw ValidationException("Invalid choice supplied")
                }
            }
        } else if (type == QuestionType.D) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd")
            dateFormat.isLenient = false
            try {
                dateFormat.parse(answer)
            } catch (e: ParseException) {
                throw ValidationException("Invalid date supplied")
            }
        } else if (type == QuestionType.H) {
            val dateFormat = SimpleDateFormat("HH:mm")
            dateFormat.isLenient = false
            try {
                dateFormat.parse(answer)
            } catch (e: ParseException) {
                throw ValidationException("Invalid time supplied")
            }
        } else if (type == QuestionType.W) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
            dateFormat.isLenient = false
            try {
                dateFormat.parse(answer)
            } catch (e: ParseException) {
                throw ValidationException("Invalid datetime supplied")
            }
        }
        return answer
    }
}
