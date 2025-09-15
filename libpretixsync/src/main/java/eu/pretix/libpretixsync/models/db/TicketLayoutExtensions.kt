package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.TicketLayout
import org.json.JSONArray
import org.json.JSONObject
import eu.pretix.libpretixsync.models.TicketLayout as TicketLayoutModel

fun TicketLayout.toModel() =
    TicketLayoutModel(
        id = this.id,
        backgroundFilename = this.background_filename,
        eventSlug = this.event_slug!!,
        isDefault = this.is_default,
        layout = JSONObject(this.json_data!!).optJSONArray("layout") ?: JSONArray(),
        serverId = this.server_id!!,
    )
