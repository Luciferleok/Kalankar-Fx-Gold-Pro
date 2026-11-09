package androidx.compose.runtime
class ProvidableCompositionLocal<T>(val d: () -> T)
fun <T> compositionLocalOf(d: () -> T) = ProvidableCompositionLocal(d)
fun <T> staticCompositionLocalOf(d: () -> T) = ProvidableCompositionLocal(d)
