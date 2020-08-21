package eu.pretix.libpretixsync.sync;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
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
import eu.pretix.libpretixsync.db.CheckIn;
import eu.pretix.libpretixsync.db.CheckInList;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.ResourceLastModified;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class OrderSyncAdapter extends BaseDownloadSyncAdapter<Order, String> {
    public OrderSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, boolean withPdfData, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
        this.withPdfData = withPdfData;
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

    private CheckInList getList(long id) {
        if (listCache.size() == 0) {
            List<CheckInList> items = store
                    .select(CheckInList.class)
                    .get().toList();
            for (CheckInList item : items) {
                listCache.put(item.getServer_id(), item);
            }
        }
        return listCache.get(id);
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
                known.put(op.getList().getServer_id(), op);
            }
        }
        JSONArray checkins = jsonobj.getJSONArray("checkins");
        for (int i = 0; i < checkins.length(); i++) {
            JSONObject ci = checkins.getJSONObject(i);
            Long listid = ci.getLong("list");
            CheckInList list = getList(listid);
            if (list == null) {
                continue;
            }
            if (known.containsKey(listid)) {
                CheckIn ciobj = known.remove(listid);
                ciobj.setDatetime(ISODateTimeFormat.dateTimeParser().parseDateTime(ci.getString("datetime")).toDate());
                ciobj.setType(ci.optString("type", "entry"));
                ciobj.setJson_data(ci.toString());
                store.update(ciobj);
            } else {
                CheckIn ciobj = new CheckIn();
                ciobj.setPosition(obj);
                ciobj.setType(ci.optString("type", "entry"));
                ciobj.setList(getList(listid));
                ciobj.setDatetime(ISODateTimeFormat.dateTimeParser().parseDateTime(ci.getString("datetime")).toDate());
                ciobj.setJson_data(ci.toString());
                checkinCreateCache.add(ciobj);
            }
        }
        if (known.size() > 0) {
            store.delete(known.values());
        }
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
                url += "&testmode=false";
            } else {
                url += "?testmode=false";
            }
            if (withPdfData) {
                url += "&pdf_data=true";
            }
        }

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
                        url += "&ordering=datetime&created_since=" + URLEncoder.encode(rlm.getStatus().substring(11), "UTF-8");
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
                // be inserted on page 2), but we'll see it the next time, and we will se some
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
}
