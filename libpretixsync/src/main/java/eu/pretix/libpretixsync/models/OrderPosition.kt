package eu.pretix.libpretixsync.models

import eu.pretix.libpretixsync.db.OrderPositionLike
import org.json.JSONObject
import java.math.BigDecimal

class OrderPosition(
    val id: Long,
    val itemId: Long,
    val serverId: Long? = null,
    val orderId: Long,
    val positionId: Long,
    val secret: String? = null,
    val subEventServerId: Long? = null,
    val variationServerId: Long? = null,
    val attendeeNameParts: JSONObject? = null,
    val city: String? = null,
    val company: String? = null,
    val country: String? = null,
    val email: String? = null,
    val street: String? = null,
    val zipcode: String? = null,
    val price: BigDecimal? = null,
    val taxRate: BigDecimal? = null,
    val taxValue: BigDecimal? = null,
    attendeeEmail: String? = null,
    attendeeName: String? = null,
) : OrderPositionLike {
    private val _attendeeEmail = attendeeEmail
    private val _attendeeName = attendeeName

    override fun getJSON(): JSONObject {
        // TODO: Remove RemoteObject from OrderPositionLike?
        throw NotImplementedError()
    }

    override fun getAttendeeName(): String {
        return _attendeeName!!
    }

    override fun getAttendeeEmail(): String {
        return _attendeeEmail!!
    }
}
