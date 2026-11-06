/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.viewmodels.AboutLinkCollection
import dev.citali.lunartune.viewmodels.AboutScreenEffect
import dev.citali.lunartune.viewmodels.AboutScreenState
import dev.citali.lunartune.viewmodels.AboutUiModel
import dev.citali.lunartune.viewmodels.AboutViewModel
import dev.citali.lunartune.viewmodels.TeamMember
import dev.citali.lunartune.viewmodels.TeamMemberCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel, uriHandler) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutScreenEffect.OpenUri -> uriHandler.openUri(effect.uri)
            }
        }
    }

    AboutScreenContent(
        state = state,
        onNavigateUp = navController::navigateUp,
        onNavigateHome = navController::backToMain,
        onOpenUri = viewModel::openUri,
    )
}

@Composable
private fun AboutScreenContent(
    state: AboutScreenState,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // iOS-style rubber band: the whole screen follows the finger past the
    // scroll edges and springs back when released.
    val overscroll = remember { Animatable(0f) }
    val overscrollConnection =
        remember(listState, scope) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        scope.launch {
                            overscroll.snapTo(
                                (overscroll.value + available.y * 0.45f).coerceIn(-180f, 180f),
                            )
                        }
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    overscroll.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                    return Velocity.Zero
                }
            }
        }

    val overscrollPx by overscroll.asState()
    val topInset =
        WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top)
            .asPaddingValues()
            .calculateTopPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AboutColors.background)
                .nestedScroll(overscrollConnection),
    ) {
        // Single scrolling surface. The identity hero lives inside the
        // LazyColumn as its first item and fades+scales away as you scroll.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = overscrollPx },
        ) {
            when (state) {
                AboutScreenState.Loading -> {
                    AboutLoadingContent(modifier = Modifier.fillMaxSize())
                }

                AboutScreenState.Empty -> {
                    AboutMessageContent(
                        message = stringResource(R.string.no_results_found),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is AboutScreenState.Error -> {
                    AboutMessageContent(
                        message = stringResource(state.messageResId),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is AboutScreenState.Success -> {
                    AboutSuccessContent(
                        model = state.model,
                        onOpenUri = onOpenUri,
                        topInset = topInset,
                        modifier = Modifier.fillMaxSize(),
                        listState = listState,
                    )
                }
            }
        }

        // Simple top row, like the reference About screen.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .graphicsLayer { translationY = overscrollPx * 0.35f },
        ) {
            IconButton(
                onClick = onNavigateUp,
                onLongClick = onNavigateHome,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AboutColors.TopBarScrim),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                    tint = AboutColors.TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AboutColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun AboutLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun AboutMessageContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(AboutSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = AboutColors.TextSecondary,
        )
    }
}

@Composable
private fun AboutSuccessContent(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    topInset: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    // Identity hero height in pixels, measured once it is laid out, so we
    // can derive a 0..1 scroll progress for the parallax fade+scale.
    var identityHeightPx by remember { mutableIntStateOf(0) }
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0 && identityHeightPx > 0) {
                (listState.firstVisibleItemScrollOffset.toFloat() / identityHeightPx).coerceIn(0f, 1f)
            } else if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                0f
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding =
            PaddingValues(
                top = topInset + 56.dp,
                bottom = SettingsDimensions.ScreenBottomPadding + 40.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.md),
    ) {
        item(key = "identity", contentType = "about_identity") {
            AboutIdentityHero(
                appName = stringResource(model.appNameResId),
                versionName = model.versionName,
                scrollProgress = scrollProgress,
                onHeightMeasured = { px -> identityHeightPx = px },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "gpl_license", contentType = "about_gpl_license") {
            GplLicenseCard(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "about_links", contentType = "about_links") {
            AboutLinksCard(
                model = model,
                onOpenUri = onOpenUri,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "lead_developer", contentType = "about_team_section") {
            TeamMemberSection(
                title = stringResource(R.string.about_lead_developer),
                members = TeamMemberCollection.of(model.leadDeveloper),
                onOpenUri = onOpenUri,
                prominentFirstItem = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "team", contentType = "about_team_section") {
            TeamMemberSection(
                title = stringResource(R.string.about_archive_tune_team),
                members = model.collaborators,
                onOpenUri = onOpenUri,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "respecters", contentType = "about_team_section") {
            TeamMemberSection(
                title = stringResource(R.string.about_respecter),
                members = model.respecters,
                onOpenUri = onOpenUri,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "legal_notice", contentType = "about_legal_notice") {
            LegalNoticeCard(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AboutIdentityHero(
    appName: String,
    versionName: String,
    scrollProgress: Float,
    onHeightMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Parallax: as the list scrolls, the hero fades out, shrinks slightly,
    // and drifts up — so the identity recedes behind the links card and
    // disappears by the time the card reaches the top, matching the
    // reference About screen.
    val fade = 1f - scrollProgress
    val scale = 1f - scrollProgress * 0.25f
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { size -> onHeightMeasured(size.height) }
                .graphicsLayer {
                    alpha = fade
                    scaleX = scale
                    scaleY = scale
                    translationY = -scrollProgress * 80f
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(120.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.about_splash),
                contentDescription = appName,
                modifier = Modifier.padding(24.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = appName,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = AboutColors.TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = AboutColors.TextSecondary,
        )
        Spacer(Modifier.height(AboutSpacing.md))
    }
}

@Composable
private fun GplLicenseCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = AboutColors.Container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = AboutColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.about_gpl_license_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = AboutColors.TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.about_gpl_license_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AboutColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.about_gpl_license_view_full),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clickable {
                            uriHandler.openUri("https://github.com/cognitiveshadows03/LunarTune/blob/main/LICENSE")
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AboutLinksCard(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wineTint = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = AboutColors.Container,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
        ) {
            val links = model.primaryLinks
            repeat(links.size) { index ->
                val link = links[index]
                AboutLinkRow(
                    iconResId = link.iconResId,
                    label = stringResource(link.labelResId),
                    tint = wineTint,
                    onClick = { onOpenUri(link.url) },
                )
                if (index < links.size - 1) {
                    HorizontalDivider(
                        color = AboutColors.Divider,
                        modifier = Modifier.padding(start = 62.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLinkRow(
    iconResId: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint),
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = AboutColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = AboutColors.TextSecondary,
        )
    }
}

@Composable
private fun TeamMemberSection(
    title: String,
    members: TeamMemberCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
    prominentFirstItem: Boolean = false,
) {
    // Wrap every section in the same card style as the links card so all
    // blocks on the About screen share the same width, shape, and colour.
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = AboutColors.Container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
        ) {
            AboutSectionHeader(title = title)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                repeat(members.size) { index ->
                    TeamMemberListItem(
                        member = members[index],
                        index = index,
                        itemCount = members.size,
                        onOpenUri = onOpenUri,
                        avatarSize = if (prominentFirstItem && index == 0) 64.dp else 52.dp,
                        minHeight = if (prominentFirstItem && index == 0) 96.dp else 80.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalNoticeCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = AboutColors.Container,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AboutSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = AboutColors.TextSecondary,
                )
                Text(
                    text = stringResource(R.string.about_legal_notice),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = AboutColors.TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.about_legal_notice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AboutColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun AboutSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = AboutColors.TextPrimary,
        modifier =
            modifier.padding(
                horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                vertical = SettingsDimensions.SectionHeaderBottomPadding,
            ),
    )
}

@Composable
private fun TeamMemberListItem(
    member: TeamMember,
    index: Int,
    itemCount: Int,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp,
    minHeight: Dp,
) {
    val onClick: () -> Unit =
        remember(member.profileUrl, onOpenUri) {
            {
                val profileUrl = member.profileUrl
                if (!profileUrl.isNullOrBlank()) {
                    onOpenUri(profileUrl)
                }
            }
        }

    SegmentedListItem(
        onClick = onClick,
        shapes =
            if (itemCount == 1) {
                ListItemDefaults.shapes(
                    shape = MaterialTheme.shapes.extraLarge,
                )
            } else {
                ListItemDefaults.segmentedShapes(index = index, count = itemCount)
            },
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
        colors = AboutListItemDefaults.colors(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = member.name,
                modifier =
                    Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(AboutColors.ContainerHighest),
            )
        },
        supportingContent = {
            Text(
                text = stringResource(member.positionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = AboutColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            MemberLinkActions(
                links = member.links,
                onOpenUri = onOpenUri,
            )
        },
        content = {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = AboutColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberLinkActions(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.widthIn(max = 156.dp),
        horizontalArrangement = Arrangement.spacedBy(AboutSpacing.xxs, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(AboutSpacing.xxs),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val onClick = remember(link.url, onOpenUri) { { onOpenUri(link.url) } }

            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(link.iconResId),
                    contentDescription = stringResource(link.labelResId),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutLeadingIcon(
    iconResId: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier =
                Modifier
                    .padding(AboutSpacing.sm)
                    .size(20.dp),
        )
    }
}

private object AboutColors {
    // Screen background — black in dark mode, surface in light mode.
    val background: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface

    // Card / row containers.
    val Container: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1F1A1B) else MaterialTheme.colorScheme.surfaceContainerLow
    val ContainerHigh: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A2326) else MaterialTheme.colorScheme.surfaceContainerHigh
    val ContainerHighest: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF362D30) else MaterialTheme.colorScheme.surfaceContainerHighest

    // Text
    val TextPrimary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
    val TextSecondary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant
    val TextTertiary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.40f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    // Misc
    val Divider: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val TopBarScrim: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    val AccentIconTint: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
}

private object AboutDimensions {
    val DialogContentMaxWidth = 920.dp
    val DialogListPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
}

private object AboutSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
}

private object AboutListItemDefaults {
    @Composable
    fun colors() =
        ListItemDefaults.colors(
            containerColor = AboutColors.Container,
            headlineColor = AboutColors.TextPrimary,
            overlineColor = AboutColors.TextSecondary,
            supportingColor = AboutColors.TextSecondary,
            leadingIconColor = AboutColors.TextPrimary,
            trailingIconColor = AboutColors.TextSecondary,
        )
}

private val NoOpAction: () -> Unit = {}
