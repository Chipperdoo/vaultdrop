import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.vaultdrop"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.vaultdrop"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.365"
    }

    // Release signing lives outside the repo, in ~/.gradle/gradle.properties or as
    // ORG_GRADLE_PROJECT_vaultdrop* variables in CI. Without it assembleRelease
    // still runs, and leaves the APK unsigned.
    val releaseStore = providers.gradleProperty("vaultdropStoreFile").orNull
    signingConfigs {
        if (releaseStore != null) create("release") {
            storeFile = file(releaseStore)
            storePassword = providers.gradleProperty("vaultdropStorePassword").get()
            keyAlias = providers.gradleProperty("vaultdropKeyAlias").get()
            // A PKCS12 keystore has one password for the store and the key.
            keyPassword = providers.gradleProperty("vaultdropKeyPassword").orNull ?: storePassword
        }
    }

    buildTypes {
        // A debug build that installs next to the real one, with its own app id,
        // name and settings, so a feature can be tried on the phone without
        // touching the copy that points at the everyday vault.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += "debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Scrib's Open Transcribe contract is AIDL, which AGP 8 and later leave off.
        aidl = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
