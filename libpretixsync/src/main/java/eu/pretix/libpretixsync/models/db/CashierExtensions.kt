package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.Cashier
import org.json.JSONObject
import eu.pretix.libpretixsync.models.Cashier as CashierModel

fun Cashier.toModel(): CashierModel {
    val json = JSONObject(this.json_data)

    return CashierModel(
        id = this.id,
        numericId = this.server_id,
        userId = this.userid,
        name = this.name!!,
        active = this.active,
        pin = this.pin!!,
        team = json.optJSONObject("team"),
    )
}
