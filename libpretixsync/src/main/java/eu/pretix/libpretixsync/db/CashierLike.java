package eu.pretix.libpretixsync.db;

public interface CashierLike {
    boolean checkPIN(String pin);

    boolean validOnDevice(String device);

    boolean hasPermission(String permission);

    boolean hasNfcUid();

    Long getNumericId();

    String getUserId();

    String getName();
}
