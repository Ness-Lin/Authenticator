plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.example.authenticator.arch.impl"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {
    implementation(project(":arch:api"))
    implementation(project(":common:basic"))
}
