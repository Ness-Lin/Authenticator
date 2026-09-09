// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

val moduleDependencies = buildMap<String, Set<String>> {
    put(":common:basic", emptySet())
    put(":arch:api", setOf(":common:basic"))
    put(":arch:impl", setOf(":arch:api", ":common:basic"))
    put(":arch:ui", setOf(":arch:api", ":common:basic"))
    for (feature in listOf("token", "enrollment", "transfer", "security")) {
        val shared = setOf(":arch:api", ":common:basic")
        put(":feature:$feature:api", shared)
        val additional = when (feature) {
            "token" -> setOf(":feature:security:api")
            "enrollment", "transfer" -> setOf(":feature:token:api", ":feature:security:api")
            else -> emptySet()
        }
        put(":feature:$feature:impl", shared + ":feature:$feature:api" + additional)
        val uiAdditional = if (feature == "security") emptySet() else setOf(":feature:security:api")
        put(":feature:$feature:ui", shared + ":arch:ui" + ":feature:$feature:api" + uiAdditional)
    }
    put(":app", keys.toSet())
}

val checkModuleDependencies = tasks.register("checkModuleDependencies") {
    group = "verification"
    description = "Checks all project dependency configurations against the architecture whitelist."
    // Project models are inspected without resolving external artifacts.
    notCompatibleWithConfigurationCache("Inspects project dependency declarations at execution time")
    doLast {
        val violations = mutableListOf<String>()
        val graph = mutableMapOf<String, MutableSet<String>>()
        for (module in allprojects) {
            for (configuration in module.configurations) {
                for (dependency in configuration.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>()) {
                    val target = dependency.path
                    // Android test classpaths include the project under test.
                    if (target == module.path && configuration.isCanBeResolved &&
                        configuration.name.matches(Regex(".*(?:AndroidTest|UnitTest).*Classpath"))) {
                        continue
                    }
                    graph.getOrPut(module.path) { mutableSetOf() }.add(target)
                    if (target !in moduleDependencies[module.path].orEmpty()) {
                        violations += "${module.path}:${configuration.name} -> $target"
                    }
                }
            }
        }
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        fun visit(path: String) {
            check(visiting.add(path)) { "Circular module dependency at $path" }
            if (visited.add(path)) graph[path].orEmpty().forEach { visit(it) }
            visiting.remove(path)
        }
        graph.keys.forEach { visit(it) }
        check(violations.isEmpty()) {
            "Module dependency whitelist violations:\n${violations.joinToString("\n")}"
        }
        logger.lifecycle("Module dependency whitelist passed (${moduleDependencies.size} modules).")
    }
}

tasks.named("check") { dependsOn(checkModuleDependencies) }
subprojects {
    tasks.matching { it.name == "check" || it.name == "preBuild" || it.name == "compileKotlin" }
        .configureEach { dependsOn(checkModuleDependencies) }
}
