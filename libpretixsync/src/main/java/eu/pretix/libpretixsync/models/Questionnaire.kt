package eu.pretix.libpretixsync.models

import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.db.QuestionLike
import eu.pretix.libpretixsync.db.QuestionOption
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.String

class Questionnaire(
    val id: Long,
    val type: String,
    val internalName: String,
    val position: Long,
    val allSalesChannels: Boolean,
    val limitSalesChannels: List<String>,
    rows: List<RowSpec>
) {
    val rows = rows.map {
        it.toRow(this)
    }

    data class DataField(
        val identifier: String,
        val type: QuestionType,
        val internalName: String,
        val showDuringCheckin: Boolean,
        val printOnInvoice: Boolean,
        val printOnInvoiceLabel: String?,
        val options: List<QuestionOption>,
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

    data class RowSpec(
        val required: Boolean,
        val label: String,
        val helpText: String,
        val renderedHelpText: String,
        val dependencyDataField: String?,
        val dependencyValues: List<String>,
        val dataField: DataField?
    ) {
        fun toRow(parent: Questionnaire): Row {
            return Row(
                required = required,
                label = label,
                helpText = helpText,
                renderedHelpText = renderedHelpText,
                dependencyValues = dependencyValues,
                dependencyDataField = dependencyDataField,
                parent = parent,
                dataField = dataField,
            )
        }
    }

    data class Row(
        override val required: Boolean,
        val label: String,
        val helpText: String,
        val renderedHelpText: String,
        val parent: Questionnaire,
        val dependencyDataField: String?,
        override val dependencyValues: List<String>,
        val dataField: DataField?
    ) : QuestionLike() {
        override val serializeOptionToIdentifier = true

        override val type: QuestionType
            get() = dataField?.type
                ?: throw IllegalArgumentException("Cannot use text block as QuestionLike")

        override val question: String
            get() = label

        override val identifier: String
            get() = dataField?.identifier
                ?: throw IllegalArgumentException("Cannot use text block as QuestionLike")

        override val options: List<QuestionOption>?
            get() = dataField?.options

        override val validDateMin: Long?
            get() = dataField?.validDateMin?.atTime(LocalTime.MIDNIGHT)
                ?.atZone(ZoneOffset.systemDefault())?.toEpochSecond()?.times(1000L)

        override val validDateMax: Long?
            get() = dataField?.validDateMax?.atTime(LocalTime.MIDNIGHT)
                ?.atZone(ZoneOffset.systemDefault())?.toEpochSecond()?.times(1000L)

        override val validDateTimeMin: Long?
            get() = dataField?.validDatetimeMin?.toEpochSecond()?.times(1000L)

        override val validDateTimeMax: Long?
            get() = dataField?.validDatetimeMax?.toEpochSecond()?.times(1000L)

        override val dependency: QuestionLike?
            get() {
                if (dependencyDataField == null) return null
                return parent.rows.find { it.dataField?.identifier == dependencyDataField }
            }
    }
}
