pluginManagement {
    repositories {
        // Mirror only the 60 MB kotlin-compiler-embeddable jar off Google's Maven Central
        // mirror. Maven Central rate-limits GitHub Actions runners and redirects that
        // artifact to the GitHub release asset CDN, which intermittently returns 504
        // Gateway Time-out. Google's mirror is a straight byte-for-byte copy of Maven
        // Central (not a dev channel), so the bytes are the released ones; the pinned
        // SHA-256 in gradle/kotlin-compiler-embeddable-<version>.sha256 is still checked
        // in CI in case the mirror ever serves something unexpected. If the mirror is
        // unreachable or behind on a brand-new release, Gradle falls through to
        // mavenCentral below.
        maven {
            name = "MavenCentralMirror"
            setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
            content {
                includeModule("org.jetbrains.kotlin", "kotlin-compiler-embeddable")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
