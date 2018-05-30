package eu.pretix.libpretixsync.db;

import org.json.JSONObject;

import java.math.BigDecimal;

public class ItemVariation {
    private Long server_id;
    private BigDecimal price;
    private boolean active;
    private JSONObject description;
    private JSONObject value;
    private Long position;

    public void setServer_id(Long server_id) {
        this.server_id = server_id;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDescription(JSONObject description) {
        this.description = description;
    }

    public void setValue(JSONObject value) {
        this.value = value;
    }

    public void setPosition(Long position) {
        this.position = position;
    }

    public Long getServer_id() {
        return server_id;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }

    public JSONObject getDescription() {
        return description;
    }

    public JSONObject getValue() {
        return value;
    }

    public Long getPosition() {
        return position;
    }
}
