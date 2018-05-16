package eu.pretix.libpretixsync.db;


public class QuestionOption {
    private Long server_id;
    private Long position;
    private String identifier;
    private String value;

    public QuestionOption(Long server_id, Long position, String identifier, String value) {
        this.server_id = server_id;
        this.position = position;
        this.identifier = identifier;
        this.value = value;
    }

    public Long getPosition() {
        return position;
    }

    public Long getServer_id() {
        return server_id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getValue() {
        return value;
    }


    public String toString() {
        return value;
    }
}
