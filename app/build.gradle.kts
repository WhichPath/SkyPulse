import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
}

/**
 * Release 签名参数**只从本地读取**，仓库中不再存放任何 keystore 或口令。
 *
 * 来源优先级：Gradle 属性 (-P) > local.properties > 环境变量。
 * 口令缺失时 release 构建立即失败，杜绝产出用空口令签名的包。
 */
fun releaseSecret(name: String): String? {
    val localProps = rootProject.file("local.properties")
    val fromLocal = if (localProps.exists()) {
        val props = Properties()
        localProps.inputStream().use { props.load(it) }
        props.getProperty(name)
    } else null
    return providers.gradleProperty(name).orNull ?: fromLocal ?: System.getenv(name)
}

val releaseStoreFile = releaseSecret("RELEASE_STORE_FILE") ?: "release-keystore.jks"
val releaseStorePassword = releaseSecret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSecret("RELEASE_KEY_ALIAS") ?: "weather-app"
val releaseKeyPassword = releaseSecret("RELEASE_KEY_PASSWORD") ?: releaseStorePassword

android {
    namespace = "com.ridgecast.weather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ridgecast.weather"
        minSdk = 34
        targetSdk = 35
        versionCode = 101
        versionName = "1.0.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 口令缺失时直接失败，绝不产出未签名/弱口令签名的 APK
            signingConfig = if (releaseStorePassword != null) {
                signingConfigs.getByName("release")
            } else {
                throw GradleException(
                    "[RidgeCast] 缺少 release 签名口令。请在 local.properties 配置 " +
                        "RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD，" +
                        "或提供同名环境变量。详见 local.properties.example。"
                )
            }
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
            excludes += "org/bouncycastle/pqc/**"
            excludes += "org/bouncycastle/x509/*.properties"
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

    // UI
    implementation(libs.androidsvg)
    implementation(libs.haze)
    implementation(libs.splashscreen)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

