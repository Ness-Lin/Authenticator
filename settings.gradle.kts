pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Authenticator"
include(
    ":app",
    ":common:basic",
    ":arch:api", ":arch:impl", ":arch:ui",
    ":feature:token:api", ":feature:token:impl", ":feature:token:ui",
    ":feature:enrollment:api", ":feature:enrollment:impl", ":feature:enrollment:ui",
    ":feature:transfer:api", ":feature:transfer:impl", ":feature:transfer:ui",
    ":feature:security:api", ":feature:security:impl", ":feature:security:ui",
)
