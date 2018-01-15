package eu.pretix.libpretixsync.db;

import io.requery.*;

import java.util.List;

@Entity(cacheable = false)
public class AbstractItem {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    private String name;

    @ManyToMany
    List<Question> questions;
}
