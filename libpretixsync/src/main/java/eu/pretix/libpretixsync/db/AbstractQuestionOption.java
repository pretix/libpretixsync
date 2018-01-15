package eu.pretix.libpretixsync.db;

import io.requery.*;

@Entity(cacheable = false)
public class AbstractQuestionOption {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    @ManyToOne
    public Question question;

    @Column(name = "\"value\"")
    public String value;
}
