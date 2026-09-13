plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.authenticator.arch.impl"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":arch:api"))
    implementation(project(":common:basic"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
}
