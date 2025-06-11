package eu.pretix.libpretixsync.db

import eu.pretix.libpretixsync.models.QuestionLike

class Answer(var question: QuestionLike, var value: String, var options: List<QuestionOption>? = null) {
}

