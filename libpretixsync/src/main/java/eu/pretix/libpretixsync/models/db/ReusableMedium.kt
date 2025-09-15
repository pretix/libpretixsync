package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.ReusableMedium
import eu.pretix.libpretixsync.models.ReusableMedium as ReusableMediumModel

fun ReusableMedium.toModel(): ReusableMediumModel {
    return ReusableMediumModel(
        id = this.id,
        serverId = this.server_id!!,
        active = this.active,
        customerId = this.customer_id,
        expires = this.expires,
        identifier = this.identifier,
        linkedGiftCardId = this.linked_giftcard_id,
        linkedOrderPositionServerId = this.linked_orderposition_id,
        type = this.type,
    )
}
