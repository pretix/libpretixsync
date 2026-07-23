package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.CheckIn
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import eu.pretix.libpretixsync.models.CheckIn as CheckInModel

fun CheckIn.toModel(): CheckInModel {
    val json = JSONObject(this.json_data)

    fun toOffsetDateTime(d: Date?): OffsetDateTime? {
        if (d == null) {
            return null
        }
        return d.toInstant().atOffset(ZoneOffset.systemDefault().rules.getOffset(d.toInstant()))
    }

    return CheckInModel(
        id = this.id,
        serverId = this.server_id,
        listServerId = this.listId,
        positionId = this.position,
        type = this.type,
        // Use date values from JSON, as they contain time zone information
        dateTime = SafeOffsetDateTimeMapper.decode(json, "datetime"),
        localAnnulled = toOffsetDateTime(this.local_annulled),
        localNonce = this.local_nonce
    )
}
