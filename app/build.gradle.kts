plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.fitbitsync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.fitbitsync"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        manifestPlaceholders["appAuthRedirectScheme"] = "com.example.fitbitsync"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    
    // AppAuth for Fitbit
    implementation("net.openid:appauth:0.11.1")
    
    // MSAL for Microsoft Graph
    implementation("com.microsoft.identity.client:msal:4.10.0")
    
    // MS Graph API SDK
    implementation("com.microsoft.graph:microsoft-graph:6.0.1")
    implementation("com.azure:azure-identity:1.11.0")
}
