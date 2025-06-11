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
    override val question: String,
    override val identifier: String,
    val askDuringCheckIn: Boolean = false,
    val showDuringCheckIn: Boolean = false,
    val dependencyQuestionServerId: Long? = null,
    override val type: QuestionType = QuestionType.T,
    override val options: List<QuestionOption>? = null,
    override val dependencyValues: List<String> = emptyList(),
) : QuestionLike() {

    private var resolveDependencyCalled = false
    private var resolvedDependency: Question? = null

    override fun requiresAnswer(): Boolean = required

    override val dependency: QuestionLike?
        get() {
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
}
