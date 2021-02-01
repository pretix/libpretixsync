package eu.pretix.libpretixsync.db;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Nullable;
import io.requery.ReferentialAction;

@Entity(cacheable = false)
public class AbstractCachedPdfImage {

    @Generated
    @Key
    public Long id;

    @Column
    @Index
    public Long orderposition_id;

    @Index
    public String etag;

    @Index
    public String key;
}
