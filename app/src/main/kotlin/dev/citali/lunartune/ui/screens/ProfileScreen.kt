/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.citali.lunartune.BuildConfig
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.AccountChannelHandleKey
import dev.citali.lunartune.constants.AccountEmailKey
import dev.citali.lunartune.constants.AccountNameKey
import dev.citali.lunartune.constants.InnerTubeCookieKey
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.screens.settings.AccountSummaryCard
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.AccountChannelsState
import dev.citali.lunartune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val accountNameFromViewModel by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountChannelsState by viewModel.accountChannelsState.collectAsStateWithLifecycle()

    val (accountNamePref) = rememberPreference(AccountNameKey, "")
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
    val hasSwitchableChannels =
        (accountChannelsState as? AccountChannelsState.Success)
            ?.channels
            ?.items
            ?.size
            .let { (it ?: 0) > 1 }

    // Bottom inset for the mini player — used as content padding so the
    // list scrolls behind the mini player area instead of leaving a
    // blank bar.
    val bottomInset =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isLoggedIn) {
                            "Good to see you, $displayName!"
                        } else {
                            "Welcome to LunarTune"
                        },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
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
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The pre-existing account card — banner, avatar, name, login/logout.
            item(key = "profile_account_card") {
                AccountSummaryCard(
                    isLoggedIn = isLoggedIn,
                    accountName = displayName,
                    accountEmail = accountEmail,
                    accountHandle = accountChannelHandle,
                    accountImageUrl = accountImageUrl,
                    accountSwitcherEnabled =
                        isLoggedIn || hasSwitchableChannels,
                    onPrimaryAction = {
                        if (isLoggedIn) {
                            navController.navigate("account")
                        } else {
                            navController.navigate(buildLoginRoute())
                        }
                    },
                    onSecondaryAction = {
                        if (isLoggedIn) {
                            navController.navigate("settings/account")
                        }
                    },
                    onOpenAccountSwitcher = {
                        navController.navigate("settings/account")
                    },
                )
            }

            // Stats — inline card with a "View Stats >" action.
            item(key = "profile_stats") {
                ProfileActionCard(
                    title = stringResource(R.string.stats),
                    description = "View your listening statistics",
                    iconRes = R.drawable.stats,
                    onClick = { navController.navigate("stats") },
                )
            }

            // History shortcut.
            item(key = "profile_history") {
                ProfileActionCard(
                    title = stringResource(R.string.history),
                    description = "Recently played tracks",
                    iconRes = R.drawable.history,
                    onClick = { navController.navigate("history") },
                )
            }

            // Version info — large version number, links to About.
            item(key = "profile_version") {
                ProfileVersionCard(
                    version = BuildConfig.VERSION_NAME,
                    isDebug = BuildConfig.DEBUG,
                    onClick = { navController.navigate("settings/about") },
                )
            }

            // Open settings — at the bottom after scrolling.
            item(key = "profile_open_settings") {
                ProfileActionCard(
                    title = stringResource(R.string.settings),
                    description = "App preferences and more",
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

/**
 * A cleaner action card with an icon, title, description, and chevron.
 * Uses surfaceContainerLow for tonal depth instead of a flat row.
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
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
                modifier = Modifier
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
 * Version card with a large version number — inspired by the reference
 * design's "Version & about app" card.
 */
@Composable
private fun ProfileVersionCard(
    version: String,
    isDebug: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
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
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    text = version.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Version & about app",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$version ${if (isDebug) "· Debug" else "· Release"}",
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
