package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.TicketLayout;
import eu.pretix.libpretixsync.utils.HashUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class TicketLayoutSyncAdapter extends BaseDownloadSyncAdapter<TicketLayout, Long> {
    public TicketLayoutSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api) {
        super(store, fileStorage, eventSlug, api);
    }

    @Override
    public void updateObject(TicketLayout obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setIs_default(jsonobj.getBoolean("default"));
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setJson_data(jsonobj.toString());

        // We need to reverse a complicated relationship here. We need to make sure that this
        // ticket layout is assigned to all items that it should be to, but those assignments
        // can differ by sales channel, so we'd like to give preference to assignments set for
        // the "pretixpos" channel but fall back to the "web" channel if there is none.

        // pretixpos_assigned will  be a set of all item IDs where we know the ticket layout
        // has been *specifically* set for the pretixpos channel.
        // TODO: Make the name of that channel configurable for this library
        Set<Long> pretixpos_assigned = new HashSet<>();

        // itemids will be a list of all item IDs where we *could* assign this to through either
        // channel
        List<Long> itemids = new ArrayList<>();

        // Iterate over all items this layout is assigned to
        JSONArray assignmentarr = jsonobj.getJSONArray("item_assignments");
        for (int i = 0; i < assignmentarr.length(); i++) {
            Long item = assignmentarr.getJSONObject(i).getLong("item");
            String sc = assignmentarr.getJSONObject(i).optString("sales_channel", "web");

            if (!sc.equals("web") && !sc.equals("pretixpos")) {
                // This is some channel we don't care about, e.g. pretixPOS
                continue;
            }
            // This is either the web or the pretixpos channel, so we'll absolutely look at this
            // item again
            itemids.add(item);

            // If we haven't seen this item for the pretixpos channel, and this is the pretixpos
            // channel, add it to the set
            if (!pretixpos_assigned.contains(item) && sc.equals("pretixpos")) {
                pretixpos_assigned.add(item);
            }
        }

        // Get all items that we *might* want to assign this to
        List<Item> items = store.select(Item.class).where(
                Item.SERVER_ID.in(itemids)
        ).get().toList();
        for (Item item : items) {
            if (item.getTicket_layout_id() != null) {
                if (item.getTicket_layout_id().equals(obj.getServer_id())) {
                    // This item is already assigned to this layout, we can leave it as it is
                    continue;
                }

                // This item is currently assigned to a different layout, let's look at that layout
                TicketLayout previous = store.select(TicketLayout.class).where(
                        TicketLayout.SERVER_ID.eq(item.getTicket_layout_id())
                ).get().firstOrNull();
                if (previous != null && !pretixpos_assigned.contains(item.getServer_id())) {
                    continue;
                }
                // EITHER the layout this was previously assigned to does not exist any more
                // OR we looked at this just for the default case but we only have it assigned on the "web" channel
            }
            // else { This item currently is not assignedto anything, so assign it with this in any case }

            // Assign this item to this layout
            item.setTicket_layout_id(obj.getServer_id());
            store.update(item, Item.TICKET_LAYOUT_ID);
        }

        // Look if there are any items in the local database assigned to this layout even though
        // they should not be any more.
        items = store.select(Item.class).where(
                Item.SERVER_ID.notIn(itemids).and(
                        Item.TICKET_LAYOUT_ID.eq(obj.getServer_id())
                )
        ).get().toList();
        for (Item item : items) {
            item.setTicket_layout_id(null);
            store.update(item, Item.TICKET_LAYOUT_ID);
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
    Iterator<TicketLayout> getKnownObjectsIterator() {
        return store.select(TicketLayout.class)
                .where(TicketLayout.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "ticketlayouts";
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
