package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.ReusableMedium
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import org.json.JSONObject
import eu.pretix.libpretixsync.models.ReusableMedium as ReusableMediumModel

fun ReusableMedium.toModel(): ReusableMediumModel {
    val json = JSONObject(this.json_data!!)

    return ReusableMediumModel(
        id = this.id,
        serverId = this.server_id!!,
        active = this.active,
        customerId = this.customer_id,
        expires = SafeOffsetDateTimeMapper.decode(json, "expires"),
        identifier = this.identifier,
        linkedGiftCardId = this.linked_giftcard_id,
        type = this.type,
    )
}
