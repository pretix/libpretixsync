package eu.pretix.libpretixsync.models

import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.db.QuestionLike
import eu.pretix.libpretixsync.db.QuestionOption
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

class Questionnaire(
    val id: Long,
    val type: String,
    val internalName: String,
    val position: Long,
    val rows: List<Row>
) {
    data class Option(
        val identifier: String,
        val answer: String,
        val position: Long,
    )

    data class DataField(
        val identifier: String,
        val type: QuestionType,
        val internalName: String,
        val showDuringCheckin: Boolean,
        val printOnInvoice: Boolean,
        val printOnInvoiceLabel: String,
        val options: List<Option>,
        val validNumberMin: BigDecimal? = null,
        val validNumberMax: BigDecimal? = null,
        val validDatetimeMin: OffsetDateTime? = null,
        val validDatetimeMax: OffsetDateTime? = null,
        val validDateMin: LocalDate? = null,
        val validDateMax: LocalDate? = null,
        val validStringLengthMin: Long? = null,
        val validStringLengthMax: Long? = null,
        val validFilePortrait: Boolean = false,
    )

    data class Row(
        val required: Boolean,
        val label: String,
        val helpText: String,
        val renderedHelpText: String,
        val dependencyDataField: String?,
        val dependencyValues: List<String>,
        val dataField: DataField?,
    )
}
