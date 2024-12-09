package eu.pretix.libpretixsync.database

import eu.pretix.libpretixsync.sqldelight.BadgeLayout
import org.json.JSONArray
import org.json.JSONObject
import eu.pretix.libpretixsync.models.BadgeLayout as BadgeLayoutModel

fun BadgeLayout.toModel() =
    BadgeLayoutModel(
        id = this.id,
        backgroundFilename = this.background_filename,
        eventSlug = this.event_slug!!,
        isDefault = this.is_default,
        layout = JSONObject(this.json_data!!).optJSONArray("layout") ?: JSONArray(),
        serverId = this.server_id!!,
    )
