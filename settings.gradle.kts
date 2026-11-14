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
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        // Mirror only the 60 MB kotlin-compiler-embeddable jar off JetBrains Space.
        // Maven Central rate-limits this artifact on GitHub Actions runners and
        // redirects it to the GitHub release asset CDN, which has been returning
        // 504 Gateway Time-out under load. Every other Kotlin artifact (stdlib,
        // reflect, plugin, plugin markers) is small enough to resolve from Maven
        // Central, so the mirror is narrowed to this single module to keep the
        // dev-channel exposure minimal. Verified: the jar bytes are identical to
        // Maven Central (SHA-256 pinned in
        // gradle/kotlin-compiler-embeddable-2.4.0.sha256 and checked in CI).
        maven {
            name = "JetBrainsKotlin"
            setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
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
