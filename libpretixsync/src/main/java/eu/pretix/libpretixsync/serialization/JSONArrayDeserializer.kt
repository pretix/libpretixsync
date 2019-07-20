package eu.pretix.libpretixsync.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.json.JSONArray
import org.json.JSONObject

class JSONArrayDeserializer(t: Class<JSONObject>? = null) : StdDeserializer<JSONArray>(t) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): JSONArray {
        val mapper = (p.codec as ObjectMapper)
        val node: JsonNode = mapper.readTree(p)
        return JSONArray(mapper.writeValueAsString(node))
    }
}