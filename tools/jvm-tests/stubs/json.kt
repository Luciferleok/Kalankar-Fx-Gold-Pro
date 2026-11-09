package kotlinx.serialization.json
sealed class JsonElement
class JsonPrimitive(val raw: String?, val isString: Boolean = false) : JsonElement() { val content: String get() = raw ?: "null" }
class JsonObject(val m: Map<String, JsonElement>) : JsonElement(), Map<String, JsonElement> by m
class JsonArray(val l: List<JsonElement>) : JsonElement(), List<JsonElement> by l
object JsonNull : JsonElement()
class JsonBuilder { var ignoreUnknownKeys = false; var isLenient = false; var explicitNulls = true; var coerceInputValues = false }
open class Json(val b: JsonBuilder = JsonBuilder()) {
    companion object Default : Json()
    fun parseToJsonElement(s: String): JsonElement = throw UnsupportedOperationException("stub")
    inline fun <reified T> decodeFromString(s: String): T = throw UnsupportedOperationException("stub")
    inline fun <reified T> encodeToString(v: T): String = throw UnsupportedOperationException("stub")
}
fun Json(block: JsonBuilder.() -> Unit): Json = Json(JsonBuilder().apply(block))
val JsonElement.jsonPrimitive: JsonPrimitive get() = this as JsonPrimitive
val JsonElement.jsonObject: JsonObject get() = this as JsonObject
val JsonElement.jsonArray: JsonArray get() = this as JsonArray
val JsonPrimitive.double: Double get() = content.toDouble()
val JsonPrimitive.doubleOrNull: Double? get() = content.toDoubleOrNull()
val JsonPrimitive.int: Int get() = content.toInt()
val JsonPrimitive.intOrNull: Int? get() = content.toIntOrNull()
val JsonPrimitive.long: Long get() = content.toLong()
val JsonPrimitive.longOrNull: Long? get() = content.toLongOrNull()
val JsonPrimitive.contentOrNull: String? get() = raw
val JsonPrimitive.boolean: Boolean get() = content.toBoolean()
val JsonPrimitive.booleanOrNull: Boolean? get() = content.toBooleanStrictOrNull()
