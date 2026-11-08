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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

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
