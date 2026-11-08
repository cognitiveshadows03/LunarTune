pluginManagement {
    repositories {
        // JetBrains Space serves every published Kotlin artifact (compiler, gradle plugin,
        // stdlib, plugin markers) directly through CloudFront. Maven Central rate-limits the
        // 60 MB kotlin-compiler-embeddable jar on GitHub Actions runners and redirects it to
        // the GitHub release asset CDN, which has been returning 504 Gateway Time-out under
        // load. Resolve Kotlin from JetBrains first so buildSrc compilation does not depend
        // on the GitHub release CDN staying healthy.
        maven {
            name = "JetBrainsKotlin"
            setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
            content {
                includeGroupAndSubgroups("org.jetbrains.kotlin")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
