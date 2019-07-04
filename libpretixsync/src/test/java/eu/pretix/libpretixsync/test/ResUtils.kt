package eu.pretix.libpretixsync.test

import org.json.JSONObject


fun readResource(filename: String): String? {
    return FakePretixApi::class.java.classLoader.getResource(filename).readText()
}

fun jsonResource(filename: String): JSONObject {
    return JSONObject(readResource(filename))
}
