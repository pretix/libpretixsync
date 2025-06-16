package eu.pretix.libpretixsync.sync

import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Event
import eu.pretix.libpretixsync.db.Order
import eu.pretix.libpretixsync.db.OrderPosition
import eu.pretix.libpretixsync.db.ResourceSyncStatus
import eu.pretix.libpretixsync.db.SubEvent
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import io.requery.BlockingEntityStore
import io.requery.Persistable
import io.requery.RollbackException
import io.requery.query.Tuple
import org.joda.time.DateTime
import org.joda.time.Duration
import org.json.JSONException
import kotlin.math.max

class OrderCleanup(val db: SyncDatabase, val store: BlockingEntityStore<Persistable>, val fileStorage: FileStorage, val api: PretixApi, val syncCycleId: String, val feedback: ProgressFeedback?) {
    private var subeventsDeletionDate: MutableMap<Long, Long?> = HashMap()
    private fun deletionTimeForSubevent(sid: Long, eventSlug: String): Long? {
        if (subeventsDeletionDate.containsKey(sid)) {
            return subeventsDeletionDate[sid]
        }
        try {
            SubEventSyncAdapter(db, eventSlug, sid.toString(), api, syncCycleId) { }.download()
        } catch (e: RollbackException) {
            subeventsDeletionDate[sid] = null
            return null
        } catch (e: JSONException) {
            subeventsDeletionDate[sid] = null
            return null
        } catch (e: ApiException) {
            subeventsDeletionDate[sid] = null
            return null
        }
        val se = store.select(SubEvent::class.java).where(SubEvent.SERVER_ID.eq(sid)).get().firstOrNull()
        if (se == null) {
            subeventsDeletionDate[sid] = null
            return null
        }
        val d = DateTime(if (se.getDate_to() != null) se.getDate_to() else se.getDate_from())
        val v = d.plus(Duration.standardDays(14)).millis
        subeventsDeletionDate[sid] = v
        return v
    }

    fun deleteOldSubevents(eventSlug: String, subeventId: Long?) {
        if (subeventId == null || subeventId < 1) {
            return
        }

        // To keep the local database small in large event series, we clean out orders that only
        // affect subevents more than 14 days in the past. However, doing so is not quite simple since
        // we need to take care of orders changing what subevents they effect. Therefore, we only
        // filter this server-side on an initial sync (see above). For every subsequent sync,
        // we still get a full diff of all orders.
        // After the diff fetch, we iterate over all orders and assign them a deletion date if they
        // currently do not have one.
        // Since we don't sync all subevents routinely, we fetch all subevents that we see for current
        // information and cache it in the subeventsDeletionDate map.
        // We then delete everything that is past its deletion date.
        // Further above, in updateObject(), we *always* reset the deletion date to 0 for anything
        // that's in the diff. This way, we can be sure to "un-delete" orders when they are changed
        // -- or when the subevent date is changed, which triggers all orders to be in the diff.
        val ordercount: Int = store.count(Order::class.java)
                .where(Order.EVENT_SLUG.eq(eventSlug))
                .and(Order.DELETE_AFTER_TIMESTAMP.isNull().or(Order.DELETE_AFTER_TIMESTAMP.lt(1L)))
                .get().value()
        var done = 0
        feedback?.postFeedback("Checking for old orders ($done/$ordercount) [$eventSlug] …")
        while (true) {
            val orders: List<Order> = store.select(Order::class.java)
                    .where(Order.EVENT_SLUG.eq(eventSlug))
                    .and(Order.DELETE_AFTER_TIMESTAMP.isNull().or(Order.DELETE_AFTER_TIMESTAMP.lt(1L)))
                    .limit(100)
                    .get().toList()
            if (orders.isEmpty()) {
                break
            }
            for (o in orders) {
                var deltime: Long? = null
                try {
                    val subeventIds = store.select(OrderPosition.SUBEVENT_ID)
                        .from(OrderPosition::class.java)
                        .where(OrderPosition.ORDER_ID.eq(o.id))
                        .get().toList().map { it.get(0) as Long? }.distinct()
                    if (subeventIds.isEmpty()) {
                        deltime = System.currentTimeMillis()
                    }
                    for (sId in subeventIds) {
                        if (sId == null || sId == 0L) {
                            deltime = System.currentTimeMillis() + 1000L * 3600 * 24 * 365 * 20 // should never happen, if it does, don't delete this any time soon
                            break
                        }
                        val thisDeltime = deletionTimeForSubevent(sId, eventSlug)
                        if (thisDeltime != null) {
                            deltime = if (deltime == null) {
                                thisDeltime
                            } else {
                                max(deltime, thisDeltime)
                            }
                        }
                    }
                } catch (e: JSONException) {
                    break
                }
                if (deltime == null) {
                    continue
                }
                o.setDeleteAfterTimestamp(deltime)
                store.update(o)
                done++
                if (done % 50 == 0) {
                    feedback?.postFeedback("Checking for old orders ($done/$ordercount) …")
                }
            }
        }
        feedback?.postFeedback("Deleting old orders…")
        var deleted = 0
        while (true) {
            val ordersToDelete: List<Tuple> = store.select(Order.ID).where(Order.DELETE_AFTER_TIMESTAMP.lt(System.currentTimeMillis()).and(Order.DELETE_AFTER_TIMESTAMP.gt(1L))).and(Order.ID.notIn(store.select(OrderPosition.ORDER_ID).from(OrderPosition::class.java).where(OrderPosition.SUBEVENT_ID.eq(subeventId)))).limit(200).get().toList()
            if (ordersToDelete.isEmpty()) {
                break
            }
            val idsToDelete: MutableList<Long> = ArrayList()
            for (t in ordersToDelete) {
                idsToDelete.add(t.get(0))
            }
            // sqlite foreign keys are created with `on delete cascade`, so order positions and checkins are handled automatically
            deleted += store.delete(Order::class.java).where(Order.ID.`in`(idsToDelete)).get().value()
            feedback?.postFeedback("Deleting old orders ($deleted)…")
        }
    }

    private var eventsDeletionDate = HashMap<String, Long>()
    private fun deletionTimeForEvent(slug: String): Long? {
        if (eventsDeletionDate.containsKey(slug)) {
            return eventsDeletionDate[slug]
        }
        val e: Event = store.select(Event::class.java).where(Event.SLUG.eq(slug)).get().firstOrNull()
                ?: return null
        val d = DateTime(if (e.getDate_to() != null) e.getDate_to() else e.getDate_from())
        val v = d.plus(Duration.standardDays(14)).millis
        eventsDeletionDate[slug] = v
        return v
    }

    fun deleteOldEvents(keepSlugs: List<String?>) {
        if (keepSlugs.isEmpty())
            return
        feedback?.postFeedback("Deleting orders of old events…")
        val tuples: List<Tuple> = store.select(Order.EVENT_SLUG)
                .from(Order::class.java)
                .where(Order.EVENT_SLUG.notIn(keepSlugs))
                .groupBy(Order.EVENT_SLUG)
                .orderBy(Order.EVENT_SLUG)
                .get().toList()
        var deleted = 0
        for (t in tuples) {
            val slug = t.get<String>(0)
            val deletionDate = deletionTimeForEvent(slug)
            if (deletionDate == null || deletionDate < System.currentTimeMillis()) {
                store.delete(ResourceSyncStatus::class.java).where(ResourceSyncStatus.RESOURCE.like("order%")).and(ResourceSyncStatus.EVENT_SLUG.eq(slug))
                while (true) {
                    val ordersToDelete: List<Tuple> = store.select(Order.ID).where(Order.EVENT_SLUG.eq(slug)).limit(200).get().toList()
                    if (ordersToDelete.isEmpty()) {
                        break
                    }
                    val idsToDelete: MutableList<Long> = ArrayList()
                    for (t2 in ordersToDelete) {
                        idsToDelete.add(t2.get(0))
                    }
                    // sqlite foreign keys are created with `on delete cascade`, so order positions and checkins are handled automatically
                    deleted += store.delete(Order::class.java).where(Order.ID.`in`(idsToDelete)).get().value()
                    feedback?.postFeedback("Deleting orders of old events ($deleted)…")
                }
            }
        }
    }

    fun deleteOldPdfImages() {
        db.cachedPdfImageQueries.deleteOld()
        for (filename in fileStorage.listFiles { _, s -> s.startsWith("pdfimage_") }) {
            val namebase = filename.split("\\.".toRegex()).toTypedArray()[0]
            val etag = namebase.split("_".toRegex()).toTypedArray()[1]
            if (db.cachedPdfImageQueries.countEtag(etag).executeAsOne() == 0L) {
                fileStorage.delete(filename)
            }
        }
    }
}