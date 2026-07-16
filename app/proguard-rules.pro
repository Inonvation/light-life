# ========================
# Moshi (反射版 - KotlinJsonAdapterFactory)
# ========================
# 保留 Kotlin 反射元数据（KotlinJsonAdapterFactory 需要）
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# 保留所有 Moshi 注解
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonQualifier <methods>;
}

# 保留 KotlinJsonAdapterFactory
-keep class com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory { *; }

# 保留项目中的数据模型类（Moshi 反射需要访问构造函数和字段）
-keep class com.example.devicecontrol.data.** { *; }

# ========================
# Retrofit
# ========================
# 保留 Retrofit 接口方法
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# 保留 Retrofit 的注解和签名
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# ========================
# OkHttp
# ========================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn okhttp3.internal.platform.BouncyCastlePlatform
-dontwarn okhttp3.internal.platform.OpenJSSEPlatform

# Material Icons Extended — R8 自动裁剪未引用的图标，不需要手动 keep

# ========================
# Kotlin Coroutines
# ========================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ========================
# Security Crypto
# ========================
-keep class androidx.security.crypto.** { *; }

# ========================
# 通用优化
# ========================
# 移除 release 包中的日志调用
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
