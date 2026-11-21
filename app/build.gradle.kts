plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    // No kotlin.plugin.compose here: that plugin id only exists from Kotlin 2.0.
    // On 1.9.x Compose is enabled via buildFeatures.compose + kotlinCompilerExtensionVersion below.
}

android {
    namespace = "com.amin.vbox"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.amin.vbox"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ---- sing-box libbox (see README "VPN core status") ----
    // libbox is NOT published on Maven Central. It is built from the
    // sing-box source tree with gomobile (scripts/build-libbox.sh) and
    // dropped in as app/libs/libbox.aar. Only when that file is present
    // do we add the libbox source set and the AAR dependency, so a plain
    // CI build (no AAR) still compiles and ships the full UI + config
    // manager, with the tunnel honestly reporting "Core not ready".
    if (file("libs/libbox.aar").exists()) {
        sourceSets {
            getByName("main") {
                java.srcDirs("src/libbox/java")
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // 1.6.3: last kotlinx-serialization runtime compatible with Kotlin 1.9.x
    // (1.7.x requires Kotlin 2.0).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // QR scanning (ZXing). CaptureActivity comes from the library manifest.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")

    if (file("libs/libbox.aar").exists()) {
        implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    }

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
