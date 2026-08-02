import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val amapApiKey = providers.gradleProperty("AMAP_API_KEY")
    .orElse(localProperties.getProperty("AMAP_API_KEY", ""))
    .get()
val escapedAmapApiKey = amapApiKey.replace("\\", "\\\\").replace("\"", "\\\"")

val amapWebApiKey = providers.gradleProperty("AMAP_WEB_API_KEY")
    .orElse(localProperties.getProperty("AMAP_WEB_API_KEY", ""))
    .get()
val escapedAmapWebApiKey = amapWebApiKey.replace("\\", "\\\\").replace("\"", "\\\"")

val qweatherProjectId = providers.gradleProperty("QWEATHER_PROJECT_ID")
    .orElse(localProperties.getProperty("QWEATHER_PROJECT_ID", ""))
    .get()
val escapedQweatherProjectId = qweatherProjectId.replace("\\", "\\\\").replace("\"", "\\\"")

val qweatherKeyId = providers.gradleProperty("QWEATHER_KEY_ID")
    .orElse(localProperties.getProperty("QWEATHER_KEY_ID", ""))
    .get()
val escapedQweatherKeyId = qweatherKeyId.replace("\\", "\\\\").replace("\"", "\\\"")

val qweatherPrivateKey = providers.gradleProperty("QWEATHER_PRIVATE_KEY")
    .orElse(localProperties.getProperty("QWEATHER_PRIVATE_KEY", ""))
    .get()
// PEM 私钥可能含换行符，需转义为 \n 以正确嵌入 BuildConfig 字符串字面量
val escapedQweatherPrivateKey = qweatherPrivateKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "")

val qweatherApiHost = providers.gradleProperty("QWEATHER_API_HOST")
    .orElse(localProperties.getProperty("QWEATHER_API_HOST", "https://devapi.qweatherapi.com/"))
    .get()

android {
    namespace = "com.skypulse.weather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skypulse.weather"
        minSdk = 26
        targetSdk = 35
        versionCode = 984
        versionName = "3.4.53"

        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        buildConfigField("String", "AMAP_API_KEY", "\"$escapedAmapApiKey\"")
        buildConfigField("String", "AMAP_WEB_API_KEY", "\"$escapedAmapWebApiKey\"")
        buildConfigField("String", "QWEATHER_PROJECT_ID", "\"$escapedQweatherProjectId\"")
        buildConfigField("String", "QWEATHER_KEY_ID", "\"$escapedQweatherKeyId\"")
        buildConfigField("String", "QWEATHER_PRIVATE_KEY", "\"$escapedQweatherPrivateKey\"")
        buildConfigField("String", "QWEATHER_API_HOST", "\"${qweatherApiHost.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-keystore.jks")
            storePassword = "weather123"
            keyAlias = "weather-app"
            keyPassword = "weather123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }



    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.animation)


    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.okhttp.logging)
    implementation(libs.bouncycastle)

    // Location
    implementation(libs.play.services.location)
    implementation(libs.amap.location)
    implementation(libs.accompanist.permissions)

    // UI
    implementation(libs.androidsvg)
    implementation(libs.browser)
    implementation(libs.haze)
    implementation(libs.splashscreen)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security — EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

