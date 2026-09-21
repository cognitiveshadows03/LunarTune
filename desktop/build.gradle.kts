import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "dev.citali.lunartune"
version = "5.2.0-desktop-alpha"

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // VIVI Desktop's JVM playback foundation; LunarTune's stream resolver and queue
    // will be connected to this boundary in the next desktop-port increment.
    implementation("com.github.walkyst:lavaplayer-fork:1.4.3")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
}

compose.desktop {
    application {
        mainClass = "dev.citali.lunartune.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "LunarTune"
            packageVersion = "5.2.0"
            description = "LunarTune desktop music client"
            vendor = "LunarTune contributors"
        }
    }
}
