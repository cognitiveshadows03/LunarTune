/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import dev.citali.lunartune.viewmodels.ProfileViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie

private val ProfileCardShape = RoundedCornerShape(28.dp)
private val ProfileCardPadding = 20.dp

/** Width of the vertical "…'s CARD" ribbon on the identity card. */
private val CardRibbonWidth = 44.dp
private val ProfileGroupItemSpacing = 2.dp
private val ProfileNameMinFontSize = 14.sp

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val accountNameFromViewModel by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val totalListeningTimeMs by profileViewModel.totalListeningTimeMs.collectAsStateWithLifecycle()
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(screenBackground)
                .dotGrid(color = dotColor.copy(alpha = 0.05f)),
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
                            }
                        },
                    )
                }

                // The two listening numbers belong together, so they share a
                // group the way settings stacks its entries under a heading.
                item(key = "profile_listening") {
                    Column(verticalArrangement = Arrangement.spacedBy(ProfileGroupItemSpacing)) {
                        ProfileSectionTitle(stringResource(R.string.profile_section_listening))

                        // Listening time inline with a "View Stats >" link.
                        ProfileListeningTimeCard(
                            totalListeningTimeMs = totalListeningTimeMs,
                            onViewStats = { navController.navigate("stats") },
                        )

                        // The all-time play count, laid out like the card above.
                        ProfileHistoryCard(
                            totalPlayCount = totalPlayCount,
                            onViewHistory = { navController.navigate("history") },
                        )
                    }
                }

                // App info and the way out to settings, grouped the same way.
                item(key = "profile_app") {
                    Column(verticalArrangement = Arrangement.spacedBy(ProfileGroupItemSpacing)) {
                        ProfileSectionTitle(stringResource(R.string.profile_section_app))

                        // Version info — opens update page; "About app" opens about.
                        ProfileVersionCard(
                            version = BuildConfig.VERSION_NAME,
                            isDebug = BuildConfig.DEBUG,
                            onClick = { navController.navigate("settings/update") },
                            onAboutClick = { navController.navigate("settings/about") },
                        )

                        // Open settings — the last card before the spacer.
                        ProfileActionCard(
                            title = stringResource(R.string.settings),
                            description = stringResource(R.string.profile_settings_description),
                            iconRes = R.drawable.settings,
                            onClick = { navController.navigate("settings") },
                        )
                    }
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
    // Room for the caption to end before it runs under the card's corners.
    val ribbonInsetPx =
        with(LocalDensity.current) {
            ProfileCardPadding.roundToPx()
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
            modifier = Modifier.fillMaxWidth(),
            shape = ProfileCardShape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .profileCardWash(accent = accent, deep = deep)
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
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
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
                            availablePx = (ribbonHeightPx - ribbonInsetPx * 2).coerceAtLeast(0),
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

/** The heading settings puts above a group of entries, reused here. */
@Composable
private fun ProfileSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
    )
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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.coerceAtMost(4096)
        val fontSize =
            remember(text, widthPx, baseStyle, fontScale) {
                var size = baseStyle.fontSize.value
                while (size > ProfileNameMinFontSize.value) {
                    val laidOut =
                        measurer.measure(
                            text = AnnotatedString(text),
                            style = baseStyle.copy(fontSize = size.sp),
                            maxLines = 1,
                            softWrap = false,
                            constraints = Constraints(maxWidth = widthPx),
                        )
                    if (!laidOut.hasVisualOverflow) break
                    size -= 0.5f
                }
                size.sp
            }

        Text(
            text = text,
            style = baseStyle,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
