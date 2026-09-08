plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
dependencies {
    api(project(":arch:api"))
    api(project(":common:basic"))
}
