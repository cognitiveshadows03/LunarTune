plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

/*
 * Macrobenchmark + Baseline Profile generation.
 *
 * Opt-in only: settings.gradle.kts includes this module just when -Pbenchmark is passed, so a
 * normal build never configures it and nothing here can break a shipping APK.
 *
 *     ./gradlew -Pbenchmark :benchmark:connectedBenchmarkAndroidTest
 *
 * The generated profile goes to benchmark/src/main/generated/baselineProfiles/baseline-prof.txt
 * and is copied to app/src/main/baseline-prof.txt, which AGP packages into the app.
 */
android {
    namespace = "dev.citali.lunartune.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The app has three flavour dimensions, so the test module has to pick one of each.
        // universal bundles every ABI, which is what an emulator run needs.
        missingDimensionStrategy("distribution", "gms")
        missingDimensionStrategy("device", "mobile")
        missingDimensionStrategy("abi", "universal")
    }

    targetProjectPath = ":app"

    // A self-instrumenting test APK: it drives the real app process rather than being installed
    // into it. This is the only supported way to macrobenchmark an app.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            // The app's own `profileable` build type is release-identical, profileable from the
            // shell and debug-signed - exactly the artifact a profile should be collected from.
            matchingFallbacks += listOf("profileable", "release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        // The benchmark build type is the only one this module needs.
        variant.enable = variant.buildType == "benchmark"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.junit)
}
