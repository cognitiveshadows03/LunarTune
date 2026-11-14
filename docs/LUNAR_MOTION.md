# Lunar Motion

How motion works in LunarTune: the vocabulary, the rules it encodes, and the patterns to
reach for when you add or change an animation.

Everything lives in one place:

```
app/src/main/kotlin/dev/citali/lunartune/ui/theme/Motion.kt  →  object LunarMotion
```

Do not sprinkle ad-hoc `tween(310)` / `spring(dampingRatio = 1.5f, ...)` through screens.
If a spec is missing, extend `LunarMotion` — that keeps the whole app on one clock.

---

## 1. The three rules

1. **Springs are for interruptible, finger-driven motion.** Presses, drags, snap-backs,
   expanding panels. Springs settle organically and survive being grabbed mid-flight.
2. **Tweens are for motion a finger cannot interrupt mid-flight.** Navigation transitions,
   fades, color morphs. Plain tweens are also what make Android 14+ **Predictive Back**
   scrub 1:1 — `SeekableTransitionState` needs monotonic timing, and springs break the
   illusion (they travel fastest early and crawl at the end).
3. **No overshoot on alpha, color, lyrics, or AOD motion.** Overshooting a blended channel
   reads as a glitch, not life. Fades and color morphs are always tweens.

### The momentum rule

A drag that is released must **never** animate from rest. Pass the release velocity as
`initialVelocity` so the element continues at the speed the finger left at:

```kotlin
val offsetX = remember { Animatable(0f) }
scope.launch {
    offsetX.animateTo(0f, LunarMotion.settle(), initialVelocity = clampVelocity(releaseVelocity))
}
```

Use `settle()` for snap-backs, `flingOut()` for dismissals (off-screen target, so any
overshoot is invisible and bouncing off the edge reads as a bug). `clampVelocity()` caps
absurd flicks so the spring can settle cleanly.

---

## 2. Decision tree

```
Is it a nav/route transition or a fade/color morph?
    → tween, inside a LunarMotion nav builder (Depth Zoom / Drill / tab glide)

Is it direct motion that a finger can interrupt?
    ├─ press feedback          → press()
    ├─ content appears         → bouncy()
    ├─ shape/size/layout       → smooth() (zero overshoot)
    ├─ fast retract            → snappy()
    ├─ snap back after drag    → settle() + initialVelocity
    ├─ dismissal after drag    → flingOut() + initialVelocity
    └─ scroll-driven hide      → scrollHide()

Is it progress/loading/lyric/ambient/AOD?
    → tween. Not a spring, by rule 3.

Is it a crossfade between two states of the same thing?
    → Crossfade + tween(250) or less; see the style picker headline for an example.
```

---

## 3. The vocabulary

### 3.1 Easings (literal `CubicBezierEasing` — stable across Compose versions)

| Easing | Curve | Used for |
|---|---|---|
| `EmphasizedDecelerate` | (0.05, 0.7, 0.1, 1) | entrances — fast start, long soft settle |
| `EmphasizedAccelerate` | (0.3, 0, 0.8, 0.15) | exits — clears the way |
| `SpatialEnterEasing` | (0.18, 0.88, 0.22, 1) | depth-zoom arrivals |
| `SpatialExitEasing` | (0.3, 0, 0.85, 0.22) | depth-zoom / picker departures |
| `TabGlideEasing` | (0.18, 0.9, 0.2, 1) | tab glide arrival (avoids front-loading travel into frame 1) |
| `TabGlideExitEasing` | (0.3, 0, 0.85, 0.25) | tab glide departure |
| `LinearOutSlowInEasing` | androidx constant | fade leg of the depth-zoom enter |

### 3.2 Springs (generic `<T>`)

| Spec | damping / stiffness | Feel | Reach for it when |
|---|---|---|---|
| `bouncy()` | 0.75 / 500 | ~350ms, one soft overshoot | entrances, icon rotations, sheets appearing, snap-backs |
| `smooth()` | 1.0 / 700 | zero overshoot | shape/size/layout motion (search morph, crossfades with scale) |
| `snappy()` | 0.95 / 1500 | ~200ms, barely-there give | fast retracts (FABs, banners) |
| `glide()` | 0.88 / 350 | ~520ms, hint of life | full-screen nav glides |
| `press()` | 0.62 / 900 | tactile press bounce | any custom clickable surface |
| `settle()` | 0.82 / 520 | organic, no weird overshoot | snap-back after a drag |
| `flingOut()` | 1.0 / 700 | critically damped | dismissal when target is off-screen |
| `scrollHide()` | 0.72 / 800 | one visible overshoot | scroll-driven hides (nav bar) |

### 3.3 The depth-zoom navigation system (NavHost defaults)

Two 1:1-tween families composed from `fadeIn`/`fadeOut`/`scaleIn`/`scaleOut`:

| Spec | What the page does |
|---|---|
| `depthPageEnter()` | fades in (185ms) + scales in from `DepthEnterInitialScale` (0.94), 270ms decelerate |
| `depthPageExit()` | sinks back — fades toward `DepthBackgroundDimAlpha` (0.58) + recedes to `DepthRecedeTargetScale` (0.95), 270ms |
| `depthPagePopEnter()` | re-emerges from 0.95 / 0.58-dim to resting, 250ms |
| `depthPagePopExit()` | exits via fade + recede to `DepthPopExitTargetScale` (0.92), 220ms |
| `depthRootHoldExit()` / `depthRootHoldPopEnter()` | plain fades for the keep-alive Home proxy |

Dials (constants): `DepthZoom*Ms` durations, `DepthBackgroundDimAlpha`, `DepthScrimMaxAlpha`,
`SpatialEnter/ExitEasing`.

**Predictive back:** because these are tweens, the Android 14+ edge swipe drives them
linearly with the finger. This is deliberate and required for anything in a route lambda.
See §6.

### 3.4 Drill transitions

`drillEnter()` / `drillExit()` — `EmphasizedDecelerate` / `EmphasizedAccelerate` at
380/240ms. For list→detail movement that travels further than a tab switch.

### 3.5 The tab glide (top-level route switches)

The most reusable *pattern* in the file — asymmetry is the point:

- outgoing page leaves fast: `TabGlideExitMs = 115`, `TabGlideExitEasing`
- incoming settles slower: `TabGlideEnterMs = 210`, `TabGlideEasing`
- incoming fade runs on a delay: `TabGlideEnterFadeMs = 165`, `TabGlideFadeDelayMs = 25`
- offset hint: `TabGlideOffsetDp = 28`

Exit fast, enter slower, fade delayed. Copy this shape whenever two states replace each
other, not the numbers themselves.

### 3.6 House dials

`TabEnterMs/TabExitMs/DrillEnterMs/DrillExitMs` — durations live here, not inline.
`PressedScale` (0.85) — press target. `MaxStretch` (0.04) — drag stretch as a fraction of
travel, `coerceIn(0f, LunarMotion.MaxStretch)` (see `NetworkStatusBanner`).
`clampVelocity()` — trim flicks above ~6 000 px/s.

### 3.7 Bespoke example: the player style picker close

`playerStylePickerExit()` — fade out (220ms) + scale to 0.90 **anchored to bottom-center**
(`TransformOrigin(0.5f, 1f)`) + sink ~1/12 of height, 260ms `SpatialExitEasing`. Same path
for Apply and Exit; predictive-back scrubbable; whole page is the hero (per-child stagger
is not expressible in `ExitTransition` — see §7).

---

## 4. Recipes

### 4.1 Give a route a bespoke exit (predictive-back safe)

```kotlin
composable(
    route = "settings/appearance/player_styles",
    exitTransition = { if (disableAnimations) fadeOut(tween(0)) else LunarMotion.playerStylePickerExit() },
    popExitTransition = { if (disableAnimations) fadeOut(tween(0)) else LunarMotion.playerStylePickerExit() },
    // enter/popEnter intentionally unset → inherit NavHost defaults (depth zoom)
) { DrillPageContainer(pureBlack) { /* ... */ } }
```

Rules of the recipe: everything in the lambda must be an `Enter/ExitTransition` built from
tweens; respect `disableAnimations` with an instant fade; reference specs in `LunarMotion`,
never inline.

### 4.2 DrillPageContainer — rounded clip during transitions

```kotlin
Modifier.graphicsLayer {
    if (transition.isRunning) { shape = RoundedCornerShape(24.dp); clip = true }
    else { shape = RectangleShape; clip = false }
}.background(containerColor)
```

Cheap, high-value pattern for any full-screen page that exits with scale: the page clips to
the same rounded shape as the app's sheets while it moves, then snaps square at rest. Reuse
it for any new `composable` that needs a "physical panel" feel on exit.

### 4.3 Press scale on a custom clickable

```kotlin
val scale by animateFloatAsState(
    targetValue = if (pressed) LunarMotion.PressedScale else 1f,
    animationSpec = LunarMotion.press(),
    label = "pressScale",
)
Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
```

`press()` deliberately overshoots past 0.85 and settles — that's the tactile read. Apply to
surfaces that don't inherit M3's Button semantics (custom cards, tiles).

### 4.4 Drag release with momentum

```kotlin
val offset = remember { Animatable(initialValue = 0f) }
// while dragging: offset.snapTo(fingerX)
// on release:
scope.launch {
    if (abs(offset.value) > dismissThreshold) {
        offset.fling(0f - sign(offset.value) * screenWidth, LunarMotion.flingOut(), initialVelocity = clampVelocity(velocity))
    } else {
        offset.animateTo(0f, LunarMotion.settle(), initialVelocity = clampVelocity(velocity))
    }
}
```

Never `animateTo(target, spring())` without the release velocity — that is the "restart
from rest" bug the momentum rule exists to prevent.

### 4.5 Crossfade with the tab-glide asymmetry

The style picker headline crossfades the two-line title block:
`Crossfade(targetState = selected, animationSpec = tween(250))`. If you want it to breathe
more, reuse the glide timing: incoming 210ms `EmphasizedDecelerate` with a ~25ms delay,
outgoing 115ms accelerated — out clears before in peaks.

### 4.6 Content-size change with a bounce

```kotlin
Modifier.animateContentSize(LunarMotion.bouncy())
```

For expanding chips/capsules/cards. `smooth()` if the overshoot would distort layout
(anything showing text or artwork reflowing).

### 4.7 Predictive-back-safe overlay scrims

Use `DepthScrimMaxAlpha` (0.28) and `DepthBackgroundDimAlpha` (0.58) when introducing
scrims, so sheets, pickers and drill pages share one black.

---

## 5. Extending LunarMotion — rules

1. Add new specs **to `LunarMotion`**, never inline in a screen. Name it after intent
   (`playerStylePickerExit`, not `pickerOut2`).
2. Tweens only for anything in a route lambda; springs only for finite, interruptible motion.
3. Enter: decelerate. Exit: accelerate. Exit shorter than enter (see tab glide ratio).
4. Alpha is always a tween; color is always a tween.
5. Bake the disable-animations escape hatch into route lambdas, not into the spec itself.
6. Keep separate dials for separate axes (durations as `*Ms` constants, geometry as
   dedicated constants, curves as named `Easing` vals) so tuning never means hunting floats.
7. Litmus test for anything new: *would pretending to physical momentum survive an interruption?*
   If yes → spring + release velocity. If no → tween.

---

## 6. Predictive back

Android 14+ edge-swipe drives route transitions through `SeekableTransitionState`: the
transition is *scrubbed* by the gesture, not played to completion. Consequences:

- Route transitions must be **element-position-independent, monotonic tweens**. Springs are
  monotonic in value but not in perceived travel rate — they front-load and crawl, which
  feels broken under a finger.
- Alpha/scale/offset only. No layout-dependent distances that can't resolve from the
  transition's `(fullHeight, fullWidth)` lambdas.
- In-screen `Animatable` choreography (blooms, staggers) plays on tap/back-button but
  **not** during an edge-swipe scrub — if predictive back must cover it, the motion belongs
  in the nav transition.
- `DrillPageContainer`'s clip-while-running keeps panels looking intentional during the drag.

---

## 7. Anti-patterns

- ❌ Springs in `composable(...)` route transitions (breaks predictive-back scrub).
- ❌ Per-child stagger expectations from `ExitTransition` — it transforms the page, not its
  children. Page-level fade/scale/offset only.
- ❌ Bouncing alpha or color channels.
- ❌ `tween()` with a loose magic number inside a screen — extend `LunarMotion` instead.
- ❌ Overlapping enter and exit durations (enter never outlasts exit by design; the outgoing
  screen must clear first).
- ❌ Restarting drag animations from rest (pass `initialVelocity`; see the momentum rule).
- ❌ Using `DepthZoom*` numbers to size in-screen content; they are transition values, not
  layout values.

---

## 7b. The player sheet handoff (Phase 2, first slice)

The player is one full-screen container translated up and down (`BottomSheet.kt`), with the
mini player as a separate overlay that fades out and the expanded content fading in from 25 %
of the drag. There is no second destination, so a `SharedTransitionLayout` + `sharedBounds`
refactor would mean re-shaping the player into two animated-visibility scopes — a large change
to the most-used screen, which is not something to land blind.

What did land is the cheap half of the same idea, entirely in the draw phase:

| Before | After |
|---|---|
| Row fades out where it stands | Row scales up 6 % as it fades (`leave`) |
| Sheet content pops in at full scale | Content grows 0.94 → 1.0 and rises the last 18 dp as it fades in |

Both read from the same `state.progress`, so they arrive on one clock, and because they're
`graphicsLayer` blocks nothing re-lays out. This buys "one thing arriving" without pretending
to be true shared-element continuity — the artwork itself still does not fly between the two
layouts. That remains the step change for the phase, and it needs a device to verify.

## 8. Checklist for new motion

Before the checklist: a motion change is judged against numbers, not against how it looks
paused. `docs/LUNAR_MOTION_BASELINE.md` has the five journeys and the log lines to compare —
turn on Debug → log slow frames, run the journey, and compare `slow %` and `worst` with the
last recorded run.

- [ ] Spec added to `LunarMotion` (or reuses an existing one)
- [ ] Correct tool chosen (spring vs tween — decision tree §2)
- [ ] Enter decelerates / exit accelerates / exit ≤ enter in duration
- [ ] Alpha and color via tweens
- [ ] Interruptible drag motion carries release velocity (`settle`/`flingOut` + `clampVelocity`)
- [ ] `disableAnimations` respected at the route level
- [ ] Predictive-back behavior considered: route transitions scrub 1:1
- [ ] Reasonable under both light/dark theme and pure-black AOD mode

---

## 9. Quick file map

| File | What |
|---|---|
| `ui/theme/Motion.kt` | The specs (`LunarMotion`) |
| `ui/screens/NavigationBuilder.kt` | Route transition wiring, `DrillPageContainer` |
| `MainActivity.kt` (`NavHost`) | NavHost default transitions (depth zoom), `disableAnimations` |
| `ui/component/PrimaryTabsHost.kt` | Tab glide consumers |
| `ui/component/IconButton.kt`, `CapsuleNavigationBar.kt` | `press()` / `bouncy()` consumers |
| `ui/component/NetworkStatusBanner.kt` | `MaxStretch` drag-stretch reference |
| `ui/screens/settings/PlayerStyleCarousel.kt` | Crossfade, dots, pager graphicsLayer motion, the picker close route config |
| `ui/utils/MotionFrames.kt` | In-app frame timing (Debug → log slow frames) |
| `docs/LUNAR_MOTION.md` | This document |
| `docs/LUNAR_MOTION_BASELINE.md` | How motion is measured: journeys, log format, baseline table |
