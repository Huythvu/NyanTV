# App Core & Lifecycle
-keep class com.nyantv.** { *; }
-keep interface com.nyantv.** { *; }
-keep class androidx.lifecycle.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlin Standard Library (Required by Extensions)
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.text.Regex { *; }
-keep class kotlin.Pair { *; }
-keep class kotlin.ranges.** { *; }
-dontwarn kotlin.**

# Tachiyomi / Aniyomi Core & Extension Loader
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class dalvik.system.** { *; }
-keepattributes SourceFile,LineNumberTable

# Injekt & RxJava
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keep class rx.** { *; }

# Serialization & Coroutines
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.serialization.**

# Networking & Data
-keep class org.jsoup.** { *; }
-keep class androidx.datastore.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn androidx.datastore.**