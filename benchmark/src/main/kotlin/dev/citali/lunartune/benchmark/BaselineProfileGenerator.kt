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
 * the GC churn that follows each of those compiles. A Baseline Profile is ART's list of methods
 * worth compiling ahead of time; without one, every install pays that cost again on every
 * screen, on every device, however fast the device is.
 *
 * The journeys are deliberately dull and coordinate-based: the profile only needs the *code
 * paths*, so a gesture that survives a localization change or a UI reshuffle is worth more than
 * one that asserts on text. Every step is best-effort: a tap that lands on nothing still leaves
 * the app on a screen whose composables were just exercised, and nothing here asserts.
 *
 * The first version of this only walked Home and one drill, and the frame log afterwards showed
 * exactly what that missed: `ds3.q`, `cg1.v`, `et3.i` and `dm6.a` - library, playlist and
 * account code - still being compiled at runtime. Hence the tabs.
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

            // The primary tabs. Coordinates rather than labels: the bar is a capsule with equal
            // weights, so four taps across its width land on four destinations, and a miss just
            // means one screen fewer in the profile.
            val width = device.displayWidth
            val height = device.displayHeight
            val tabY = height - (height * 0.06f).toInt()
            listOf(0.26f, 0.42f, 0.58f, 0.74f).forEach { fraction ->
                device.click((width * fraction).toInt(), tabY)
                device.waitForIdle()
                repeat(2) { flingUp() }
                repeat(1) { flingDown() }
            }

            // Back to the first tab, then a drill and back: the depth transition and the detail
            // composables are a different set of methods from the shelves.
            device.click((width * 0.26f).toInt(), tabY)
            device.waitForIdle()
            device.click(width / 2, (height * 0.62f).toInt())
            device.waitForIdle()
            repeat(1) { flingUp() }
            device.pressBack()
            device.waitForIdle()

            // The sheet above the bar: mini player into the full player, which carries the
            // heaviest composables in the app.
            device.click(width / 2, height - (height * 0.16f).toInt())
            device.waitForIdle()
            device.wait(Until.hasObject(By.pkg(TargetPackage).depth(0)), 3_000)
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
         * The app's `profileable` build type carries an `applicationIdSuffix`, so the installed
         * package is the suffixed one. Targeting the bare id fails with "Unable to find target
         * package ... is it installed?" even when the APK is right there.
         */
        const val TargetPackage = "dev.citali.lunartune.profileable"
    }
}
