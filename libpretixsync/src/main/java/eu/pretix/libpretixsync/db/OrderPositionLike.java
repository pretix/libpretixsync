package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

public interface OrderPositionLike extends RemoteObject {
    JSONObject getJSON() throws JSONException;
    String getAttendeeName();
    String getAttendeeEmail();
}
