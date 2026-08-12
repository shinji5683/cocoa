plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shinji.serena"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shinji.serena"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-alpha01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEVELOPER_NAME", "\"Shinji\"")
        buildConfigField("String", "DEVELOPER_EMAIL", "\"shinjisakiyama@gmail.com\"")
        buildConfigField("String", "DEVELOPER_PHONE", "\"+818094959134\"")
        buildConfigField("String", "DEVELOPER_PHONE_DISPLAY", "\"080-9495-9134\"")
        buildConfigField("String", "COPYRIGHT_NOTICE", "\"Copyright © 2026 Shinji. All Rights Reserved.\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("cocoa-release-key.jks")
            storePassword = "cocoa2026"
            keyAlias = "cocoa_key"
            keyPassword = "cocoa2026"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
