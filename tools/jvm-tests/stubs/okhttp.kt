package okhttp3
import java.util.concurrent.TimeUnit
class MediaType { companion object { fun String.toMediaType(): MediaType = MediaType(); fun String.toMediaTypeOrNull(): MediaType? = MediaType() } }
abstract class RequestBody { companion object { fun String.toRequestBody(t: MediaType? = null): RequestBody = object : RequestBody() {} } }
class ResponseBody { fun string(): String = ""; fun close() {} }
class Headers { operator fun get(n: String): String? = null }
class Response(val code: Int = 0) : java.io.Closeable {
    val body: ResponseBody? = null; val isSuccessful: Boolean get() = code in 200..299; val message: String = ""; val headers = Headers()
    fun header(n: String): String? = null; override fun close() {}
}
class Call { fun execute(): Response = throw java.io.IOException("stub"); fun cancel() {} }
class HttpUrl
class Request private constructor() {
    val url: HttpUrl = HttpUrl()
    class Builder { fun url(u: String) = this; fun header(n: String, v: String) = this; fun addHeader(n: String, v: String) = this
        fun get() = this; fun post(b: RequestBody) = this; fun build(): Request = Request() }
}
class OkHttpClient private constructor() {
    constructor(b: Builder) : this()
    fun newCall(r: Request): Call = Call()
    fun newBuilder(): Builder = Builder()
    class Builder { fun connectTimeout(t: Long, u: TimeUnit) = this; fun readTimeout(t: Long, u: TimeUnit) = this; fun writeTimeout(t: Long, u: TimeUnit) = this
        fun callTimeout(t: Long, u: TimeUnit) = this; fun retryOnConnectionFailure(b: Boolean) = this
        fun connectTimeout(d: java.time.Duration) = this; fun readTimeout(d: java.time.Duration) = this; fun callTimeout(d: java.time.Duration) = this
        fun build(): OkHttpClient = OkHttpClient() }
}
