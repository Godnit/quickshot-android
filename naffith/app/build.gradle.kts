plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.naffith"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.naffith"
        minSdk = 27
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // يعتمد التطبيق على واجهات أندرويد الأساسية لتقليل حجم APK.
}
