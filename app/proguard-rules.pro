# SkyPulse ProGuard / R8 规则
#
# 日志剥离：文件底部的 -assumenosideeffects 规则让 R8 在 Release 构建中直接移除
# 所有 Log.v/d/i/w/e 调用（含参数字符串拼接），减小包体并消除运行时日志开销。

# === Disable R8 optimization (breaks Moshi generic type resolution) ===
-dontoptimize

# === Moshi JSON serialization ===
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# Keep ALL model data classes and their members
-keep class com.ridgecast.weather.model.** { *; }

# Keep ALL Moshi classes (required for codegen adapter discovery via @JsonClass annotation)
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# === Kotlin ===
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn kotlin.**

# === Retrofit ===
-keepattributes Exceptions
# Retrofit 注解方法必须保留（@GET/@POST 等）；整库 keep 已移除，其余由 Retrofit 自带 consumer 规则覆盖
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Keep API interface with ALL members and signatures (R8 strips Signature from interfaces)
-keep,allowobfuscation class com.ridgecast.weather.data.remote.** { *; }
-keepclassmembers,allowobfuscation class com.ridgecast.weather.data.remote.** { *; }
-dontwarn retrofit2.**

# === OkHttp ===
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# === BouncyCastle (Ed25519 JWT signing & KeyPair management for QWeather) ===
-keep class org.bouncycastle.crypto.generators.Ed25519** { *; }
-keep class org.bouncycastle.crypto.params.Ed25519** { *; }
-keep class org.bouncycastle.crypto.signers.Ed25519** { *; }
-keep class org.bouncycastle.crypto.util.PrivateKeyInfoFactory { *; }
-keep class org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory { *; }
# org.bouncycastle.asn1.** 整库 keep 已移除：Ed25519 签名链路只需上面几个精确类，ASN.1 运行时按需解析
-dontwarn org.bouncycastle.**

# === Google Play Services ===
# GMS 各组件自带 consumer ProGuard 规则，整库 keep 已移除
-dontwarn com.google.android.gms.**

# === Kotlin Coroutines ===
# kotlinx-coroutines 自带 consumer 规则，整库 keep 已移除
-dontwarn kotlinx.coroutines.**

# === AndroidX ===
# AndroidX 各库自带 consumer ProGuard 规则，整库 keep 已移除
-dontwarn androidx.lifecycle.**
-dontwarn androidx.activity.**

# === 日志 ===
# 注意：-dontoptimize（Moshi 泛型解析需要）会关闭优化器，因此
# -assumenosideeffects 日志剥离规则在本配置下不生效。若未来移除 -dontoptimize，
# 可在此处加回 -assumenosideeffects class android.util.Log { ... } 以剥离日志。

# === Hilt / Dagger ===
# Hilt 代码生成依赖这些入口类，保留整库 keep 作为 Dagger 自带规则之外的双保险
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
