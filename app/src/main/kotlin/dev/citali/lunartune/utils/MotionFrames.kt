/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.content.Context
import android.os.Debug
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
 * The summary also carries the two numbers that explain most slow frames on real hardware:
 * how many GCs ran in the window and how long they took, and how much the heap grew. A
 * window that drops a third of its frames next to "gc 4 (912ms), heap +21MB" is an allocation
 * problem, not an animation problem - guessing between the two is what this whole thing is
 * for.
 *
 * Costs one Choreographer callback per frame while on, and nothing at all while off.
 */
object MotionFrames {
    const val Tag = "Motion"

    private const val BytesPerMb = 1024L * 1024L

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
    private var quietSlowFrames = 0

    private var windowGcCount = 0L
    private var windowGcTimeMs = 0L
    private var lastGcCount = 0L
    private var lastGcTimeMs = 0L
    private var windowStartHeapBytes = 0L
    private var gcStatsUsable = false

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
        lastSlowLogAtMs = 0L
        gcStatsUsable = true
        lastGcCount = runtimeStat("art.gc.gc-count")
        lastGcTimeMs = runtimeStat("art.gc.gc-time")
        if (lastGcCount < 0 || lastGcTimeMs < 0) gcStatsUsable = false
        startWindow(0L)
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
            logSummary("Frame timing off")
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
                    "Slow frame %dms (budget %dms)%s",
                    deltaNanos / 1_000_000,
                    budgetNanos / 1_000_000,
                    if (quietSlowFrames > 0) ", $quietSlowFrames more since last line" else "",
                )
                quietSlowFrames = 0
            } else {
                // the lines are rate limited, so the summary is the only honest count
                quietSlowFrames++
            }
        }

        if (windowStartNanos == 0L) windowStartNanos = frameTimeNanos
        if (frameTimeNanos - windowStartNanos >= SummaryEveryNanos) {
            logSummary(null)
            startWindow(frameTimeNanos)
        }
    }

    private fun startWindow(atNanos: Long) {
        windowStartNanos = atNanos
        framesInWindow = 0
        longFramesInWindow = 0
        worstFrameNanos = 0L
        quietSlowFrames = 0
        windowGcCount = 0L
        windowGcTimeMs = 0L
        windowStartHeapBytes = heapUsedBytes()
    }

    /**
     * Writes the window to the log. [prefix] labels a final summary; the periodic one passes
     * null and gets the bare numbers.
     */
    private fun logSummary(prefix: String?) {
        val gcNow = runtimeStat("art.gc.gc-count")
        val gcTimeNow = runtimeStat("art.gc.gc-time")
        var gcPart = "gc unavailable"
        var heapPart = "heap unavailable"
        if (gcStatsUsable && gcNow >= lastGcCount && gcTimeNow >= lastGcTimeMs) {
            gcPart = "gc ${gcNow - lastGcCount} (${gcTimeNow - lastGcTimeMs}ms)"
            lastGcCount = gcNow
            lastGcTimeMs = gcTimeNow
        }
        val heapNow = heapUsedBytes()
        if (heapNow > 0 && windowStartHeapBytes > 0) {
            heapPart = "heap %+dMB".format((heapNow - windowStartHeapBytes) / BytesPerMb)
        }
        val numbers =
            "%d frames, %d slow (%s), worst %dms, budget %dms | %s, %s, %d unlogged".format(
                framesInWindow,
                longFramesInWindow,
                share(longFramesInWindow, framesInWindow),
                worstFrameNanos / 1_000_000,
                budgetNanos / 1_000_000,
                gcPart,
                heapPart,
                quietSlowFrames,
            )
        if (prefix == null) {
            Timber.tag(Tag).i("%s", numbers)
        } else {
            Timber.tag(Tag).i("%s: %s", prefix, numbers)
        }
    }

    private fun share(part: Int, whole: Int): String =
        if (whole <= 0) "0%" else "%.1f%%".format(part * 100f / whole)

    /** a single runtime counter, or -1 when the platform won't give it up */
    private fun runtimeStat(key: String): Long =
        runCatching { Debug.getRuntimeStat(key)?.toLongOrNull() ?: -1L }.getOrDefault(-1L)

    private fun heapUsedBytes(): Long =
        runCatching {
            val runtime = Runtime.getRuntime()
            runtime.totalMemory() - runtime.freeMemory()
        }.getOrDefault(0L)

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
