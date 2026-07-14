# Moshi
-keep class com.squareup.moshi.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn okio.**
-dontwarn javax.annotation.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep data classes used by Moshi for JSON serialization
-keep class com.example.devicecontrol.data.** { *; }

# Keep Compose
-dontwarn androidx.compose.**

# security-crypto (Tink)
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
