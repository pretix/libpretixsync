package eu.pretix.libpretixsync.db;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToOne;

@Entity(cacheable = false)
public class AbstractQuestionOption {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    @ManyToOne
    public Question question;

    public String value;
}
