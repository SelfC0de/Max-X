-keep class ru.maxx.app.** { *; }
-keep class org.msgpack.** { *; }
-keep class net.jpountz.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes *Annotation*
-keepclassmembers class * implements android.os.Parcelable { static ** CREATOR; }

# msgpack — использует sun.nio.ch.DirectBuffer (JVM internal, нет на Android)
-dontwarn sun.nio.ch.DirectBuffer
-dontwarn org.msgpack.core.buffer.DirectBufferAccess
-keep class org.msgpack.** { *; }
-keep class net.jpountz.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
