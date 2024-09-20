package eu.pretix.libpretixsync.models

import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId

data class Event(
    val id: Long,
    val name: String,
    val slug: String,
    val currency: String,
    val isLive: Boolean,
    val hasSubEvents: Boolean,
    val dateFrom: OffsetDateTime,
    val dateTo: OffsetDateTime? = null,
    val timezone: ZoneId = ZoneId.of("UTC"),
    val plugins: List<String> = emptyList(),
    val hasSeating: Boolean = false,
    val seatCategoryMapping: JSONObject = JSONObject(),
    val validKeys: JSONObject? = null,
)
