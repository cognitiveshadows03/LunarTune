/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `baseline-prof.txt` for the app.
 *
 * Why this exists: on-device frame logs from the reference device show Compose UI methods being
 * JIT-compiled *while the user is scrolling them* (`Compiler allocated 14MB to compile ...`) and
 * 12-26 MB of GC churn following each of those compiles. A Baseline Profile is ART's list of
 * methods worth compiling ahead of time; without one, every install pays that cost again on
 * every screen, on every device, however fast the device is.
 *
 * The journeys here are deliberately dull and coordinate-based: startup, a few flings, one drill
 * in and back out. The profile only needs the *code paths*, so a gesture that survives a
 * localization change or a UI reshuffle is worth more than one that asserts on text.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = TargetPackage,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            // Let Home finish its first composition before touching it: a gesture into a
            // half-drawn screen profiles the wrong frames.
            device.wait(Until.hasObject(By.pkg(TargetPackage).depth(0)), 5_000)
            device.waitForIdle()

            repeat(3) { flingUp() }
            repeat(2) { flingDown() }

            // One depth-zoom drill and back: different composables, and the transition itself.
            val width = device.displayWidth
            val height = device.displayHeight
            device.click(width / 2, (height * 0.62f).toInt())
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            repeat(2) { flingUp() }
        }
    }

    private fun MacrobenchmarkScope.flingUp() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(
            width / 2,
            (height * 0.72f).toInt(),
            width / 2,
            (height * 0.28f).toInt(),
            24,
        )
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.flingDown() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(
            width / 2,
            (height * 0.28f).toInt(),
            width / 2,
            (height * 0.72f).toInt(),
            24,
        )
        device.waitForIdle()
    }

    private companion object {
        /**
         * The benchmark build type targets the app's `profileable` flavour of this package, so
         * the id is the release one - not the `.debug` suffixed build.
         */
        const val TargetPackage = "dev.citali.lunartune"
    }
}
