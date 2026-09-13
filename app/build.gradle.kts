plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.authenticator"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.authenticator"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":common:basic"))
    implementation(project(":arch:api"))
    implementation(project(":arch:impl"))
    implementation(project(":arch:ui"))
    implementation(project(":feature:token:api"))
    implementation(project(":feature:token:impl"))
    implementation(project(":feature:token:ui"))
    implementation(project(":feature:enrollment:api"))
    implementation(project(":feature:enrollment:impl"))
    implementation(project(":feature:enrollment:ui"))
    implementation(project(":feature:transfer:api"))
    implementation(project(":feature:transfer:impl"))
    implementation(project(":feature:transfer:ui"))
    implementation(project(":feature:security:api"))
    implementation(project(":feature:security:impl"))
    implementation(project(":feature:security:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
