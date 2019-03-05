package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
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

public class OrderSyncAdapter extends BaseDownloadSyncAdapter<Order, String> {
    public OrderSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    private Map<Long, Item> itemCache = new HashMap<>();
    private Map<Long, CheckInList> listCache = new HashMap<>();
    private Map<String, List<OrderPosition>> positionCache = new HashMap<>();
    private Map<Long, List<CheckIn>> checkinCache = new HashMap<>();
    private List<CheckIn> checkinCreateCache = new ArrayList<>();
    private PretixApi.ApiResponse firstResponse;

    @Override
    public void download() throws JSONException, ApiException, ExecutionException, InterruptedException {
        super.download();
        if (firstResponse != null) {
            ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                    .where(ResourceLastModified.RESOURCE.eq("orders"))
                    .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                    .limit(1)
                    .get().firstOrNull();
            if (resourceLastModified == null) {
                resourceLastModified = new ResourceLastModified();
                resourceLastModified.setResource("orders");
                resourceLastModified.setEvent_slug(eventSlug);
            }
            if (firstResponse.getResponse().header("X-Page-Generated") != null) {
                resourceLastModified.setLast_modified(firstResponse.getResponse().header("X-Page-Generated"));
                store.upsert(resourceLastModified);
            }
            firstResponse = null;
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
        obj.setAttendee_name(jsonobj.optString("attendee_name"));
        obj.setAttendee_email(jsonobj.optString("attendee_email"));
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
                ciobj.fromJSON(ci);
                store.update(ciobj);
            } else {
                CheckIn ciobj = new CheckIn();
                ciobj.setPosition(obj);
                ciobj.setList(getList(listid));
                ciobj.fromJSON(ci);
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
        ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                .where(ResourceLastModified.RESOURCE.eq("orders"))
                .and(ResourceLastModified.EVENT_SLUG.eq(eventSlug))
                .limit(1)
                .get().firstOrNull();
        if (resourceLastModified == null) {
            if (!url.contains("testmode")) {
                if (url.contains("?")) {
                    url += "&pdf_data=true&testmode=false";
                } else {
                    url += "?pdf_data=true&testmode=false";
                }
            }
        } else {
            try {
                if (!url.contains("modified_since")) {
                    if (url.contains("?")) {
                        url += "&pdf_data=true&testmode=false&modified_since=" + URLEncoder.encode(resourceLastModified.getLast_modified(), "UTF-8");
                    } else {
                        url += "?pdf_data=true&testmode=false&modified_since=" + URLEncoder.encode(resourceLastModified.getLast_modified(), "UTF-8");
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        PretixApi.ApiResponse apiResponse = api.fetchResource(url);
        if (isFirstPage) {
            firstResponse = apiResponse;
        }
        return apiResponse.getData();
    }

    @Override
    protected Map<String, Order> getKnownObjects(Set<String> ids) {
        positionCache.clear();
        checkinCache.clear();

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
    public Iterator<Order> runBatch(List<String> ids) {
        return store.select(Order.class)
                .where(Order.EVENT_SLUG.eq(eventSlug))
                .and(Order.CODE.in(ids))
                .get().iterator();
    }

    @Override
    Iterator<Tuple> getKnownIDsIterator() {
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
}
