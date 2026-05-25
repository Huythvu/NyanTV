-keep class com.nyantv.** { *; }
-keep interface com.nyantv.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Tachiyomi / Aniyomi Extension-System & Loader
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class dalvik.system.** { *; }
-keepattributes SourceFile,LineNumberTable

# 3. Injekt & RxJava
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keep class rx.** { *; }

# 4. Kotlinx Serialization & Coroutines
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.serialization.**

# 5. Jsoup & OkHttp Networking
-keep class org.jsoup.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**