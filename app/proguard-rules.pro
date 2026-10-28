# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class pl.fleet.monitor.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pl.fleet.monitor.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# osmdroid
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# security-crypto / Tink
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
