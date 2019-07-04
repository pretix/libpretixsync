package eu.pretix.libpretixsync.test

import eu.pretix.libpretixsync.sync.FileStorage
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class FakeFileStorage : FileStorage {
    override fun contains(filename: String?): Boolean {
        return false
    }

    override fun writeStream(filename: String?): OutputStream {
        return ByteArrayOutputStream()
    }

    override fun delete(filename: String?) {
    }

}