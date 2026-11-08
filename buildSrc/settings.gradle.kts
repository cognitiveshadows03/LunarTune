pluginManagement {
    repositories {
        // Mirror only the 60 MB kotlin-compiler-embeddable jar off JetBrains Space.
        // Maven Central rate-limits this artifact on GitHub Actions runners and
        // redirects it to the GitHub release asset CDN, which has been returning
        // 504 Gateway Time-out under load. Every other Kotlin artifact (stdlib,
        // reflect, plugin, plugin markers) is small enough to resolve from Maven
        // Central or the plugin portal, so the mirror is narrowed to this single
        // module to keep the dev-channel exposure minimal. Verified: the jar bytes
        // are identical to Maven Central (SHA-256 pinned in
        // gradle/kotlin-compiler-embeddable-2.4.0.sha256 and checked in CI).
        maven {
            name = "JetBrainsKotlin"
            setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
            content {
                includeModule("org.jetbrains.kotlin", "kotlin-compiler-embeddable")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
