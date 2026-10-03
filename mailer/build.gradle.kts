plugins {
    id("com.android.application")
}

android {
    namespace = "com.shinji.serena.mail"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shinji.serena.mail"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEVELOPER_NAME", "\"Shinji\"")
        buildConfigField("String", "DEVELOPER_EMAIL", "\"shinjisakiyama@gmail.com\"")
        buildConfigField("String", "COPYRIGHT_NOTICE", "\"Copyright © 2026 Shinji. All Rights Reserved.\"")
        buildConfigField("String", "REPO_RELEASE_TAG", "\"v2.5.1\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("app/serena-release-key.jks")
            storePassword = "serena2026"
            keyAlias = "serena_key"
            keyPassword = "serena2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/{NOTICE.md,LICENSE.md,NOTICE,LICENSE}"
        }
    }
}

dependencies {
    // AndroidX Core & UI
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0-alpha09")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // JavaMail API for Android (IMAP / SMTP for Gmail)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // Google AI Edge / MediaPipe GenAI for Gemma 4 On-Device Inference
    implementation("com.google.mediapipe:tasks-genai:0.10.35")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
}
