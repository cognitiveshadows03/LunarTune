/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import dev.citali.lunartune.ui.screens.HomeScreen
import dev.citali.lunartune.ui.screens.Screens
import dev.citali.lunartune.ui.screens.library.LibraryScreen
import dev.citali.lunartune.ui.screens.search.SearchScreen
import dev.citali.lunartune.ui.theme.LunarMotion
import dev.citali.lunartune.viewmodels.HomeViewModel
import kotlinx.coroutines.launch

private const val OffscreenParkPx = 100_000f
private val DepthRecedeCornerShape = RoundedCornerShape(24.dp)

/**
 * Bridges NavHost's primary-root [EnterExitState] (including live Android 14+ Predictive Back
 * seek progress) to [PrimaryTabsHost], the floating TopAppBar, and the bottom bar without causing
 * main-thread recompositions: [presence] is backed directly by the transition's [State] and read
 * only inside draw / graphicsLayer / offset phases.
 */
@Stable
class PrimaryRootDepthState {
    private var attachedOnce by mutableStateOf(false)
    private var presenceState by mutableStateOf<State<Float>?>(null)

    val presence: Float
        get() = if (!attachedOnce) 1f else (presenceState?.value ?: 0f)

    val isActiveInHierarchy: Boolean
        get() = !attachedOnce || presenceState != null

    internal fun attach(state: State<Float>) {
        attachedOnce = true
        presenceState = state
    }

    internal fun detach(state: State<Float>) {
        if (presenceState === state) {
            presenceState = null
        }
    }
}

@Composable
fun rememberPrimaryRootDepthState(): PrimaryRootDepthState = remember { PrimaryRootDepthState() }

/**
 * Lightweight 0-cost proxy placed on [Screens.Home.route] inside `NavHost` so `NavHost` drives
 * [PrimaryRootDepthState] smoothly during push, pop, and Predictive Back gestures while the heavy
 * primary tabs remain warm behind `NavHost`.
 */
@Composable
fun AnimatedContentScope.PrimaryRootNavProxy(
    depthState: PrimaryRootDepthState,
    disableAnimations: Boolean,
) {
    val animatedPresence =
        transition.animateFloat(
            transitionSpec = {
                when {
                    disableAnimations -> snap()
                    EnterExitState.Visible isTransitioningTo EnterExitState.PostExit ->
                        tween(
                            durationMillis = LunarMotion.DepthZoomExitMs,
                            easing = LunarMotion.SpatialEnterEasing,
                        )
                    else ->
                        tween(
                            durationMillis = LunarMotion.DepthZoomPopEnterMs,
                            easing = LunarMotion.SpatialEnterEasing,
                        )
                }
            },
            label = "primaryRootPresence",
        ) { state ->
            when (state) {
                EnterExitState.Visible -> 1f
                EnterExitState.PreEnter, EnterExitState.PostExit -> 0f
            }
        }

    DisposableEffect(depthState, animatedPresence) {
        depthState.attach(animatedPresence)
        onDispose {
            depthState.detach(animatedPresence)
        }
    }
}

@Stable
private class TabSlotMotion(
    initialVisible: Boolean,
) {
    val alpha = Animatable(if (initialVisible) 1f else 0f)
    val offsetXPx = Animatable(0f)
}

/**
 * Persistent keep-alive host for the main bottom-bar tabs ([Screens.Home], [Screens.Search],
 * [Screens.Library]).
 *
 * Each tab composes lazily on its first visit and stays mounted in composition thereafter with its
 * `RenderNode` display lists, `LazyListState`, `HorizontalPager`, and `ViewModel` flows intact.
 * Inactive tabs are parked off-viewport with `alpha = 0f` on `RenderThread`, incurring zero draw,
 * hit-test, or focus work while eliminating Frame-1 rebuild lag on tab switches and back-swipes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimaryTabsHost(
    tabs: List<Screens>,
    selectedTab: Screens,
    isAtPrimaryRoot: Boolean,
    depthState: PrimaryRootDepthState,
    pureBlack: Boolean,
    disableAnimations: Boolean,
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    homeScrollConnection: NestedScrollConnection?,
    searchScrollConnection: NestedScrollConnection?,
    onOpenSearch: () -> Unit,
    homeScrollToTopTrigger: Int,
    searchScrollToTopTrigger: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val glideDistancePx = with(density) { LunarMotion.TabGlideOffsetDp.dp.toPx() }
    val surfaceColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface

    var visitedRoutes by rememberSaveable { mutableStateOf(listOf(selectedTab.route)) }
    val effectiveVisitedRoutes =
        if (selectedTab.route in visitedRoutes) {
            visitedRoutes
        } else {
            visitedRoutes + selectedTab.route
        }
    SideEffect {
        if (visitedRoutes != effectiveVisitedRoutes) {
            visitedRoutes = effectiveVisitedRoutes
        }
    }

    val slotMotions =
        remember(tabs) {
            tabs.associate { tab ->
                tab.route to TabSlotMotion(initialVisible = tab.route == selectedTab.route)
            }
        }

    var previousSelectedRoute by remember { mutableStateOf(selectedTab.route) }
    var lastOutgoingRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab.route, disableAnimations, glideDistancePx) {
        val targetRoute = selectedTab.route
        val fromRoute = previousSelectedRoute
        if (fromRoute == targetRoute) {
            val activeMotion = slotMotions[targetRoute]
            if (activeMotion != null && activeMotion.alpha.value < 1f) {
                activeMotion.alpha.snapTo(1f)
                activeMotion.offsetXPx.snapTo(0f)
            }
            return@LaunchedEffect
        }

        val fromIndex = tabs.indexOfFirst { it.route == fromRoute }.coerceAtLeast(0)
        val toIndex = tabs.indexOfFirst { it.route == targetRoute }.coerceAtLeast(0)
        val direction = if (toIndex >= fromIndex) 1f else -1f
        lastOutgoingRoute = fromRoute
        previousSelectedRoute = targetRoute

        if (disableAnimations) {
            slotMotions.forEach { (route, motion) ->
                if (route == targetRoute) {
                    motion.alpha.snapTo(1f)
                    motion.offsetXPx.snapTo(0f)
                } else {
                    motion.alpha.snapTo(0f)
                    motion.offsetXPx.snapTo(0f)
                }
            }
            return@LaunchedEffect
        }

        val incomingMotion = slotMotions[targetRoute]
        if (incomingMotion != null) {
            if (incomingMotion.alpha.value <= 0.04f) {
                incomingMotion.offsetXPx.snapTo(direction * glideDistancePx)
            }
            launch {
                incomingMotion.alpha.animateTo(
                    targetValue = 1f,
                    animationSpec =
                        tween(
                            durationMillis = LunarMotion.TabGlideEnterFadeMs,
                            delayMillis = LunarMotion.TabGlideFadeDelayMs,
                            easing = LinearOutSlowInEasing,
                        ),
                )
            }
            launch {
                incomingMotion.offsetXPx.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = LunarMotion.TabGlideEnterMs,
                            easing = LunarMotion.TabGlideEasing,
                        ),
                )
            }
        }

        slotMotions.forEach { (route, motion) ->
            if (route != targetRoute && motion.alpha.value > 0.001f) {
                launch {
                    motion.alpha.animateTo(
                        targetValue = 0f,
                        animationSpec =
                            tween(
                                durationMillis = LunarMotion.TabGlideExitMs,
                                easing = LunarMotion.TabGlideExitEasing,
                            ),
                    )
                    if (selectedTab.route != route) {
                        motion.offsetXPx.snapTo(0f)
                    }
                }
                launch {
                    motion.offsetXPx.animateTo(
                        targetValue = -direction * glideDistancePx * 0.72f,
                        animationSpec =
                            tween(
                                durationMillis = (LunarMotion.TabGlideEnterMs * 3) / 4,
                                easing = LunarMotion.TabGlideExitEasing,
                            ),
                    )
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = depthState.presence.coerceIn(0f, 1f)
                    val visible = p > 0.001f
                    val scale =
                        LunarMotion.DepthRecedeTargetScale +
                            (1f - LunarMotion.DepthRecedeTargetScale) * p
                    scaleX = if (visible) scale else 1f
                    scaleY = if (visible) scale else 1f
                    alpha = if (visible) 1f else 0f
                    translationX = if (visible) 0f else OffscreenParkPx
                    if (visible && p < 0.998f) {
                        shape = DepthRecedeCornerShape
                        clip = true
                    } else {
                        shape = RectangleShape
                        clip = false
                    }
                }.drawWithContent {
                    val p = depthState.presence.coerceIn(0f, 1f)
                    if (p <= 0.001f) return@drawWithContent
                    drawContent()
                    val scrimAlpha = LunarMotion.DepthScrimMaxAlpha * (1f - p)
                    if (scrimAlpha > 0.002f) {
                        drawRect(Color.Black.copy(alpha = scrimAlpha))
                    }
                },
    ) {
        tabs.fastForEach { tab ->
            if (tab.route in effectiveVisitedRoutes) {
                key(tab.route) {
                    val motion = slotMotions.getValue(tab.route)
                    val isTabInteractive = isAtPrimaryRoot && tab.route == selectedTab.route
                    val slotZIndex =
                        when (tab.route) {
                            selectedTab.route -> 2f
                            lastOutgoingRoute -> 1f
                            else -> 0f
                        }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .zIndex(slotZIndex)
                                .focusProperties { canFocus = isTabInteractive }
                                .graphicsLayer {
                                    val hostVisible = depthState.presence > 0.001f
                                    val a = motion.alpha.value.coerceIn(0f, 1f)
                                    val visible = hostVisible && a > 0.001f
                                    alpha = if (visible) a else 0f
                                    translationX = if (visible) motion.offsetXPx.value else OffscreenParkPx
                                    clip = !visible
                                }.background(surfaceColor)
                                .then(
                                    if (!isTabInteractive) {
                                        Modifier.pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    event.changes.fastForEach { it.consume() }
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        when (tab) {
                            Screens.Home -> {
                                HomeScreen(
                                    navController = navController,
                                    headerScrollConnection = homeScrollConnection,
                                    isActiveTab = isTabInteractive,
                                    scrollToTopTrigger = homeScrollToTopTrigger,
                                    viewModel = homeViewModel,
                                )
                            }

                            Screens.Search -> {
                                SearchScreen(
                                    navController = navController,
                                    onSearchClick = onOpenSearch,
                                    headerScrollConnection = searchScrollConnection,
                                    isActiveTab = isTabInteractive,
                                    scrollToTopTrigger = searchScrollToTopTrigger,
                                )
                            }

                            Screens.Library -> {
                                LibraryScreen(
                                    navController = navController,
                                    scrollBehavior = scrollBehavior,
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
