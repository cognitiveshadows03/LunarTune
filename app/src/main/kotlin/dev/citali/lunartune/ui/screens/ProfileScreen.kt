/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.citali.lunartune.BuildConfig
import dev.citali.lunartune.LocalAnimationsDisabled
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.AccountChannelHandleKey
import dev.citali.lunartune.constants.AccountEmailKey
import dev.citali.lunartune.constants.AccountNameKey
import dev.citali.lunartune.constants.HideStatusBarKey
import dev.citali.lunartune.constants.InnerTubeCookieKey
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.theme.rememberProfileCardColors
import dev.citali.lunartune.ui.screens.settings.AccountSwitcherSheetHost
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.HomeViewModel
import dev.citali.lunartune.viewmodels.ProfileDayListening
import dev.citali.lunartune.viewmodels.ProfileViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie

private val ProfileCardShapeRadius = 28.dp
private val ProfileCardShape = RoundedCornerShape(ProfileCardShapeRadius)
private val ProfileCardPadding = 20.dp

/** Width of the vertical "…'s CARD" ribbon on the identity card. */
private val CardRibbonWidth = 44.dp
private val ProfileNameMinFontSize = 14.sp
private val DotGridStep = 22.dp
private val DotGridRadius = 1.dp

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val accountNameFromViewModel by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val totalListeningTimeMs by profileViewModel.totalListeningTimeMs.collectAsStateWithLifecycle()
    val recentDays by profileViewModel.recentDays.collectAsStateWithLifecycle()
    val totalPlayCount by profileViewModel.totalPlayCount.collectAsStateWithLifecycle()
    val firstListenDate by profileViewModel.firstListenDate.collectAsStateWithLifecycle()

    val (accountNamePref) = rememberPreference(AccountNameKey, "")
    // With the status bar hidden there is nothing to reserve at the top — the
    // screens that pad for it anyway are the ones that leave a blank band the
    // height of the cutout.
    val (hideStatusBar) = rememberPreference(HideStatusBarKey, defaultValue = false)
    val (accountEmail) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")

    val isLoggedIn = hasYouTubeLoginCookie(innerTubeCookie)
    val displayName =
        when {
            accountNameFromViewModel.isNotBlank() -> accountNameFromViewModel
            accountNamePref.isNotBlank() -> accountNamePref
            isLoggedIn -> stringResource(R.string.account)
            else -> stringResource(R.string.login)
        }
    var accountActionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showAccountSwitcher by rememberSaveable { mutableStateOf(false) }

    // Restart the timeout each time the actions are revealed.
    LaunchedEffect(accountActionsExpanded) {
        if (accountActionsExpanded) {
            delay(5_000L)
            accountActionsExpanded = false
        }
    }
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) accountActionsExpanded = false
    }


    // Bottom inset for the mini player — used as content padding so the
    // list scrolls behind the mini player area instead of leaving a
    // blank bar.
    val bottomInset =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    val screenBackground = MaterialTheme.colorScheme.background
    val dotColor = MaterialTheme.colorScheme.onSurface

    // The dot grid is a surface, not wallpaper: it swells under a finger and
    // lifts while the page is scrolling, and every dot it crosses ticks.
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val animationsDisabled = LocalAnimationsDisabled.current
    val dotStepPx = with(LocalDensity.current) { DotGridStep.toPx() }
    val scope = rememberCoroutineScope()
    val dotGlow = remember { Animatable(0f) }
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    var touching by remember { mutableStateOf(false) }
    var lastDotCell by remember { mutableStateOf(-1) }
    var lastTickAt by remember { mutableStateOf(0L) }
    val scrollEnergy by
        animateFloatAsState(
            targetValue = if (listState.isScrollInProgress) 1f else 0f,
            label = "profileDotEnergy",
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(screenBackground)
                .reactiveDotGrid(
                    color = dotColor.copy(alpha = 0.05f),
                    // Read inside the draw pass: the grid then invalidates
                    // only its own drawing when a finger moves, instead of
                    // recomposing the page underneath it.
                    touch = { touchPoint },
                    glow = { dotGlow.value },
                    energy = { scrollEnergy },
                ).pointerInput(dotStepPx, animationsDisabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            if (!change.pressed) {
                                if (touching) {
                                    touching = false
                                    // The light fades on its own after the
                                    // finger has gone, then the position is
                                    // dropped.
                                    scope.launch {
                                        dotGlow.animateTo(0f, tween(durationMillis = 700))
                                        touchPoint = null
                                    }
                                }
                                lastDotCell = -1
                                continue
                            }
                            touchPoint = change.position
                            if (!touching) {
                                touching = true
                                scope.launch {
                                    dotGlow.animateTo(1f, tween(durationMillis = 180))
                                }
                            }
                            val cell =
                                (change.position.x / dotStepPx).toInt() * 10_000 +
                                    (change.position.y / dotStepPx).toInt()
                            if (cell != lastDotCell) {
                                lastDotCell = cell
                                val now = System.currentTimeMillis()
                                // One tick per dot crossed, spaced far enough
                                // apart to feel like texture, not a rattle.
                                if (!animationsDisabled && now - lastTickAt > 80L) {
                                    lastTickAt = now
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            // The default content insets include the display cutout, which is
            // still there once the bar is gone: the greeting would start below
            // an empty strip instead of drawing behind the camera hole.
            contentWindowInsets =
                if (hideStatusBar) {
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                } else {
                    WindowInsets.safeDrawing
                },
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                // Bottom padding accounts for the mini player so the last
                // item can scroll above it — no blank bar, the content
                // fills the screen behind the mini player.
                contentPadding =
                    PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = bottomInset + 32.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "profile_header") {
                    ProfileHeader(
                        isLoggedIn = isLoggedIn,
                        displayName = displayName,
                        onBack = navController::navigateUp,
                        onBackLongClick = navController::backToMain,
                    )
                }

                // The identity card — banner-free card matching the
                // reference layout, with the account actions folded
                // behind the little chevron under it.
                item(key = "profile_identity_card") {
                    ProfileIdentityCard(
                        isLoggedIn = isLoggedIn,
                        displayName = displayName,
                        accountHandle = accountChannelHandle,
                        accountEmail = accountEmail,
                        accountImageUrl = accountImageUrl,
                        firstListenDate = firstListenDate,
                        recentDays = recentDays,
                        actionsExpanded = accountActionsExpanded,
                        onToggleActions = { accountActionsExpanded = !accountActionsExpanded },
                        onPrimaryAction = {
                            if (isLoggedIn) {
                                navController.navigate("account")
                            } else {
                                navController.navigate(buildLoginRoute())
                            }
                        },
                        onSwitchAccount = { showAccountSwitcher = true },
                        onSecondaryAction = {
                            if (isLoggedIn) {
                                navController.navigate("settings/account")
                            } else {
                                navController.navigate("settings/account?openTokenEditor=true")
                            }
                        },
                    )
                }

                // Stats — listening time inline with a "View Stats >" link.
                item(key = "profile_stats") {
                    ProfileListeningTimeCard(
                        totalListeningTimeMs = totalListeningTimeMs,
                        onViewStats = { navController.navigate("stats") },
                    )
                }

                // History — the all-time play count, laid out like the stats card.
                item(key = "profile_history") {
                    ProfileHistoryCard(
                        totalPlayCount = totalPlayCount,
                        onViewHistory = { navController.navigate("history") },
                    )
                }

                // Version info — opens update page; "About app" opens about.
                item(key = "profile_version") {
                    ProfileVersionCard(
                        version = BuildConfig.VERSION_NAME,
                        isDebug = BuildConfig.DEBUG,
                        onClick = { navController.navigate("settings/update") },
                        onAboutClick = { navController.navigate("settings/about") },
                    )
                }

                // Open settings — at the bottom after scrolling. The account
                // and about shortcuts live in the card and the version card, so
                // this is the only one that needs a row of its own.
                item(key = "profile_open_settings") {
                    ProfileActionCard(
                        title = stringResource(R.string.settings),
                        description = stringResource(R.string.profile_settings_description),
                        iconRes = R.drawable.settings,
                        onClick = { navController.navigate("settings") },
                    )
                }

                // Spacer so the last item can scroll above the mini player.
                item(key = "profile_bottom_spacer") {
                    Box(modifier = Modifier.heightIn(min = bottomInset))
                }
            }
        }
    }

    // The same sheet account settings opens, hosted with its own state so
    // switching accounts works from the profile without a detour.
    AccountSwitcherSheetHost(
        visible = showAccountSwitcher,
        onDismiss = { showAccountSwitcher = false },
        onAddAnotherAccount = {
            showAccountSwitcher = false
            navController.navigate(buildLoginRoute())
        },
    )
}

/**
 * Back affordance plus the greeting headline. The screen drops the top
 * app bar so the greeting can breathe the way the reference does.
 */
@Composable
private fun ProfileHeader(
    isLoggedIn: Boolean,
    displayName: String,
    onBack: () -> Unit,
    onBackLongClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onBack,
            onLongClick = onBackLongClick,
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
            )
        }
        Text(
            text =
                if (isLoggedIn) {
                    stringResource(R.string.profile_greeting, displayName)
                } else {
                    stringResource(R.string.profile_welcome)
                },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/**
 * Identity card: a soft card with a vertical ribbon on its right edge,
 * the account's avatar, and the account's name. The chevron under the
 * card folds out the account actions instead of spending card space on
 * them.
 */
@Composable
private fun ProfileIdentityCard(
    isLoggedIn: Boolean,
    displayName: String,
    accountHandle: String,
    accountEmail: String,
    accountImageUrl: String?,
    firstListenDate: LocalDate?,
    recentDays: List<ProfileDayListening>,
    actionsExpanded: Boolean,
    onToggleActions: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
    val cardColors = rememberProfileCardColors(accountImageUrl)
    val accent = cardColors.accent
    val deep = cardColors.deep ?: accent
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    // A tint that fades out of the card towards the ribbon's outer edge. Any
    // flat fill here — opaque or not — draws a vertical seam where the wash
    // behind it changes; a gradient has no edge to give away.
    val ribbonBrush =
        accent?.let {
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, it.copy(alpha = 0.22f)),
            )
        }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.onSurface

    val ribbonText =
        if (accountHandle.isNotBlank()) {
            stringResource(R.string.profile_card_ribbon, displayName, accountHandle)
        } else {
            stringResource(R.string.profile_card_ribbon_solo, displayName)
        }

    val firstListenLabel =
        remember(firstListenDate) {
            firstListenDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }

    val subtitle =
        when {
            firstListenLabel != null ->
                stringResource(R.string.profile_listening_since, firstListenLabel)

            accountEmail.isNotBlank() -> accountEmail
            !isLoggedIn -> stringResource(R.string.not_logged_in)
            else -> null
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .profileCardEntrance(),
            shape = ProfileCardShape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .profileCardWash(accent = accent, deep = deep)
                        .guilloche(accent = accent)
                        .holographicSheen(accent = accent)
                        .dotGrid(color = dotColor.copy(alpha = 0.04f), step = 18.dp, radius = 1.dp),
            ) {
                // IntrinsicSize.Min lets the ribbon stretch to the height
                // the content settles on, so it always spans the card.
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(ProfileCardPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.profile_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.sp,
                                color = onSurfaceVariant,
                            )
                            if (accountHandle.isNotBlank()) {
                                Text(
                                    text = accountHandle,
                                    style = MaterialTheme.typography.labelMedium,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // The whole leftover width: a second weighted
                                    // spacer used to halve it and cut the handle.
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            // The card's chip, sitting in the corner the way a
                            // printed card carries one.
                            CardChip(accent = accent ?: MaterialTheme.colorScheme.primary)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            // The wavy cookie the account screen already uses, a
                            // size up and ringed so it reads as the card's seal.
                            val avatarShape = MaterialShapes.Cookie9Sided.toShape()
                            Surface(
                                modifier = Modifier.size(104.dp),
                                shape = avatarShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border =
                                    BorderStroke(
                                        1.5.dp,
                                        (accent ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.45f),
                                    ),
                            ) {
                                if (isLoggedIn && !accountImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = accountImageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(avatarShape),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    if (isLoggedIn) R.drawable.account else R.drawable.login,
                                                ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_id_card_of),
                                    style = MaterialTheme.typography.labelMedium,
                                    letterSpacing = 1.5.sp,
                                    color = onSurfaceVariant,
                                )
                                // One line, shrunk to fit: the card is the
                                // name's fixed home, so the type gives way
                                // instead of the name wrapping or truncating.
                                ProfileNameText(text = displayName)
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        // The week at a glance: one square per day, tinted by
                        // how much that day was listened to.
                        SevenDayRibbon(days = recentDays, accent = accent)
                    }

                    // The ribbon hugs the card's right edge and runs the full
                    // height of it, carrying the account's name sideways.
                    var ribbonHeightPx by remember { mutableStateOf(0) }
                    Box(
                        modifier =
                            Modifier
                                .width(CardRibbonWidth)
                                .fillMaxHeight()
                                .onSizeChanged { ribbonHeightPx = it.height }
                                .then(
                                    if (ribbonBrush != null) {
                                        Modifier.background(ribbonBrush)
                                    } else {
                                        Modifier
                                    },
                                ).clipToBounds(),
                        contentAlignment = Alignment.Center,
                    ) {
                        RibbonMarquee(
                            text = ribbonText,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            // The caption runs the card's whole height, edge to
                            // edge; the card's own shape clips the ends.
                            availablePx = ribbonHeightPx,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            val chevronRotation by animateFloatAsState(
                targetValue = if (actionsExpanded) 180f else 0f,
                label = "profileActionsChevron",
            )
            IconButton(
                onClick = onToggleActions,
                onLongClick = {},
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
        }

        AnimatedVisibility(
            visible = actionsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onPrimaryAction) {
                    Icon(
                        painter =
                            painterResource(
                                if (isLoggedIn) R.drawable.account else R.drawable.login,
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            if (isLoggedIn) {
                                stringResource(R.string.account)
                            } else {
                                stringResource(R.string.login)
                            },
                    )
                }

                FilledTonalIconButton(onClick = onSwitchAccount) {
                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = stringResource(R.string.saved_accounts),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = onSecondaryAction,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                if (isLoggedIn) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        ),
                ) {
                    Text(
                        text =
                            if (isLoggedIn) {
                                stringResource(R.string.action_logout)
                            } else {
                                stringResource(R.string.advanced_login)
                            },
                    )
                }
            }
        }
    }
}

/**
 * A cleaner action card with an icon, title, description, and chevron.
 */
@Composable
private fun ProfileActionCard(
    title: String,
    description: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ProfileCardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The contact chip a printed card carries, drawn as a small tinted plate with the hairlines a
 * chip has. Purely decorative — it is what makes the card read as a card.
 */
@Composable
private fun CardChip(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // A smart-card chip: a gold metal plate divided by the contact lines a
    // real one carries, rather than a flat square with a cross through it.
    val metal =
        listOf(
            Color(0xFFFCEFB4),
            Color(0xFFD4A437),
            Color(0xFFE8C97A),
        )
    val line = Color(0xFF4A3408).copy(alpha = 0.35f)
    Canvas(
        modifier =
            modifier
                .size(width = 28.dp, height = 22.dp)
                .clip(RoundedCornerShape(5.dp)),
    ) {
        drawRect(brush = Brush.linearGradient(metal))
        val inset = 3.5.dp.toPx()
        val stroke = 0.9.dp.toPx()
        val corner = CornerRadius(2.5.dp.toPx())
        drawRoundRect(
            color = line,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = corner,
            style = Stroke(width = stroke),
        )
        // The contact pattern: one line across the middle, the verticals
        // stopping at it from opposite sides.
        drawLine(
            color = line,
            start = Offset(inset, size.height / 2f),
            end = Offset(size.width - inset, size.height / 2f),
            strokeWidth = stroke,
        )
        drawLine(
            color = line,
            start = Offset(size.width / 3f, inset),
            end = Offset(size.width / 3f, size.height / 2f),
            strokeWidth = stroke,
        )
        drawLine(
            color = line,
            start = Offset(size.width * 2f / 3f, size.height / 2f),
            end = Offset(size.width * 2f / 3f, size.height - inset),
            strokeWidth = stroke,
        )
        // A face highlight so the plate catches the light.
        drawRect(
            brush =
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                ),
            size = Size(size.width, size.height * 0.38f),
        )
    }
}

/**
 * The last seven days as a row of squares, each tinted by how much of that day was listened to.
 * Squares rather than a chart: it survives a screenshot and a thumbnail.
 */
@Composable
private fun SevenDayRibbon(
    days: List<ProfileDayListening>,
    accent: Color?,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val quiet = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val tint = accent ?: MaterialTheme.colorScheme.primary
    val loudest = remember(days) { days.maxOf { it.minutes }.coerceAtLeast(1L) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_last_seven_days),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                val strength = (day.minutes.toFloat() / loudest).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .size(width = 14.dp, height = 14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(lerp(quiet, tint, strength)),
                )
            }
        }
    }
}

/**
 * The card's display name on a single line, scaled down until it fits. A long name in the
 * stock bold face is wider than the column left over beside the avatar and the ribbon, and
 * shrinking reads better there than either wrapping onto a second line or an ellipsis.
 */
@Composable
private fun ProfileNameText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val fontScale = LocalDensity.current.fontScale
    val baseStyle =
        MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

    // The width comes from the text's own layout rather than from a
    // BoxWithConstraints: the card's row asks for intrinsic heights to stretch
    // the ribbon, and a subcomposing layout in that chain throws on the query.
    var availableWidthPx by remember { mutableIntStateOf(0) }

    val fontSize =
        remember(text, availableWidthPx, baseStyle, fontScale) {
            var size = baseStyle.fontSize.value
            if (availableWidthPx > 0) {
                while (size > ProfileNameMinFontSize.value) {
                    val laidOut =
                        measurer.measure(
                            text = AnnotatedString(text),
                            style = baseStyle.copy(fontSize = size.sp),
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(maxWidth = availableWidthPx),
                        )
                    if (!laidOut.hasVisualOverflow) break
                    size -= 0.5f
                }
            }
            size.sp
        }

    Text(
        text = text,
        style = baseStyle,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { availableWidthPx = it.width },
    )
}

/**
 * Listening time card — the total sits left of the label, with a divider
 * and the "View Stats >" link underneath.
 */
@Composable
private fun ProfileListeningTimeCard(
    totalListeningTimeMs: Long,
    onViewStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMinutes = totalListeningTimeMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ProfileCardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ProfileCardPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(modifier = Modifier.weight(1f, fill = false)) {
                    ListeningTimeValue(value = hours, unit = "h")
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .width(1.dp)
                                .height(32.dp)
                                .background(dividerColor),
                    )
                    ListeningTimeValue(value = minutes, unit = "m")
                }
                Text(
                    text = stringResource(R.string.profile_total_listening_time),
                    style = MaterialTheme.typography.labelLarge,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 116.dp).padding(bottom = 4.dp),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 18.dp),
                color = dividerColor,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewStats)
                        .padding(vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_view_stats),
                    style = MaterialTheme.typography.labelLarge,
                    color = onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** One half of the listening total: a large figure with a small unit. */
@Composable
private fun ListeningTimeValue(
    value: Long,
    unit: String,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 1.dp, bottom = 6.dp),
        )
    }
}

/**
 * History card — the all-time play count in the same shape as the listening
 * card: the figure on the left, its label on the right, then a divider and
 * the shortcut into the history list.
 */
@Composable
private fun ProfileHistoryCard(
    totalPlayCount: Int,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plays = remember(totalPlayCount) { NumberFormat.getIntegerInstance().format(totalPlayCount) }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ProfileCardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ProfileCardPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = plays,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.profile_songs_played),
                    style = MaterialTheme.typography.labelLarge,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 116.dp).padding(start = 12.dp, bottom = 4.dp),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 18.dp),
                color = dividerColor,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewHistory)
                        .padding(vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_view_history),
                    style = MaterialTheme.typography.labelLarge,
                    color = onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Version card with a large version number. Clicking the card opens the
 * update page; the "About app" button opens the about page.
 */
@Composable
private fun ProfileVersionCard(
    version: String,
    isDebug: Boolean,
    onClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ProfileCardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ProfileCardPadding),
        ) {
            Text(
                text = stringResource(R.string.profile_version_and_about),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = version,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text =
                            stringResource(
                                if (isDebug) {
                                    R.string.profile_debug_build
                                } else {
                                    R.string.profile_release_build
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onAboutClick,
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.profile_about_app),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * The ribbon's sideways caption. It scrolls when the name and handle are longer than the card
 * is tall, and simply sits there when they fit.
 */
@Composable
private fun RibbonMarquee(
    text: String,
    color: Color,
    availablePx: Int,
    modifier: Modifier = Modifier,
) {
    if (availablePx <= 0) return
    val density = LocalDensity.current
    // Rotated 90°, so the track the caption scrolls along is the ribbon's
    // length, not its width.
    val trackWidth = with(density) { availablePx.toDp() }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                // Order matters here, twice over. The ribbon is only 44 dp wide,
                // so the caption has to be measured unbounded first or the width
                // cap squeezes it into a sliver. And the turn has to come before
                // the marquee, because the marquee clips to its own bounds: turn
                // it afterwards and the clip is applied in the unrotated frame,
                // which slices the sideways line down to a single glyph.
                .wrapContentSize(align = Alignment.Center, unbounded = true)
                .rotate(90f)
                .width(trackWidth)
                .basicMarquee(iterations = Int.MAX_VALUE),
    )
}

/**
 * Washes the identity card in the avatar's own colours, so two accounts never open onto the
 * same card. Fades out towards the bottom-right to keep the type readable.
 */
@Composable
private fun Modifier.profileCardWash(
    accent: Color?,
    deep: Color?,
): Modifier {
    if (accent == null || deep == null) return this
    return drawBehind {
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accent.copy(alpha = 0.34f),
                            deep.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height * 1.15f),
                ),
        )
    }
}

/**
 * A slow band of light crossing the card — the holographic part. Kept very faint and only
 * painted when there is an avatar colour to reflect.
 */
@Composable
private fun Modifier.holographicSheen(accent: Color?): Modifier {
    if (accent == null) return this
    val transition = rememberInfiniteTransition(label = "profileCardSheen")
    val progress by
        transition.animateFloat(
            initialValue = -0.5f,
            targetValue = 1.5f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 5200, easing = LinearEasing),
                ),
            label = "profileCardSheenProgress",
        )
    return drawWithContent {
        drawContent()
        val bandWidth = size.width * 0.5f
        val x = progress * (size.width + bandWidth) - bandWidth
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.09f),
                            Color.Transparent,
                        ),
                    start = Offset(x, 0f),
                    end = Offset(x + bandWidth, size.height),
                ),
        )
    }
}

/**
 * The interlaced linework of a banknote, drawn behind the card's content. Two mirrored families
 * of slow sine waves, faint enough to read as texture and not as decoration.
 */
@Composable
private fun Modifier.guilloche(accent: Color?): Modifier {
    val color = (accent ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.08f)
    return drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        val stroke = Stroke(width = 0.7.dp.toPx())
        val amplitude = size.height * 0.10f
        val rows = 9
        val segments = 36
        var row = 0
        var y = -amplitude
        val step = size.height / rows.toFloat()
        while (y < size.height + amplitude) {
            val phase = (row.toFloat() / rows) * (PI / 3).toFloat()
            val line = Path()
            val mirror = Path()
            for (segment in 0..segments) {
                val x = size.width * segment / segments
                val wave = (sin(x / size.width * 2f * PI.toFloat() + phase) * amplitude)
                if (segment == 0) {
                    line.moveTo(x, y + wave)
                    mirror.moveTo(x, size.height - (y + wave))
                } else {
                    line.lineTo(x, y + wave)
                    mirror.lineTo(x, size.height - (y + wave))
                }
            }
            drawPath(line, color, style = stroke)
            drawPath(mirror, color, style = stroke)
            y += step
            row++
        }
    }
}

/**
 * The card sweeps into place from the top-left when the profile opens — the same gesture a card
 * makes when it is laid on a table. Skipped entirely when animations are switched off.
 */
@Composable
private fun Modifier.profileCardEntrance(): Modifier {
    val animationsDisabled = LocalAnimationsDisabled.current
    val progress = remember { Animatable(if (animationsDisabled) 1f else 0f) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        // Slow, and deliberately under-damped: the card lands
                        // with one soft overshoot and settles, instead of
                        // stopping dead on its anchor.
                        dampingRatio = 0.56f,
                        stiffness = Spring.StiffnessVeryLow * 1.7f,
                    ),
            )
        }
    }

    return graphicsLayer {
        val travelled = progress.value
        val remaining = 1f - travelled
        // The card does not cut the corner: it swings in along a quadratic arc
        // from above-left of its slot, so it reads as being laid down rather
        // than slid in on a diagonal.
        val startX = -150.dp.toPx()
        val startY = -240.dp.toPx()
        val controlX = -26.dp.toPx()
        val controlY = -72.dp.toPx()
        translationX = remaining * remaining * startX + 2f * remaining * travelled * controlX
        translationY = remaining * remaining * startY + 2f * remaining * travelled * controlY
        // Banked into the turn, squaring up as it lands.
        rotationZ = -8f * remaining
        transformOrigin = TransformOrigin(0f, 0f)
        val scale = 0.92f + 0.08f * travelled
        scaleX = scale
        scaleY = scale
        // Fully opaque well before it lands, so the bounce is of the card and
        // not of a ghost.
        alpha = (travelled * 2.4f).coerceIn(0f, 1f)
    }
}

/**
 * The dot grid the screen sits on. It lifts while the page is scrolling and swells around a
 * finger, so the texture answers to the hand instead of sitting there as wallpaper.
 */
private fun Modifier.reactiveDotGrid(
    color: Color,
    touch: () -> Offset?,
    glow: () -> Float,
    energy: () -> Float,
    step: Dp = DotGridStep,
    radius: Dp = DotGridRadius,
): Modifier =
    drawBehind {
        val touchPoint = touch()
        val glowValue = glow()
        val energyValue = energy()
        val stepPx = step.toPx()
        if (stepPx <= 0f) return@drawBehind
        val baseRadius = radius.toPx()
        val pulseRadius = stepPx * 3.4f
        val energyAlpha = (color.alpha * (1f + 1.1f * energyValue)).coerceIn(0f, 1f)
        val energyRadius = baseRadius * (1f + 0.35f * energyValue)

        var y = stepPx / 2f
        while (y < size.height) {
            var x = stepPx / 2f
            while (x < size.width) {
                var dotRadius = energyRadius
                var dotAlpha = energyAlpha
                if (touchPoint != null && glowValue > 0.01f) {
                    val distance = (touchPoint - Offset(x, y)).getDistance()
                    if (distance < pulseRadius) {
                        val closeness = (1f - distance / pulseRadius) * glowValue
                        // Barely more than a lift: the dots answer the finger
                        // without turning into a light show.
                        dotRadius += baseRadius * 1.5f * closeness
                        dotAlpha = (dotAlpha + 0.26f * closeness).coerceIn(0f, 1f)
                    }
                }
                drawCircle(
                    color = color.copy(alpha = dotAlpha),
                    radius = dotRadius,
                    center = Offset(x, y),
                )
                x += stepPx
            }
            y += stepPx
        }
    }

/** A faint dot grid, the texture the reference screen leans on. */
private fun Modifier.dotGrid(
    color: Color,
    step: Dp = 22.dp,
    radius: Dp = 1.dp,
): Modifier =
    drawBehind {
        drawDotGrid(
            color = color,
            step = step.toPx(),
            radius = radius.toPx(),
        )
    }

private fun DrawScope.drawDotGrid(
    color: Color,
    step: Float,
    radius: Float,
) {
    if (step <= 0f) return
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            drawCircle(color = color, radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
    }
}
