plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.franwan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.franwan"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.04"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Expose API base URL from environment or gradle.properties; avoid relying on local.properties
        val apiBaseUrl = System.getenv("API_BASE_URL")
            ?: (project.findProperty("API_BASE_URL") as String?)
            ?: "https://api.franwan.rf.gd/api/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("androidx.recyclerview:recyclerview:1.2.1")

    // Networking & coroutines
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Secure storage
    implementation("androidx.security:security-crypto:1.1.0")
    
    // PDF processing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}