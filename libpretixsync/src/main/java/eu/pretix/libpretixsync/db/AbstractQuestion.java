package eu.pretix.libpretixsync.db;

import eu.pretix.libpretixsync.check.QuestionType;
import io.requery.*;

import java.util.List;

@Entity(cacheable = false)
public class AbstractQuestion {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String question;

    public QuestionType type;

    public boolean required;

    public Long position;

    @OneToMany
    List<QuestionOption> options;

    @ManyToMany
    @JunctionTable
    List<Item> items;
}
