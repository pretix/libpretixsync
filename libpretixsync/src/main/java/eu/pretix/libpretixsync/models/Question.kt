package eu.pretix.libpretixsync.models

import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.db.QuestionLike
import eu.pretix.libpretixsync.db.QuestionOption

class Question(
    val id: Long,
    val serverId: Long,
    val eventSlug: String?,
    val position: Long,
    val required: Boolean,
    val askDuringCheckIn: Boolean,
    val showDuringCheckIn: Boolean,
    val dependencyQuestionServerId: Long?,
    type: QuestionType,
    question: String,
    identifier: String,
    options: List<QuestionOption>?,
    dependencyValues: List<String>,
) : QuestionLike() {

    private val _type: QuestionType = type
    private val _question: String = question
    private val _identifier: String = identifier
    private val _options: List<QuestionOption>? = options
    private val _dependencyValues: List<String> = dependencyValues

    private var resolveDependencyCalled = false
    private var resolvedDependency: Question? = null

    override fun getType(): QuestionType = _type

    override fun getQuestion(): String = _question

    override fun getIdentifier(): String = _identifier

    override fun getOptions(): List<QuestionOption>? = _options

    override fun requiresAnswer(): Boolean = required

    override fun getDependency(): QuestionLike? {
        if (!resolveDependencyCalled) {
            throw IllegalStateException("Question dependencies not resolved")
        }
        return resolvedDependency
    }

    fun resolveDependency(all: List<Question>) {
        resolveDependencyCalled = true
        if (dependencyQuestionServerId == null) {
            resolvedDependency = null
            return
        }
        for (q in all) {
            if (q.serverId == dependencyQuestionServerId) {
                resolvedDependency = q
                break
            }
        }
    }

    override fun getDependencyValues(): List<String> = _dependencyValues
}
