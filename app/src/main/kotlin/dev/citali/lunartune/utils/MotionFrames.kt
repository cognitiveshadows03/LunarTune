/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.content.Context
import android.view.Choreographer
import android.view.WindowManager
import timber.log.Timber

/**
 * Live frame timing, for judging motion on the frames it actually produced.
 *
 * With the "log slow frames" switch on, every frame the app draws is timed against the
 * panel's own refresh rate. A frame that runs past its budget is logged as it happens, and a
 * summary lands every few seconds. Both show up in Debug Logs under the "Motion" tag, which is
 * where motion work is checked - a spring that looks good in isolation but drops three frames
 * on the way in shows up here as a number.
 *
 * Budget comes from the display rather than a fixed 60 Hz: on a 120 Hz panel the budget is
 * 8.3 ms, and 16 ms is already a dropped frame.
 *
 * Costs one Choreographer callback per frame while on, and nothing at all while off.
 */
object MotionFrames {
    const val Tag = "Motion"

    /** how often the running summary is written */
    private const val SummaryEveryNanos = 5_000_000_000L

    /** rate limit on individual slow-frame lines, so one bad second doesn't flood the log */
    private const val SlowFrameQuietMs = 400L

    private var running = false
    private var budgetNanos = 16_666_667L
    private var displayHz = 60f

    private var lastFrameNanos = 0L
    private var windowStartNanos = 0L
    private var framesInWindow = 0
    private var longFramesInWindow = 0
    private var worstFrameNanos = 0L
    private var lastSlowLogAtMs = 0L

    private val callback =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                record(frameTimeNanos)
                runCatching { Choreographer.getInstance().postFrameCallback(this) }
            }
        }

    /** starts timing frames, or does nothing if it's already running */
    fun start(context: Context) {
        if (running) return
        running = true
        readRefreshRate(context)
        lastFrameNanos = 0L
        windowStartNanos = 0L
        framesInWindow = 0
        longFramesInWindow = 0
        worstFrameNanos = 0L
        lastSlowLogAtMs = 0L
        Timber.tag(Tag).i(
            "Frame timing on: %.1f Hz panel, budget %dms",
            displayHz,
            budgetNanos / 1_000_000,
        )
        runCatching { Choreographer.getInstance().postFrameCallback(callback) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { Choreographer.getInstance().removeFrameCallback(callback) }
        // a summary on the way out, so a session's numbers aren't lost by turning the switch off
        if (framesInWindow > 0) {
            Timber.tag(Tag).i(
                "Frame timing off: %d frames, %d slow (%s), worst %dms",
                framesInWindow,
                longFramesInWindow,
                share(longFramesInWindow, framesInWindow),
                worstFrameNanos / 1_000_000,
            )
        }
    }

    private fun record(frameTimeNanos: Long) {
        val previous = lastFrameNanos
        lastFrameNanos = frameTimeNanos
        if (previous == 0L) return

        val deltaNanos = frameTimeNanos - previous
        // one and a half budgets: the vsync handshake alone drifts a few ms, a real drop doubles
        val isLong = deltaNanos > budgetNanos + budgetNanos / 2

        framesInWindow++
        if (isLong) {
            longFramesInWindow++
            if (deltaNanos > worstFrameNanos) worstFrameNanos = deltaNanos
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastSlowLogAtMs >= SlowFrameQuietMs) {
                lastSlowLogAtMs = nowMs
                Timber.tag(Tag).w(
                    "Slow frame %dms (budget %dms)",
                    deltaNanos / 1_000_000,
                    budgetNanos / 1_000_000,
                )
            }
        }

        if (windowStartNanos == 0L) windowStartNanos = frameTimeNanos
        if (frameTimeNanos - windowStartNanos >= SummaryEveryNanos) {
            Timber.tag(Tag).i(
                "%d frames, %d slow (%s), worst %dms, budget %dms",
                framesInWindow,
                longFramesInWindow,
                share(longFramesInWindow, framesInWindow),
                worstFrameNanos / 1_000_000,
                budgetNanos / 1_000_000,
            )
            windowStartNanos = frameTimeNanos
            framesInWindow = 0
            longFramesInWindow = 0
            worstFrameNanos = 0L
        }
    }

    private fun share(part: Int, whole: Int): String =
        if (whole <= 0) "0%" else "%.1f%%".format(part * 100f / whole)

    private fun readRefreshRate(context: Context) {
        val hz =
            runCatching {
                val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                @Suppress("DEPRECATION")
                manager.defaultDisplay.refreshRate
            }.getOrDefault(60f)
        displayHz = if (hz.isFinite() && hz > 1f) hz else 60f
        budgetNanos = (1_000_000_000.0 / displayHz).toLong()
    }
}
