import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

// 공개 저장소라 Firebase 값은 커밋하지 않는다 (spec §4.4, NFR#5).
// local.properties 가 환경변수보다 우선하고, 값이 없으면 빈 문자열로 빌드되어 앱이 미설정 화면을 띄운다.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun config(key: String): String = localProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: System.getenv(key).orEmpty()

android {
    namespace = "io.github.hwanyu365.argos"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.hwanyu365.argos"
        minSdk = 26
        targetSdk = 35
        versionCode = config("ARGOS_VERSION_CODE").toIntOrNull() ?: 1
        versionName = config("ARGOS_VERSION_NAME").ifEmpty { "0.0.0-dev" }

        listOf("API_KEY", "APP_ID", "PROJECT_ID", "DATABASE_URL").forEach {
            buildConfigField("String", "FIREBASE_$it", "\"${config("ARGOS_FIREBASE_$it")}\"")
        }
    }

    signingConfigs {
        create("release") {
            val path = config("ANDROID_KEYSTORE_PATH")
            if (path.isNotEmpty()) {
                storeFile = file(path)
                storePassword = config("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = config("ANDROID_KEY_ALIAS")
                keyPassword = config("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 서명 키가 없으면 unsigned 로 빌드된다 → fork 에서도 assembleRelease 가 실패하지 않음.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
