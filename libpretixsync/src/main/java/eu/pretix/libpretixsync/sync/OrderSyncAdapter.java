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
import eu.pretix.libpretixsync.db.Migrations;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.ResourceSyncStatus;
import eu.pretix.libpretixsync.db.SubEvent;
import eu.pretix.libpretixsync.utils.HashUtils;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.RollbackException;
import io.requery.query.Scalar;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;
import kotlin.reflect.jvm.internal.impl.util.Check;

public class OrderSyncAdapter extends BaseDownloadSyncAdapter<Order, String> {
    public OrderSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, Long subeventId, boolean withPdfData, boolean is_pretixpos, PretixApi api, String syncCylceId, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, syncCylceId, feedback);
        this.withPdfData = withPdfData;
        this.subeventId = subeventId;
        this.is_pretixpos = is_pretixpos;
    }

    private Map<Long, Item> itemCache = new HashMap<>();
    private Map<Long, CheckInList> listCache = new HashMap<>();
    private Map<Long, List<CheckIn>> checkinCache = new HashMap<>();
    private List<CheckIn> checkinCreateCache = new ArrayList<>();
    private String firstResponseTimestamp;
    private String lastOrderTimestamp;
    private ResourceSyncStatus rlm;
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
            ResourceSyncStatus resourceSyncStatus = store.select(ResourceSyncStatus.class)
                    .where(ResourceSyncStatus.RESOURCE.eq(rlmName()))
                    .and(ResourceSyncStatus.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();

            // We need to cache the response timestamp of the *first* page in the result set to make
            // sure we don't miss anything between this and the next run.
            //
            // If the download failed, completed will be false. In case this was a full fetch
            // (i.e. no timestamp was stored beforehand) we will still store the timestamp to be
            // able to continue properly.
            if (firstResponseTimestamp != null) {
                if (resourceSyncStatus == null) {
                    resourceSyncStatus = new ResourceSyncStatus();
                    resourceSyncStatus.setResource(rlmName());
                    resourceSyncStatus.setEvent_slug(eventSlug);
                    if (completed) {
                        resourceSyncStatus.setStatus("complete");
                    } else {
                        resourceSyncStatus.setStatus("incomplete:" + lastOrderTimestamp);
                    }
                    resourceSyncStatus.setLast_modified(firstResponseTimestamp);
                    store.upsert(resourceSyncStatus);
                } else {
                    if (completed) {
                        resourceSyncStatus.setLast_modified(firstResponseTimestamp);
                        store.upsert(resourceSyncStatus);
                    }
                }
            } else if (completed && resourceSyncStatus != null) {
                resourceSyncStatus.setStatus("complete");
                store.update(resourceSyncStatus);
            } else if (!completed && lastOrderTimestamp != null && resourceSyncStatus != null) {
                resourceSyncStatus.setStatus("incomplete:" + lastOrderTimestamp);
                store.update(resourceSyncStatus);
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
                updatePdfImages(store, fileStorage, api, obj.getServer_id(), images);
            }
        }
    }

    public static void updatePdfImages(BlockingEntityStore<Persistable> store, FileStorage fileStorage, PretixApi api, Long serverId, JSONObject images) {
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
            CachedPdfImage cpi = store.select(CachedPdfImage.class).where(CachedPdfImage.ORDERPOSITION_ID.eq(serverId)).and(CachedPdfImage.KEY.eq(k)).get().firstOrNull();
            if (cpi == null) {
                cpi = new CachedPdfImage();
                cpi.setEtag(etag);
                cpi.setKey(k);
                cpi.setOrderposition_id(serverId);
                store.insert(cpi);
            } else {
                cpi.setEtag(etag);
                store.update(cpi);
            }
        }

        store.delete(CachedPdfImage.class).where(
                CachedPdfImage.ORDERPOSITION_ID.eq(serverId).and(
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
        obj.setValid_if_pending(jsonobj.optBoolean("valid_if_pending", false));
        JSONObject json_data = new JSONObject(jsonobj.toString());
        json_data.remove("positions");
        obj.setJson_data(json_data.toString());
        obj.setDeleteAfterTimestamp(0L);

        if (obj.getId() == null) {
            store.insert(obj);
        }

        Map<Long, OrderPosition> known = new HashMap<>();
        List<OrderPosition> allPos = store.select(OrderPosition.class)
                .leftJoin(Order.class).on(Order.ID.eq(OrderPosition.ORDER_ID))
                .where(OrderPosition.ORDER_ID.eq(obj.getId())).get().toList();
        for (OrderPosition op : allPos) {
            known.put(op.getServer_id(), op);
        }

        JSONArray posarray = jsonobj.getJSONArray("positions");
        Map<Long, JSONObject> posmap = new HashMap<>();
        for (int i = 0; i < posarray.length(); i++) {
            JSONObject posjson = posarray.getJSONObject(i);
            posmap.put(posjson.getLong("id"), posjson);
        }
        for (int i = 0; i < posarray.length(); i++) {
            JSONObject posjson = posarray.getJSONObject(i);
            posjson.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION);
            posjson.put("__libpretixsync_syncCycleId", syncCycleId);
            Long jsonid = posjson.getLong("id");
            JSONObject old = null;
            OrderPosition posobj;
            if (known.containsKey(jsonid)) {
                posobj = known.get(jsonid);
                old = posobj.getJSON();
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
            rlm = store.select(ResourceSyncStatus.class)
                    .where(ResourceSyncStatus.RESOURCE.eq(rlmName()))
                    .and(ResourceSyncStatus.EVENT_SLUG.eq(eventSlug))
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
        checkinCache.clear();

        if (ids.isEmpty()) {
            return new HashMap<>();
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
        data.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION);
        data.put("__libpretixsync_syncCycleId", syncCycleId);
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
}
