import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load signing properties safely
val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.onder1e.usbpdbs"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            storePassword = keystoreProperties.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = keystoreProperties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = keystoreProperties.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.onder1e.usbpdbs"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Optional: specify debug signing if you want to keep them distinct
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "USB_PD_Bypass_Script_v${versionName}.apk"
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
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
