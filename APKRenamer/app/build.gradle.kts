plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yitian.apkrenamer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yitian.apkrenamer"
        minSdk = 29           // Android 10+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false   // 关键：不要混淆，否则 ARSCLib 的反射会挂
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
    }

    // 打包大依赖时可能撞这些
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    // AndroidX 基本套件
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ====== APK 修改与签名核心 ======
    // ARSCLib：解析/修改 resources.arsc 与二进制 AndroidManifest
    implementation("com.github.REAndroid:ARSCLib:1.3.4")
    // APKEditor：在 ARSCLib 之上提供 ApkModule 与重打包能力
    implementation("com.github.REAndroid:APKEditor:1.4.1")

    // apksig：Google 官方 APK 签名库，纯 Java
    implementation("com.android.tools.build:apksig:8.5.2")

    // BouncyCastle：生成自签 keystore（X.509 + RSA）
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}
