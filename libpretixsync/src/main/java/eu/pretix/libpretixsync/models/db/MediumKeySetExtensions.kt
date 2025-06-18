package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.MediumKeySet
import eu.pretix.libpretixsync.models.MediumKeySet as MediumKeySetModel

fun MediumKeySet.toModel(): MediumKeySetModel {
    return MediumKeySetModel(
        id = this.id,
        publicId = this.public_id!!,
        active = this.active,
        mediaType = this.media_type,
        uidKey = this.uid_key!!,
        diversificationKey = this.diversification_key!!,
        organizer = this.organizer,
    )
}
