package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.api.ResourceNotModified;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Order;
import eu.pretix.libpretixsync.db.OrderPosition;
import eu.pretix.libpretixsync.db.Quota;
import eu.pretix.libpretixsync.db.ResourceLastModified;
import eu.pretix.libpretixsync.utils.JSONUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class OrderSyncAdapter extends BaseDownloadSyncAdapter<Order, String> {
    public OrderSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, PretixApi api) {
        super(store, eventSlug, api);
    }

    private void updatePositionObject(OrderPosition obj, JSONObject jsonobj) throws JSONException {
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setPositionid(jsonobj.getLong("positionid"));
        obj.setAttendee_name(jsonobj.optString("attendee_name"));
        obj.setAttendee_email(jsonobj.optString("attendee_email"));
        obj.setSecret(jsonobj.optString("secret"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    protected void updateObject(Order obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setCode(jsonobj.getString("code"));
        obj.setStatus(jsonobj.getString("status"));
        obj.setEmail(jsonobj.optString("email"));
        obj.setCheckin_attention(jsonobj.optBoolean("checkin_attention"));
        obj.setJson_data(jsonobj.toString());

        Map<Long, OrderPosition> known = new HashMap<>();
        for (OrderPosition op : obj.getPositions()) {
            known.put(op.getId(), op);
        }

        JSONArray posarray = jsonobj.getJSONArray("positions");
        List<OrderPosition> inserts = new ArrayList<>();
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
            }
            if (known.containsKey(jsonid)) {
                known.remove(jsonid);
                if (!JSONUtils.similar(posjson, old)) {
                    updatePositionObject(posobj, posjson);
                    store.update(posobj);
                }
            } else {
                updatePositionObject(posobj, posjson);
                inserts.add(posobj);
            }
        }
        store.insert(inserts);
        store.delete(known.values());
    }

    @Override
    protected JSONObject downloadPage(String url, boolean isFirstPage) throws ApiException, ResourceNotModified {
        ResourceLastModified resourceLastModified = store.select(ResourceLastModified.class)
                .where(ResourceLastModified.RESOURCE.eq("orders"))
                .limit(1)
                .get().firstOrNull();
        if (resourceLastModified == null) {
            resourceLastModified = new ResourceLastModified();
            resourceLastModified.setResource("orders");
        } else {
            if (url.contains("?")) {
                url += "&modified_since=" + resourceLastModified.getLast_modified();
            } else {
                url += "?modified_since=" + resourceLastModified.getLast_modified();
            }
        }

        PretixApi.ApiResponse apiResponse = api.fetchResource(url);
        if (isFirstPage && apiResponse.getResponse().header("X-Page-Generated") != null) {
            resourceLastModified.setLast_modified(apiResponse.getResponse().header("X-Page-Generated"));
            store.upsert(resourceLastModified);
        }
        return apiResponse.getData();
    }

    @Override
    Iterator<Order> getKnownObjectsIterator() {
        return store.select(Order.class)
                .where(Order.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
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
