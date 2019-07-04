package eu.pretix.libpretixsync.db;

import java.util.Date;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public abstract class AbstractQueuedCheckIn {

    @Key
    @Generated
    public Long id;

    public String event_slug;

    public String secret;

    public String nonce;

    public Date datetime;

    public String answers;

    public Long checkinListId;

    public void generateNonce() {
        this.nonce = NonceGenerator.nextNonce();
    }
}
