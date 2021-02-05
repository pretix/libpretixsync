package eu.pretix.libpretixsync.sync;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.CachedPdfImage;
import eu.pretix.libpretixsync.db.CheckIn;
import eu.pretix.libpretixsync.db.CheckInList;
import eu.pretix.libpretixsync.db.Event;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.ResourceLastModified;
import eu.pretix.libpretixsync.db.SubEvent;
import eu.pretix.libpretixsync.utils.HashUtils;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Scalar;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;
import kotlin.reflect.jvm.internal.impl.util.Check;

public class OrderSyncAdapter extends BaseDownloadSyncAdapter<Order, String> {
    public OrderSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, Long subeventId, boolean withPdfData, boolean is_pretixpos, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
        this.withPdfData = withPdfData;
        this.subeventId = subeventId;
        this.is_pretixpos = is_pretixpos;
    }

    private Map<Long, Item> itemCache = new HashMap<>();
    private Map<Long, CheckInList> listCache = new HashMap<>();
    private Map<String, List<OrderPosition>> positionCache = new HashMap<>();
    private Map<Long, List<CheckIn>> checkinCache = new HashMap<>();
    private List<CheckIn> checkinCreateCache = new ArrayList<>();
    private String firstResponseTimestamp;
    private String lastOrderTimestamp;
    private ResourceLastModified rlm;
    private boolean withPdfData;
    private boolean is_pretixpos;
    private Long subeventId;

    private String rlmName() {
        if (withPdfData) {
            return "orders_withpdfdata";
        } else {
            return "orders";
        }
    }

    @Override
    public void download() throws JSONException, ApiException, ExecutionException, InterruptedException {
        boolean completed = false;
        try {
            super.download();
            completed = true;
        } finally {
            ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                    .where(ResourceLastModified.RESOURCE.eq(rlmName()))
                    .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();

            // We need to cache the response timestamp of the *first* page in the result set to make
            // sure we don't miss anything between this and the next run.
            //
            // If the download failed, completed will be false. In case this was a full fetch
            // (i.e. no timestamp was stored beforehand) we will still store the timestamp to be
            // able to continue properly.
            if (firstResponseTimestamp != null) {
                if (resourceLastModified == null) {
                    resourceLastModified = new ResourceLastModified();
                    resourceLastModified.setResource(rlmName());
                    resourceLastModified.setEvent_slug(eventSlug);
                    if (completed) {
                        resourceLastModified.setStatus("complete");
                    } else {
                        resourceLastModified.setStatus("incomplete:" + lastOrderTimestamp);
                    }
                    resourceLastModified.setLast_modified(firstResponseTimestamp);
                    store.upsert(resourceLastModified);
                } else {
                    if (completed) {
                        resourceLastModified.setLast_modified(firstResponseTimestamp);
                        store.upsert(resourceLastModified);
                    }
                }
            } else if (completed && resourceLastModified != null) {
                resourceLastModified.setStatus("complete");
                store.update(resourceLastModified);
            } else if (!completed && lastOrderTimestamp != null && resourceLastModified != null) {
                resourceLastModified.setStatus("incomplete:" + lastOrderTimestamp);
                store.update(resourceLastModified);
            }
            lastOrderTimestamp = null;
            firstResponseTimestamp = null;
        }
    }

    private Item getItem(long id) {
        if (itemCache.size() == 0) {
            List<Item> items = store
                    .select(Item.class)
                    .get().toList();
            for (Item item : items) {
                itemCache.put(item.getServer_id(), item);
            }
        }
        return itemCache.get(id);
    }

    private void updatePositionObject(OrderPosition obj, JSONObject jsonobj, JSONObject jsonorder, JSONObject parent) throws JSONException {
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setPositionid(jsonobj.getLong("positionid"));
        obj.setAttendee_name(jsonobj.isNull("attendee_name") ? "" : jsonobj.optString("attendee_name"));
        obj.setAttendee_email(jsonobj.isNull("attendee_email") ? "" : jsonobj.optString("attendee_email"));
        obj.setSecret(jsonobj.optString("secret"));
        obj.setJson_data(jsonobj.toString());
        obj.setItem(getItem(jsonobj.getLong("item")));
        obj.setSubevent_id(jsonobj.optLong("subevent"));
        obj.setVariation_id(jsonobj.optLong("variation"));

        if (obj.getAttendee_name() == null && parent != null && !parent.isNull("attendee_name")) {
            obj.setAttendee_name(parent.getString("attendee_name"));
        }
        if (obj.getAttendee_email() == null && parent != null && !parent.isNull("attendee_email")) {
            obj.setAttendee_email(parent.getString("attendee_email"));
        }

        if (obj.getAttendee_name() == null) {
            try {
                JSONObject jInvoiceAddress = jsonorder.getJSONObject("invoice_address");
                if (jInvoiceAddress.isNull("name")) {
                    obj.setAttendee_name(jInvoiceAddress.getString("name"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (obj.getId() == null) {
            store.insert(obj);
        }

        Map<Long, CheckIn> known = new HashMap<>();
        List<CheckIn> checkincache = checkinCache.get(obj.getId());
        if (checkincache != null) {
            for (CheckIn op : checkincache) {
                if (op.getServer_id() != null && op.getServer_id() > 0) {
                    known.put(op.getServer_id(), op);
                } else {
                    store.delete(op);
                }
            }
        }
        JSONArray checkins = jsonobj.getJSONArray("checkins");
        for (int i = 0; i < checkins.length(); i++) {
            JSONObject ci = checkins.getJSONObject(i);
            Long listid = ci.getLong("list");
            if (known.containsKey(listid)) {
                CheckIn ciobj = known.remove(listid);
                ciobj.setPosition(obj);
                ciobj.setType(ci.optString("type", "entry"));
                ciobj.setListId(listid);
                ciobj.setDatetime(ISODateTimeFormat.dateTimeParser().parseDateTime(ci.getString("datetime")).toDate());
                ciobj.setJson_data(ci.toString());
                store.update(ciobj);
            } else {
                CheckIn ciobj = new CheckIn();
                ciobj.setPosition(obj);
                ciobj.setType(ci.optString("type", "entry"));
                ciobj.setListId(listid);
                ciobj.setDatetime(ISODateTimeFormat.dateTimeParser().parseDateTime(ci.getString("datetime")).toDate());
                ciobj.setJson_data(ci.toString());
                ciobj.setServer_id(ci.optLong("id"));
                checkinCreateCache.add(ciobj);
            }
        }
        if (known.size() > 0) {
            store.delete(known.values());
        }


        // Images
        if (jsonobj.has("pdf_data")) {
            JSONObject pdfdata = jsonobj.getJSONObject("pdf_data");
            if (pdfdata.has("images")) {
                JSONObject images = pdfdata.getJSONObject("images");
                updatePdfImages(obj, images);
            }
        }
    }

    private void updatePdfImages(OrderPosition op, JSONObject images) {
        Set<String> seen_etags = new HashSet<>();
        for (Iterator it = images.keys(); it.hasNext(); ) {
            String k = (String) it.next();
            String remote_filename = images.optString(k);
            if (remote_filename == null || !remote_filename.startsWith("http")) {
                continue;
            }
            String etag = HashUtils.toSHA1(remote_filename.getBytes());
            if (remote_filename.contains("#etag=")) {
                etag = remote_filename.split("#etag=")[1];
            }
            String local_filename = "pdfimage_" + etag + ".bin";
            seen_etags.add(etag);

            if (!fileStorage.contains(local_filename)) {
                try {
                    PretixApi.ApiResponse file = api.downloadFile(remote_filename);
                    OutputStream os = fileStorage.writeStream(local_filename);
                    InputStream is = file.getResponse().body().byteStream();
                    byte[] buffer = new byte[1444];
                    int byteread;
                    while ((byteread = is.read(buffer)) != -1) {
                        os.write(buffer, 0, byteread);
                    }
                    is.close();
                    os.close();
                } catch (ApiException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                    fileStorage.delete(local_filename);
                }
            }
            CachedPdfImage cpi = store.select(CachedPdfImage.class).where(CachedPdfImage.ORDERPOSITION_ID.eq(op.getId())).and(CachedPdfImage.KEY.eq(k)).get().firstOrNull();
            if (cpi == null) {
                cpi = new CachedPdfImage();
                cpi.setEtag(etag);
                cpi.setKey(k);
                cpi.setOrderposition_id(op.getId());
                store.insert(cpi);
            } else {
                cpi.setEtag(etag);
                store.update(cpi);
            }
        }

        store.delete(CachedPdfImage.class).where(
                CachedPdfImage.ORDERPOSITION_ID.eq(op.getId()).and(
                        CachedPdfImage.ETAG.notIn(seen_etags)
                )
        );
    }

    @Override
    protected void afterPage() {
        super.afterPage();
        store.insert(checkinCreateCache);
        checkinCreateCache.clear();
    }

    @Override
    public void updateObject(Order obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setCode(jsonobj.getString("code"));
        obj.setStatus(jsonobj.getString("status"));
        obj.setEmail(jsonobj.optString("email"));
        obj.setCheckin_attention(jsonobj.optBoolean("checkin_attention"));
        obj.setJson_data(jsonobj.toString());
        obj.setDeleteAfterTimestamp(0L);

        if (obj.getId() == null) {
            store.insert(obj);
        }

        Map<Long, OrderPosition> known = new HashMap<>();
        List<OrderPosition> positions = positionCache.get(obj.getCode());
        if (positions != null) {
            for (OrderPosition op : positions) {
                known.put(op.getServer_id(), op);
            }
        }

        JSONArray posarray = jsonobj.getJSONArray("positions");
        Map<Long, JSONObject> posmap = new HashMap<>();
        for (int i = 0; i < posarray.length(); i++) {
            JSONObject posjson = posarray.getJSONObject(i);
            posmap.put(posjson.getLong("id"), posjson);
        }
        for (int i = 0; i < posarray.length(); i++) {
            JSONObject posjson = posarray.getJSONObject(i);
            Long jsonid = posjson.getLong("id");
            JSONObject old = null;
            OrderPosition posobj;
            if (known.containsKey(jsonid)) {
                posobj = known.get(jsonid);
                old = obj.getJSON();
            } else {
                posobj = new OrderPosition();
                posobj.setOrder(obj);
            }
            JSONObject parent = null;
            if (!posjson.isNull("addon_to")) {
                parent = posmap.get(posjson.getLong("addon_to"));
            }
            if (known.containsKey(jsonid)) {
                known.remove(jsonid);
                if (!JSONUtils.similar(posjson, old)) {
                    updatePositionObject(posobj, posjson, jsonobj, parent);
                    store.update(posobj);
                }
            } else {
                updatePositionObject(posobj, posjson, jsonobj, parent);
            }
        }
        if (known.size() > 0) {
            store.delete(known.values());
        }
    }

    @Override
    protected boolean deleteUnseen() {
        return false;
    }

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        if (isFirstPage) {
            rlm = store.select(ResourceLastModified.class)
                    .where(ResourceLastModified.RESOURCE.eq(rlmName()))
                    .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();
        }
        boolean is_continued_fetch = false;
        if (!url.contains("testmode=")) {
            if (url.contains("?")) {
                url += "&";
            } else {
                url += "?";
            }
            url += "testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads";
            if (!is_pretixpos) {
                url += "&exclude=payments&exclude=refunds";
            }
            if (withPdfData) {
                url += "&pdf_data=true";
            }
        }

        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ");
        DateTime cutoff = new DateTime().withZone(DateTimeZone.UTC).minus(Duration.standardDays(14));
        String firstrun_params = "";
        try {
            if (subeventId != null && subeventId > 0) {
                firstrun_params = "&subevent_after=" + URLEncoder.encode(formatter.print(cutoff), "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        // On event series, we ignore orders that only affect subevents more than 14 days old.
        // However, we can only do that on the first run, since we'd otherwise miss if e.g. an order
        // that we have in our current database is changed to a date outside that time frame.

        if (rlm != null) {
            // This resource has been fetched before.
            if (rlm.getStatus() != null && rlm.getStatus().startsWith("incomplete:")) {
                // Continuing an interrupted fetch

                // Ordering is crucial here: Only because the server returns the orders in the
                // order of creation we can be sure that we don't miss orders created in between our
                // paginated requests.
                is_continued_fetch = true;
                try {
                    if (!url.contains("created_since")) {
                        url += "&ordering=datetime&created_since=" + URLEncoder.encode(rlm.getStatus().substring(11), "UTF-8") + firstrun_params;
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                // Diff to last time

                // Ordering is crucial here: Only because the server returns the orders in the
                // order of modification we can be sure that we don't miss orders created in between our
                // paginated requests. If an order were to be modified between our fetch of page 1
                // and 2 that originally wasn't part of the result set, we won't see it (as it will
                // be inserted on page 1), but we'll see it the next time, and we will se some
                // duplicates on page 2, but we don't care. The important part is that nothing gets
                // lost "between the pages". If an order of page 2 gets modified and moves to page
                // one while we fetch page 2, again, we won't see it and we'll see some duplicates,
                // but the next sync will fix it since we always fetch our diff compared to the time
                // of the first page.
                try {
                    if (!url.contains("modified_since")) {
                        url += "&ordering=-last_modified&modified_since=" + URLEncoder.encode(rlm.getLast_modified(), "UTF-8");
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (!url.contains("subevent_after")) {
                url += firstrun_params;
            }
        }

        PretixApi.ApiResponse apiResponse = api.fetchResource(url);
        if (isFirstPage && !is_continued_fetch) {
            firstResponseTimestamp = apiResponse.getResponse().header("X-Page-Generated");
        }
        JSONObject d = apiResponse.getData();
        if (apiResponse.getResponse().code() == 200) {
            try {
                JSONArray res = d.getJSONArray("results");
                if (res.length() > 0) {
                    lastOrderTimestamp = res.getJSONObject(res.length() - 1).getString("datetime");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

    @Override
    protected Map<String, Order> getKnownObjects(Set<String> ids) {
        positionCache.clear();
        checkinCache.clear();

        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<OrderPosition> allPos = store.select(OrderPosition.class)
                .leftJoin(Order.class).on(Order.ID.eq(OrderPosition.ORDER_ID))
                .where(Order.CODE.in(ids)).get().toList();
        for (OrderPosition p : allPos) {
            String code = p.getOrder().getCode();
            if (positionCache.containsKey(code)) {
                positionCache.get(code).add(p);
            } else {
                List<OrderPosition> opos = new ArrayList<>();
                opos.add(p);
                positionCache.put(code, opos);
            }
        }

        List<CheckIn> allCheckins = store.select(CheckIn.class)
                .leftJoin(OrderPosition.class).on(OrderPosition.ID.eq(CheckIn.POSITION_ID))
                .leftJoin(Order.class).on(Order.ID.eq(OrderPosition.ORDER_ID))
                .where(Order.CODE.in(ids)).get().toList();
        for (CheckIn c : allCheckins) {
            Long pk = c.getPosition().getId();
            if (checkinCache.containsKey(pk)) {
                checkinCache.get(pk).add(c);
            } else {
                List<CheckIn> l = new ArrayList<>();
                l.add(c);
                checkinCache.put(pk, l);
            }
        }

        return super.getKnownObjects(ids);
    }

    @Override
    public CloseableIterator<Order> runBatch(List<String> ids) {
        return store.select(Order.class)
                .where(Order.EVENT_SLUG.eq(eventSlug))
                .and(Order.CODE.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(Order.CODE)
                .where(Item.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    protected boolean autoPersist() {
        return false;
    }

    @Override
    String getResourceName() {
        return "orders";
    }

    @Override
    String getId(JSONObject obj) throws JSONException {
        return obj.getString("code");
    }

    @Override
    String getId(Order obj) {
        return obj.getCode();
    }

    @Override
    Order newEmptyObject() {
        return new Order();
    }

    public void standaloneRefreshFromJSON(JSONObject data) throws JSONException {
        Order order = store.select(Order.class)
                .where(Order.CODE.eq(data.getString("code")))
                .get().firstOr(newEmptyObject());
        JSONObject old = null;
        if (order.getId() != null) {
            old = order.getJSON();
        }

        // Warm up cache
        Set<String> ids = new HashSet<>();
        ids.add(data.getString("code"));
        getKnownObjects(ids);
        // Store object
        if (old == null) {
            updateObject(order, data);
        } else {
            if (!JSONUtils.similar(data, old)) {
                updateObject(order, data);
                store.update(order);
            }
        }
        store.insert(checkinCreateCache);
        checkinCreateCache.clear();
    }

    Map<Long, Long> subeventsDeletionDate = new HashMap<>();

    private Long deletionTimeForSubevent(long sid) {
        if (subeventsDeletionDate.containsKey(sid)) {
            return subeventsDeletionDate.get(sid);
        }

        try {
            new SubEventSyncAdapter(store, eventSlug, String.valueOf(sid), api, current_action -> {
            }).download();
        } catch (JSONException | ApiException e) {
            subeventsDeletionDate.put(sid, null);
            return null;
        }

        SubEvent se = store.select(SubEvent.class).where(SubEvent.SERVER_ID.eq(sid)).get().firstOrNull();
        if (se == null) {
            subeventsDeletionDate.put(sid, null);
            return null;
        }

        DateTime d = new DateTime(se.getDate_to() != null ? se.getDate_to() : se.getDate_from());
        long v = d.plus(Duration.standardDays(14)).getMillis();
        subeventsDeletionDate.put(sid, v);
        return v;
    }

    public void deleteOldSubevents() {
        if (this.subeventId == null || this.subeventId < 1) {
            return;
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
        int ordercount = store.count(Order.class)
                .where(Order.EVENT_SLUG.eq(eventSlug))
                .and(Order.DELETE_AFTER_TIMESTAMP.isNull().or(Order.DELETE_AFTER_TIMESTAMP.lt(1L)))
                .get().value();

        int done = 0;

        if (feedback != null) {
            feedback.postFeedback("Checking for old " + getResourceName() + " (" + done + "/" + ordercount + ") …");
        }

        while (true) {
            List<Order> orders = store.select(Order.class)
                    .where(Order.EVENT_SLUG.eq(eventSlug))
                    .and(Order.DELETE_AFTER_TIMESTAMP.isNull().or(Order.DELETE_AFTER_TIMESTAMP.lt(1L)))
                    .limit(100)
                    .get().toList();
            if (orders.size() == 0) {
                break;
            }

            for (Order o : orders) {

                Long deltime = null;
                try {
                    JSONArray pos = o.getJSON().getJSONArray("positions");
                    if (pos.length() == 0) {
                        deltime = System.currentTimeMillis();
                    }
                    for (int i = 0; i < pos.length(); i++) {
                        JSONObject p = pos.getJSONObject(i);
                        if (p.isNull("subevent")) {
                            deltime = System.currentTimeMillis() + 1000 * 3600 * 24 * 365 * 20;  // should never happen, if it does, don't delete this any time soon
                            break;
                        }
                        Long thisDeltime = deletionTimeForSubevent(p.getLong("subevent"));
                        if (thisDeltime != null) {
                            if (deltime == null) {
                                deltime = thisDeltime;
                            } else {
                                deltime = Math.max(deltime, thisDeltime);
                            }
                        }
                    }
                } catch (JSONException e) {
                    break;
                }
                if (deltime == null) {
                    continue;
                }
                o.setDeleteAfterTimestamp(deltime);
                store.update(o);
                done++;
                if (done % 50 == 0) {
                    if (feedback != null) {
                        feedback.postFeedback("Checking for old " + getResourceName() + " (" + done + "/" + ordercount + ") …");
                    }
                }
            }
        }

        if (feedback != null) {
            feedback.postFeedback("Deleting old " + getResourceName() + "…");
        }
        int deleted = 0;
        while (true) {
            List<Tuple> ordersToDelete = store.select(Order.ID).where(Order.DELETE_AFTER_TIMESTAMP.lt(System.currentTimeMillis()).and(Order.DELETE_AFTER_TIMESTAMP.gt(1L))).and(Order.ID.notIn(store.select(OrderPosition.ORDER_ID).from(OrderPosition.class).where(OrderPosition.SUBEVENT_ID.eq(this.subeventId)))).limit(200).get().toList();
            if (ordersToDelete.size() == 0) {
                break;
            }
            List<Long> idsToDelete = new ArrayList<>();
            for (Tuple t : ordersToDelete) {
                idsToDelete.add(t.get(0));
            }
            // sqlite foreign keys are created with `on delete cascade`, so order positions and checkins are handled automatically
            deleted += store.delete(Order.class).where(Order.ID.in(idsToDelete)).get().value();
            if (feedback != null) {
                feedback.postFeedback("Deleting old " + getResourceName() + " (" + deleted + ")…");
            }
        }
    }

    Map<String, Long> eventsDeletionDate = new HashMap<>();

    private Long deletionTimeForEvent(String slug) {
        if (eventsDeletionDate.containsKey(slug)) {
            return eventsDeletionDate.get(slug);
        }

        Event e = store.select(Event.class).where(Event.SLUG.eq(slug)).get().firstOrNull();

        DateTime d = new DateTime(e.getDate_to() != null ? e.getDate_to() : e.getDate_from());
        long v = d.plus(Duration.standardDays(14)).getMillis();
        eventsDeletionDate.put(slug, v);
        return v;
    }

    public void deleteOldEvents() {
        if (feedback != null) {
            feedback.postFeedback("Deleting " + getResourceName() + " of old events…");
        }

        List<Tuple> tuples = store.select(Order.EVENT_SLUG)
                .from(Order.class)
                .where(Order.EVENT_SLUG.ne(eventSlug))
                .groupBy(Order.EVENT_SLUG)
                .orderBy(Order.EVENT_SLUG)
                .get().toList();
        int deleted = 0;
        for (Tuple t : tuples) {
            String slug = t.get(0);
            Long deletionDate = deletionTimeForEvent(slug);
            if (deletionDate < System.currentTimeMillis()) {
                store.delete(ResourceLastModified.class).where(ResourceLastModified.RESOURCE.like("order%")).and(ResourceLastModified.EVENT_SLUG.eq(slug));
                while (true) {
                    List<Tuple> ordersToDelete = store.select(Order.ID).where(Order.EVENT_SLUG.eq(slug)).limit(200).get().toList();
                    if (ordersToDelete.size() == 0) {
                        break;
                    }
                    List<Long> idsToDelete = new ArrayList<>();
                    for (Tuple t2 : ordersToDelete) {
                        idsToDelete.add(t2.get(0));
                    }
                    // sqlite foreign keys are created with `on delete cascade`, so order positions and checkins are handled automatically
                    deleted += store.delete(Order.class).where(Order.ID.in(idsToDelete)).get().value();
                    if (feedback != null) {
                        feedback.postFeedback("Deleting " + getResourceName() + " of old events (" + deleted + ")…");
                    }
                }
            }
        }
    }

    public void deleteOldPdfImages() {
        store.delete(CachedPdfImage.class).where(
                CachedPdfImage.ORDERPOSITION_ID.notIn(store.select(OrderPosition.ID).from(OrderPosition.class))
        );
        for (String filename : fileStorage.listFiles((file, s) -> s.startsWith("pdfimage_"))) {
            String namebase = filename.split("\\.")[0];
            String etag = namebase.split("_")[1];
            if (store.count(CachedPdfImage.class).where(CachedPdfImage.ETAG.eq(etag)).get().value() == 0) {
                fileStorage.delete(filename);
            }
        }
    }
}
