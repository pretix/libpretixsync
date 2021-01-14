package eu.pretix.libpretixsync.db


class Answer(var question: QuestionLike, var value: String, var options: List<QuestionOption>? = null) {
}

