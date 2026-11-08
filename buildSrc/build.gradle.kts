plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    google()
    // Mirror Kotlin artifacts off JetBrains Space: Maven Central rate-limits the 60 MB
    // kotlin-compiler-embeddable jar on GitHub Actions runners and redirects it to the
    // GitHub release asset CDN, which has been returning 504 Gateway Time-out under load.
    // JetBrains Space serves the same release artifacts directly via CloudFront.
    maven {
        name = "JetBrainsKotlin"
        setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        content {
            includeGroupAndSubgroups("org.jetbrains.kotlin")
        }
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("org.apache.xmlgraphics:batik-all:1.19")
}
