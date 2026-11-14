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
| _(fill)_ | | | | Cold start | | | |
| | | | | Home scroll | | | |
| | | | | Drill | | | |
| | | | | Player expand | | | |
| | | | | Sheet drag | | | |

Targets once the first row exists:

- **slow % < 1** on every journey, on the reference device, for both 60 Hz and 120 Hz runs.
- **worst < 2 × budget** on every journey (no visible stutter, even a single one).
- No regression: a change that raises any journey's slow % by more than 0.5 point needs a
  reason in the commit message.

---

## 2. Macrobenchmark harness (spec'd, not built yet)

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

## 3. What Phase 0 does *not* claim

- No numbers are filled in above. They have to come from a device, and inventing them would
  poison every later comparison.
- No motion behaviour changed in this phase — one debug switch, one timer, one doc.
- The macrobenchmark module is not in the tree; §2 is the spec for adding it safely.

---

## 4. Next, in order

1. Fill the table on the reference device (30 minutes, one journey at a time).
2. Any journey already over target gets a ticket in Phase 5 (frame budget) first — a janky
   scroll is not fixed by a nicer spring.
3. Then Phase 1 (one clock, CI-enforced), which is the change that makes the rest of the plan
   stick.
