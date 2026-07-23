# --- GENERAL KOTLIN & COROUTINES ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, SourceFile, LineNumberTable
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- KOTLINX SERIALIZATION ---
# Keep all @Serializable classes and their generated serializers
-keep class com.grepho.cozydoubling.core.** { *; }
-keep class com.grepho.cozydoubling.features.** { *; }
-keepclassmembers class com.grepho.cozydoubling.** {
    *** Companion;
    *** serializer(...);
}
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# --- SUPABASE & KTOR ---
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
-keep class io.github.jan_tennert.supabase.** { *; }
-dontwarn io.ktor.network.sockets.SocketBase
-dontwarn kotlinx.serialization.internal.ClassValueReferences
-dontwarn java.lang.management.**

# --- REVENUECAT ---
# RevenueCat includes its own consumer rules, but broad keep is safest for reflection
-keep class com.revenuecat.purchases.** { *; }
-keep class androidx.lifecycle.DefaultLifecycleObserver
-keep class androidx.startup.InitializationProvider

# --- OKHTTP & OKIO ---
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- GOOGLE IDENTITY / CREDENTIAL MANAGER ---
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep public class com.google.android.gms.auth.api.signin.** { *; }
-keep class androidx.credentials.** { *; }
