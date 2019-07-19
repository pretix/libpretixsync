package eu.pretix.libpretixsync.db;

import io.requery.Column;
import org.json.JSONException;
import org.json.JSONObject;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public abstract class AbstractInvoicesettings implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public String slug;

    public String name;

    @Column(definition = "TEXT")
    public String address;

    public String zipcode;

    public String city;

    public String country;

    public String tax_id;

    public String vat_id;

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

}
