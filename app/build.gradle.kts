import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.ankheye.pulsarsync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ankheye.pulsarsync"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        manifestPlaceholders["appAuthRedirectScheme"] = "com.ankheye.pulsarsync"
        
        val fitbitSecret = System.getenv("FITBIT_CLIENT_SECRET")
            ?: localProperties.getProperty("FITBIT_CLIENT_SECRET")
            ?: ""
        buildConfigField("String", "FITBIT_CLIENT_SECRET", "\"$fitbitSecret\"")

        val googleClientId = System.getenv("GOOGLE_CLIENT_ID")
            ?: localProperties.getProperty("GOOGLE_CLIENT_ID")
            ?: ""
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")

        val msalClientId = System.getenv("MSAL_CLIENT_ID")
            ?: localProperties.getProperty("MSAL_CLIENT_ID")
            ?: "5ca54ee8-677a-49ce-806c-7c2d72f5ea77"
        buildConfigField("String", "MSAL_CLIENT_ID", "\"$msalClientId\"")

        val msalSignatureHash = System.getenv("MSAL_SIGNATURE_HASH")
            ?: localProperties.getProperty("MSAL_SIGNATURE_HASH")
            ?: "hpu8za+PiOjX9nNtLCV6XlXHlJI="
        buildConfigField("String", "MSAL_SIGNATURE_HASH", "\"$msalSignatureHash\"")
        
        val msalRedirectUri = System.getenv("MSAL_REDIRECT_URI")
            ?: localProperties.getProperty("MSAL_REDIRECT_URI")
            ?: "msauth://com.ankheye.pulsarsync/7HTh%2Fzk6bA0lTAtnaiUW%2BBguDKs%3D"
        buildConfigField("String", "MSAL_REDIRECT_URI", "\"$msalRedirectUri\"")
        
        // Also add the hash to manifestPlaceholders for the AndroidManifest.xml
        manifestPlaceholders["msalSignatureHash"] = msalSignatureHash
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
        buildConfig = true
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
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    
    // AppAuth for Fitbit
    implementation("net.openid:appauth:0.11.1")
    
    // MSAL for Microsoft Graph
    implementation("com.microsoft.identity.client:msal:8.2.2")

    // Google Identity Services (Play Services Auth)
    implementation("com.google.android.gms:play-services-auth:20.7.0")
}
