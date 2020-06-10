package eu.pretix.libpretixsync.db;

import io.requery.Column;
import org.json.JSONException;
import org.json.JSONObject;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public abstract class AbstractSettings implements RemoteObject {
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

    public JSONObject getFiscalJSON() throws JSONException {
        JSONObject jsonobj = getJSON();
        JSONObject j = new JSONObject();
        j.put("slug", slug);
        j.put("name", jsonobj.optString("invoice_address_from_name"));
        j.put("address", jsonobj.optString("invoice_address_from"));
        j.put("zipcode", jsonobj.optString("invoice_address_from_zipcode"));
        j.put("city", jsonobj.optString("invoice_address_from_city"));
        j.put("country", jsonobj.optString("invoice_address_from_country"));
        j.put("tax_id", jsonobj.optString("invoice_address_from_tax_id"));
        j.put("vat_id", jsonobj.optString("invoice_address_from_vat_id"));
        return j;
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

}
