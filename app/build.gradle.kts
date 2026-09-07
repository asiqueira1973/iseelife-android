// app/build.gradle.kts — módulo do aplicativo (épico 8.0).
// App phone-only, retrato apenas, offline (spec seções 3/4/17). minSdk 26 (cobre TileService,
// App Shortcuts e OpenGL ES 2.0). Compose + CameraX + Room + Play Billing.

import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

// Assinatura de release (épico de publicação). Lê o keystore.properties da raiz (NÃO versionado);
// se não existir, o build de release sai sem assinatura (útil em dev). Formato do arquivo:
//   storeFile=/caminho/iseelife-upload.jks
//   storePassword=...
//   keyAlias=iseelife
//   keyPassword=...
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.dablab.iseelife"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dablab.iseelife"   // mesmo namespace de marca do iOS (assunção 24.1)
        minSdk = 26
        targetSdk = 36
        versionCode = 6          // 6 = captura em ALTA resolução (~12MP via ImageCapture; conserto do ~2MP do frame-grab)
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"       // permite debug e release lado a lado
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Usa a chave de upload se o keystore.properties existir (senão sai sem assinatura).
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true   // p/ BuildConfig.DEBUG (simulador de compra só em debug)
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (via BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // CameraX (épico 8.3+)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Room (épico 8.1)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (reservado)
    implementation(libs.androidx.datastore.preferences)

    // Play Billing (épico 8.7)
    implementation(libs.billing.ktx)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.room.testing)
}
