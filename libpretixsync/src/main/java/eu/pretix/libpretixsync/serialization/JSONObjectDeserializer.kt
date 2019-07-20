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

class JSONObjectDeserializer(t: Class<JSONObject>? = null) : StdDeserializer<JSONObject>(t) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): JSONObject {
        val mapper = (p.codec as ObjectMapper)
        val node: JsonNode = mapper.readTree(p)
        return JSONObject(mapper.writeValueAsString(node))
    }
}