# LunarMotion baseline — how motion is measured

Phase 0 of the motion plan (`LunarMotion-plan.md`). Nothing about feel changes here: this is
how "smoother" stops being an opinion.

Two instruments, deliberately different:

| Instrument | Where the numbers come from | What it's good for |
|---|---|---|
| **In-app frame timing** (shipped) | your phone, your library, your real network | catching the frames that actually drop during daily use, with the exact screen |
| **Macrobenchmark** (spec'd below, not built) | a device/emulator, scripted journeys | repeatable before/after on one fixed script, and Baseline Profile generation |

The in-app one exists because a benchmark harness you can't run measures nothing. It costs a
callback per frame while on, and is off by default.

---

## 1. The in-app instrument

**Where:** `ui/utils/MotionFrames.kt`
**Switch:** Settings → Debug → Experimental features → **Log slow frames**
**Output:** Debug Logs, tag `Motion`

It times every drawn frame against the panel's own refresh rate (read from the display, so a
120 Hz panel gets an 8.3 ms budget, not 16.6). A frame past 1.5× budget is written as it
happens; a summary lands every 5 s; another summary is written when the switch is turned
back off.

```
I Motion   Frame timing on: 120.0 Hz panel, budget 8ms
W Motion   Slow frame 21ms (budget 8ms)
I Motion   341 frames, 12 slow (3.5%), worst 34ms, budget 8ms
I Motion   Frame timing off: 288 frames, 4 slow (1.4%), worst 27ms
```

Reading it:

- **`slow %` is the number that matters.** Under ~1 % is invisible; 3–5 % is "something is
  working hard"; > 8 % means a transition or scroll is genuinely janky.
- **`worst`** catches the one-off hitch a percentage hides — a 60 ms frame is a visible
  stutter, not a stray.
- The per-frame `W` lines tell you *when* it happened, so scroll back to what you did. Turn
  the switch on, do one journey, turn it off, and the log is that journey.

### The five journeys

Do each one on a clean run of the app, with the switch on, and note the summary lines:

1. **Cold start** — kill the app, open it, sit on Home until the shelves are drawn.
2. **Home scroll** — one long fling up, then a slow drag down. (Scroll is where list
   rendering and image decode collide.)
3. **Drill** — open the first album or artist from Library, then back out. (The depth-zoom
   transition + shared artwork decode.)
4. **Player expand** — with something playing, open the player from the mini player, then
   collapse it. (The single most expensive surface: backdrop blur + artwork + controls.)
5. **Sheet drag** — drag the player up and let it go, catch it mid-flight and drag it back.
   (Interruption quality; the phase 4 work is judged here.)

### The table to fill

One row per journey per device. Re-run the whole table after any motion change; that's what
"before/after" means from here on.

| Device | Refresh | Build | Date | Journey | frames | slow % | worst |
|---|---|---|---|---|---|---|---|
| Snapdragon 636 | 60 Hz | 5.4.0 (`588bd81`) | 2026-11-14 | see run 1 below | 10351 | 9.6 % | 683 ms |

---

## 2. Baseline run 1 — Snapdragon 636, 60 Hz (budget 16 ms)

The first real numbers. The run wasn't split into labelled journeys, so the segments below are
grouped by what the log itself says was happening; the mapping to the five journeys needs one
confirmation from the device owner (see §1b).

| Segment | Evidence in the same log lines | frames | slow % | worst |
|---|---|---|---|---|
| **A — cold start + first library/playlist sync** | `Canvas cache restored`, BotGuard pre-warm, `syncLibrarySongs`, `syncSavedPlaylists` | 599 | **7.0 %** | **683 ms** |
| **B — browsing (idle, no sync)** | quiet log | 1650 | 3.4 % | 166 ms |
| **C — the collapse** | Adreno RenderScript storm, `NativeAlloc GC ... total 250.410ms`, 18 MB freed, ends at `onAppEnteredBackground` | 966 | **48.9 %** | **650 ms** |
| **D — playback start / resume** | opus decoder init, three GCs 378 / 250 / 107 ms, JIT | 734 | 7.4 % | 416 ms |
| **E — steady browsing over ~2 min** | periodic `syncLikedSongs` / `syncPlaylist`, JIT bursts | 6106 | 5.9 % | 383 ms |
| **Total** | | **10351** | **9.6 %** | **683 ms** |

Shape of the slow frames (991 of them, from the written-out `W` lines, which are rate-limited
so the log undercounts them):

| Frame length | count | share | meaning |
|---|---|---|---|
| 25–45 ms | 95 | 44 % | one dropped vsync |
| 45–75 ms | 77 | 36 % | two to three dropped |
| 75–120 ms | 23 | 11 % | four to six dropped |
| 120–200 ms | 9 | 4 % | visible freeze |
| 200–400 ms | 4 | 2 % | obvious freeze |
| > 400 ms | 7 | 3 % | stall |

### What the log actually blames

Four causes, in order of how much frame budget they eat. None of them is an animation curve.

1. **Blur on the flight path.** Segment C is the worst run of the session — nearly half of all
   frames dropped, five consecutive 5 s windows in the 33–68 % range — and it lines up with an
   `Adreno-RS` / `RenderScript` storm plus a 250 ms native GC. That's Haze's pre-`RenderEffect`
   blur fallback: CPU/native blur, allocating a fresh set of full-frame bitmaps every time it
   runs. The stack-blur utility in this repo allocates four full-size `IntArray`s per call
   (~8 MB for a 720² frame) — a blur recomputed a few times a second is a permanent GC pump.
2. **Allocation churn everywhere else.** `Background concurrent copying GC` freeing 15–26 MB
   every few seconds, with totals of 100–560 ms *each*, on a device that has maybe six usable
   cores. Nothing in the app allocates 20 MB in five seconds by accident.
3. **No Baseline Profile.** Mid-scroll, the log shows JIT compiling at runtime:
   `Compiler allocated 15MB to compile java.util.List ds3.q(...)`, `~6MB` for two more methods
   two seconds apart — and the two worst browsing windows (25.1 % and 23.3 % slow) sit exactly
   on those compiles. That's class-loading + compiling Compose code the user is scrolling
   through, because nothing was AOT-compiled for this path.
4. **Cold start work done while the first frames are drawn.** Segment A overlaps BotGuard,
   library sync and playlist sync with the initial UI, and pays a 683 ms frame for it.

### What this rules out

Easing curves. At 33 ms a frame has already lost a whole vsync's worth of work; no spring
tuned from 380 ms to 300 ms touches that, and on this device the difference between a good and
a bad `LunarMotion` spec is invisible next to a 250 ms GC. The plan's Phase 1 (one clock) is
still worth doing — it's what keeps the codebase honest — but it is not what's making this
phone feel rough, and shipping it first would produce no measurable change in the table above.

### Revised targets for a device in this class

Measured, not aspirational:

| Segment | now | target |
|---|---|---|
| A cold start | 7.0 % / 683 ms | ≤ 3 % / ≤ 200 ms |
| B browsing idle | 3.4 % / 166 ms | ≤ 1 % / ≤ 50 ms |
| C player ↔ sheet | 48.9 % / 650 ms | ≤ 5 % / ≤ 100 ms |
| D playback start | 7.4 % / 416 ms | ≤ 3 % / ≤ 150 ms |
| E steady browsing | 5.9 % / 383 ms | ≤ 1.5 % / ≤ 100 ms |

A 60 Hz mid-range device will never hit "0 % slow". These are the numbers to beat, and every
future change has to be compared against this table, not against a 120 Hz flagship.

### 2.1 One open question

Which of the five journeys is segment C? The log says the collapse ends in
`onAppEnteredBackground` at 12:54:45, and playback only starts at 12:54:55, so C happened
while nothing was playing. If C is the player expand, the blur is confirmed as the main
offender; if it's the drill transition, the same blur is hit through the artwork zoom instead.
Either way the fix is the same, so the work can start before the answer arrives.

---

## 3. Macrobenchmark harness (spec'd, not built yet)

Deliberately deferred, for one reason: it changes the Gradle project graph, and nothing in
this repo can currently prove the change configures cleanly (no local Android SDK, and the
APK workflow doesn't build benchmark code). Shipping an unvalidated module that could break
every APK build is a worse trade than shipping the instrument above.

When it's added, here is exactly what it has to account for — read from this project:

| Constraint | Why |
|---|---|
| `com.android.test` module, `targetProjectPath = ":app"`, `experimentalProperties["android.experimental.self-instrumenting"] = true` | Macrobenchmark can't run from the app's own `androidTest`; it must be a separate, self-instrumenting test APK |
| `missingDimensionStrategy` for `distribution` → `gms`, `device` → `mobile`, `abi` → `universal` | The app has three flavor dimensions (`build.gradle.kts` ~L110–165); the test module has to pick one of each |
| Target build type: `profileable` (already exists: release-identical + `isProfileable = true` + debug signing) | Already provided — no `benchmark` build type or `<profileable>` manifest tag is needed |
| `include(":benchmark")` behind a property, e.g. `if (providers.gradleProperty("benchmark").isPresent)` | Stops the benchmark module from participating in — or being able to break — the shipping build |
| Versions available now | `androidx.benchmark` **1.5.0**, `androidx.baselineprofile` plugin **1.5.0**, `androidx.test.ext:junit` **1.3.0**, `uiautomator` **2.4.1** |
| Journeys to script | the same five above; startup via `StartupMode.COLD`, the rest via `FrameTimingMetric()` and coordinate gestures (`device.swipe`) rather than selector text, which breaks with localization and UI edits |
| Player/sheet journeys | need something playing; either seed playback before measuring or keep those two out of CI and run them manually |

Job to add at the same time: a `workflow_dispatch`-only workflow that runs
`:benchmark:connectedBenchmarkAndroidTest` on an emulator and uploads the JSON — never on
`push`, so the APK build stays the only automatic gate.

### Baseline Profile

The profile is where the "free startup/scroll win" actually comes from, and it needs one
artifact: `BaselineProfileRule` (in `benchmark-macro-junit4`) collecting startup + scroll, its
`baseline-prof.txt` copied to `app/src/main/baseline-prof.txt`, which AGP packages into the
app automatically. `profileinstaller` is already a dependency, so nothing else is needed.

Until that run exists there is no profile in the build, and no amount of tuning the motion
specs changes cold-start class loading. Treat it as the first thing after the harness lands.

---

## 4. Limits of this baseline

- **One device, one version.** A Snapdragon 636 at 60 Hz. It's the harshest target and the
  right one to optimise against, but nothing here says how the app behaves on a 120 Hz panel;
  that table still has to be filled.
- **Journeys inferred, not labelled.** §2 groups the run by what the log shows happening. One
  confirmation from the device owner would turn segment C from "the collapse" into a name.
- **The written-out `W` lines are rate-limited** (one per 400 ms), so they undercount: 215
  lines for 991 actual slow frames. The 5 s summaries are the authoritative numbers, and the
  next version of the instrument will report how many lines it suppressed.
- **No profile trace.** No `perfetto`/`simpleperf` capture was taken, so the attribution in §2
  is correlational: the RenderScript storm, the 250 ms GC and the JIT compiles sit inside the
  bad windows to the millisecond. That is strong, but it is not a stack trace.

---

## 5. Next, in order (revised by the data)

The measured order is not the planned order. §2 moves three things ahead of the motion-spec
work:

1. **Blur off the flight path** (was Phase 5). The single biggest number in the table: 48.9 %
   of frames dropped, five consecutive windows at 33–68 %, lined up with RenderScript and a
   250 ms GC. This is the one change that can plausibly move segment C from 49 % to single
   digits.
2. **Allocation churn** (was Phase 5). 15–26 MB freed every few seconds is a GC pump; find
   what allocates it and stop it. Blur is the known contributor; the rest needs a quick hunt.
3. **Baseline Profile** (was Phase 0's second half). The JIT compiles are *in* the worst
   browsing windows. This needs the benchmark harness, so it's the one item that still needs
   the deferred module — worth doing now that there's evidence it buys something.
4. **Then Phase 1** (one clock, CI-enforced), which is cheap and keeps the rest honest, but
   will not move these numbers.
5. **Then Phase 2** (shared bounds), which is the step change in *feel* once the frames stop
   dropping.
