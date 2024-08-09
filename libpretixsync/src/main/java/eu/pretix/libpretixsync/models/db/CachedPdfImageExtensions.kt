package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.CachedPdfImage
import eu.pretix.libpretixsync.models.CachedPdfImage as CachedPdfImageModel

fun CachedPdfImage.toModel() =
    CachedPdfImageModel(
        id = this.id,
        orderPositionServerId = orderposition_id!!,
        etag = etag!!,
        key = key!!,
    )
