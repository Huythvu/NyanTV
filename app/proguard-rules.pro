# App Core & Lifecycle Protection
-keep class com.nyantv.** { *; }
-keep interface com.nyantv.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }

# Keep Attributes and Signatures intact (Crucial for Type matching)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes KotlinBlankLines,LineNumberTable,SourceFile

# Kotlin Standard Library & Reflect
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.**

# Injekt Dependency Injection
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keepclassmembers class * {
    @uy.kohesive.injekt.** *;
}

# Tachiyomi / Aniyomi Core Ecosystem & Extension Loader
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class dalvik.system.** { *; }

# HARD PROTECTION: Prevent obfuscation of ALL Source structures & signatures
-keep class eu.kanade.tachiyomi.animesource.** { *; }
-keep interface eu.kanade.tachiyomi.animesource.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.animesource.** { *; }

-keep class eu.kanade.tachiyomi.source.** { *; }
-keep interface eu.kanade.tachiyomi.source.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.source.** { *; }

-keep class eu.kanade.tachiyomi.animeextension.** { *; }
-keep class eu.kanade.tachiyomi.multisrc.** { *; }

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }
-dontobfuscate class okhttp3.**

-keep class okio.** { *; }
-keep interface okio.** { *; }
-keepclassmembers class okio.** { *; }

# Networking & JSON Data
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.jsoup.** { *; }
-keep class androidx.datastore.** { *; }

# Suppress Warnings that block compilation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.**
-dontwarn androidx.datastore.**