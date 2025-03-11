plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
//    kotlin("plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.example.umechika"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.umechika"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
        packaging {
            resources {
                excludes += setOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/INDEX.LIST"
                )
            }
        }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
//    implementation ("com.mapbox.navigation:core:2.20.2")
    implementation ("com.mapbox.navigation:android:2.20.2"){
        exclude(group = "com.mapbox.common", module = "okhttp")
    }
//
    implementation("com.mapbox.maps:android:10.19.0"){
        exclude(group = "com.mapbox.common", module = "okhttp")
    }

//    implementation("com.mapbox.extension:maps-compose:11.10.2"){
//        exclude(group = "com.mapbox.common", module = "okhttp")
//    }
    configurations.all {
        exclude(group = "com.mapbox.common", module = "okhttp")
    }
//    implementation ("com.mapbox.navigation:ui-dropin:2.20.2")
//
//    implementation("com.mapbox.navigationcore:android:3.8.0-beta.1")
//        implementation("com.mapbox.navigationcore:navigation:3.8.0-beta.1")
//    implementation("com.mapbox.navigationcore:copilot:3.8.0-beta.1")
//    implementation("com.mapbox.navigationcore:ui-maps:3.8.0-beta.1")
//    implementation("com.mapbox.navigationcore:voice:3.8.0-beta.1")
//    implementation("com.mapbox.navigationcore:tripdata:3.8.0-beta.1")
//    implementation("com.mapbox.navigationcore:android:3.8.0-beta.1")
//    implementation("com.mapbox.navigationcore:ui-components:3.8.0-beta.1")
//    val nav_version = "2.8.8"
//
//
//    // Jetpack Compose integration
//    implementation("androidx.navigation:navigation-compose:$nav_version")
//
//    // Views/Fragments integration
//    implementation("androidx.navigation:navigation-fragment:$nav_version")
//    implementation("androidx.navigation:navigation-ui:$nav_version")
//
//    // Feature module support for Fragments
//    implementation("androidx.navigation:navigation-dynamic-features-fragment:$nav_version")
//
//    // Testing Navigation
//    androidTestImplementation("androidx.navigation:navigation-testing:$nav_version")
//
//    // JSON serialization library, works with the Kotlin serialization plugin
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}