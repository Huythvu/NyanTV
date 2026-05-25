# App Core, Lifecycle & Reflection
-keep class com.nyantv.** { *; }
-keep interface com.nyantv.** { *; }
-keep class androidx.lifecycle.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlin Standard Library & Type Metadata (Crucial for Extensions)
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.jvm.internal.TypeReference { *; }
-keep class kotlin.text.Regex { *; }
-keep class kotlin.Pair { *; }
-keep class kotlin.ranges.** { *; }
-keep class java.lang.reflect.ParameterizedType { *; }
-dontwarn kotlin.**

# Tachiyomi / Aniyomi Core & Extension Loader
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class dalvik.system.** { *; }
-keepattributes SourceFile,LineNumberTable

# Fix for Extension Data Models (Prevents JSON parsing errors)
-keep class eu.kanade.tachiyomi.animeextension.** { *; }
-keep class eu.kanade.tachiyomi.multisrc.** { *; }

# Injekt Dependency Injection
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keepclassmembers class * {
    @uy.kohesive.injekt.** *;
}

# RxJava & Coroutines
-keep class rx.** { *; }
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