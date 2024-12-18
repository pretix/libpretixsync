package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.CheckIn
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import org.json.JSONObject
import eu.pretix.libpretixsync.models.CheckIn as CheckInModel

fun CheckIn.toModel(): CheckInModel {
    val json = JSONObject(this.json_data)

    return CheckInModel(
        id = this.id,
        serverId = this.server_id,
        listServerId = this.listId,
        positionId = this.position,
        type = this.type,
        // Use date values from JSON, as they contain time zone information
        datetime = SafeOffsetDateTimeMapper.decode(json, "datetime"),
    )
}
