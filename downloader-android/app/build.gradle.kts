import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// مفاتيح التوقيع تُقرأ من keystore.properties (غير مرفوع إلى Git).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

// للاختبار على محاكي x86_64 فقط:  ./gradlew assembleRelease -PemulatorAbi
val emulatorAbi = providers.gradleProperty("emulatorAbi").isPresent

android {
    namespace = "com.downloader.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.downloader.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"
        // مهلة تحليل الرابط: 4 دقائق؛ أطول في نسخة المحاكي البرمجي لأنه أبطأ بعشرات المرات.
        buildConfigField("long", "ANALYZE_TIMEOUT_MS", if (emulatorAbi) "1800000L" else "240000L")
        // لاختبار مسار الترجمة الاحتياطي على المحاكي فقط (فارغ في النسخة النهائية).
        buildConfigField("String", "SUB_TEST_URL", if (emulatorAbi) "\"http://10.0.2.2:8000/multi/s_en.vtt\"" else "\"\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            if (emulatorAbi) abiFilters += "x86_64"
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // بدون keystore.properties يُوقَّع بمفتاح debug حتى ينجح البناء بأمر واحد.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
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

    packaging {
        // مطلوب لـ youtubedl-android: ملفات python/ffmpeg تُستخرج وتُنفَّذ من nativeLibraryDir.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/*.kotlin_module") }
    }
}

dependencies {
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.coil-kt.coil3:coil-video:3.6.3")
    implementation("me.saket.telephoto:zoomable-image-coil3:0.19.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
