package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.TicketLayout;
import eu.pretix.libpretixsync.utils.HashUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;
import io.requery.util.CloseableIterator;

public class TicketLayoutSyncAdapter extends BaseDownloadSyncAdapter<TicketLayout, Long> {
    String salesChannel = "pretixpos";

    public TicketLayoutSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, String syncCycleId, String salesChannel, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, syncCycleId, feedback);
        this.salesChannel = salesChannel;
    }

    @Override
    public void updateObject(TicketLayout obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setIs_default(jsonobj.getBoolean("default"));
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setJson_data(jsonobj.toString());

        // itemids will be a list of all item IDs where we *could* assign this to through either
        // channel
        List<Long> itemids_web = new ArrayList<>();
        List<Long> itemids_pretixpos = new ArrayList<>();

        // Iterate over all items this layout is assigned to
        JSONArray assignmentarr = jsonobj.getJSONArray("item_assignments");
        for (int i = 0; i < assignmentarr.length(); i++) {
            Long item = assignmentarr.getJSONObject(i).getLong("item");
            String sc = assignmentarr.getJSONObject(i).optString("sales_channel", "web");
            if (sc == null) {
                sc = "web";
            }

            if (sc.equals("web")) {
                itemids_web.add(item);

                Item itemobj = store.select(Item.class).where(
                        Item.SERVER_ID.eq(item)
                ).get().firstOrNull();
                if (itemobj != null) {
                    itemobj.setTicket_layout_id(obj.getServer_id());
                    store.update(itemobj, Item.TICKET_LAYOUT_ID);
                }
            } else if (sc.equals(salesChannel)) {
                itemids_pretixpos.add(item);

                Item itemobj = store.select(Item.class).where(
                        Item.SERVER_ID.eq(item)
                ).get().firstOrNull();
                if (itemobj != null) {
                    itemobj.setTicket_layout_pretixpos_id(obj.getServer_id());
                    store.update(itemobj, Item.TICKET_LAYOUT_PRETIXPOS_ID);
                }
            }
        }

        List<Item> items_to_remove_web;
        if (!itemids_web.isEmpty()) {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            items_to_remove_web = store.select(Item.class).where(
                    Item.SERVER_ID.notIn(itemids_web).and(
                            Item.TICKET_LAYOUT_ID.eq(obj.getServer_id())
                    )
            ).get().toList();
        } else {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            items_to_remove_web = store.select(Item.class).where(
                    Item.TICKET_LAYOUT_ID.eq(obj.getServer_id())
            ).get().toList();
        }
        for (Item item : items_to_remove_web) {
            item.setTicket_layout_id(null);
            store.update(item, Item.TICKET_LAYOUT_ID);
        }

        List<Item> items_to_remove_pretixpos;
        if (!itemids_pretixpos.isEmpty()) {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            items_to_remove_pretixpos = store.select(Item.class).where(
                    Item.SERVER_ID.notIn(itemids_pretixpos).and(
                            Item.TICKET_LAYOUT_PRETIXPOS_ID.eq(obj.getServer_id())
                    )
            ).get().toList();
        } else {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            items_to_remove_pretixpos = store.select(Item.class).where(
                    Item.TICKET_LAYOUT_PRETIXPOS_ID.eq(obj.getServer_id())
            ).get().toList();
        }
        for (Item item : items_to_remove_pretixpos) {
            item.setTicket_layout_pretixpos_id(null);
            store.update(item, Item.TICKET_LAYOUT_PRETIXPOS_ID);
        }

        String remote_filename = jsonobj.optString("background");
        if (remote_filename != null && remote_filename.startsWith("http")) {
            String hash = HashUtils.toSHA1(remote_filename.getBytes());
            String local_filename = "ticketlayout_" + obj.getServer_id() + "_" + hash + ".pdf";
            if (obj.getBackground_filename() != null && !obj.getBackground_filename().equals(local_filename)) {
                fileStorage.delete(obj.getBackground_filename());
                obj.setBackground_filename(null);
            }
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
                    obj.setBackground_filename(local_filename);
                } catch (ApiException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO: What to do?
                    e.printStackTrace();
                    fileStorage.delete(local_filename);
                }
            } else {
                obj.setBackground_filename(local_filename);
            }
        } else {
            if (obj.getBackground_filename() != null) {
                fileStorage.delete(obj.getBackground_filename());
                obj.setBackground_filename(null);
            }
        }
    }

    @Override
    protected void prepareDelete(TicketLayout obj) {
        super.prepareDelete(obj);
        if (obj.getBackground_filename() != null) {
            fileStorage.delete(obj.getBackground_filename());
        }
    }

    @Override
    public CloseableIterator<TicketLayout> runBatch(List<Long> ids) {
        return store.select(TicketLayout.class)
                .where(TicketLayout.EVENT_SLUG.eq(eventSlug))
                .and(TicketLayout.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    CloseableIterator<Tuple> getKnownIDsIterator() {
        return store.select(TicketLayout.SERVER_ID)
                .where(TicketLayout.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "ticketlayouts";
    }

    protected JSONObject preprocessObject(JSONObject obj) {
        try {
            obj.put("_written_after_20200123", true);  // Trigger full resyncronisation after a bugfix
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(TicketLayout obj) {
        return obj.getServer_id();
    }

    @Override
    TicketLayout newEmptyObject() {
        return new TicketLayout();
    }
}
