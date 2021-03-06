package eu.pretix.libpretixsync.sync;

import java.io.FilenameFilter;
import java.io.OutputStream;

public interface FileStorage {
    boolean contains(String filename);
    OutputStream writeStream(String filename);
    void delete(String filename);
    String[] listFiles(FilenameFilter filter);
}
