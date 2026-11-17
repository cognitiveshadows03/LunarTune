@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
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
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
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
        mavenCentral {
            mavenContent {
                releasesOnly()
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    setUrl("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.therealbush")
                includeGroup("com.github.TeamNewPipe")
                includeGroup("com.github.RouHim")
                includeGroup("com.github.yalantis")
            }
        }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "LunarTune"
include(":app")
include(":core")
include(":lyrics:kugou")
include(":lyrics:lrclib")
include(":lyrics:simpmusic")
include(":lyrics:paxsenix")
include(":lyrics:betterlyrics")
include(":lyrics:unison")
include(":lyrics:youlyplus")
include(":lyrics:musixmatch")
include(":lastfm")
include(":canvas")
include(":shazamkit")
include(":spotifycore")
include(":morideobfuscator")

// Opt-in macrobenchmark module: it exists to generate and check Baseline Profiles on a device
// or emulator, and a shipping build must never be able to fail because of it. Without
// -Pbenchmark it is not part of the build at all.
if (gradle.startParameter.projectProperties.containsKey("benchmark")) {
    include(":benchmark")
}

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that LunarTune and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in core/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.TeamNewPipe:NewPipeExtractor")
// includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.TeamNewPipe:NewPipeExtractor")).using(project(":extractor"))
//    }
// }
