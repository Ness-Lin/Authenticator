plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.example.authenticator.feature.transfer.ui"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":feature:transfer:api"))
    implementation(project(":feature:security:api"))
    implementation(project(":arch:api"))
    implementation(project(":arch:ui"))
    implementation(project(":common:basic"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
}
