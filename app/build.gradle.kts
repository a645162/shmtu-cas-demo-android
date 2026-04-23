plugins {
    id("com.android.application")
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion

println("Using JDK $jdkVersion")

android {
    namespace = "com.khm.shmtu.cas.ocr.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.khm.shmtu.cas.ocr.demo"
        minSdk = 21
        targetSdk = 37
        versionCode = 120
        versionName = "1.2.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = jdkVersion
        targetCompatibility = jdkVersion
    }

    kotlin {
        compileOptions {
            sourceCompatibility = jdkVersion
            targetCompatibility = jdkVersion
        }
    }

    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:5.2.1")
    implementation(project(":shmtu_ocr"))
}
