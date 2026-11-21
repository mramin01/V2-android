plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    // NOTE: org.jetbrains.kotlin.plugin.compose only exists from Kotlin 2.0;
    // on 1.9.x Compose is enabled via buildFeatures.compose + kotlinCompilerExtensionVersion (see app/build.gradle.kts).
}
