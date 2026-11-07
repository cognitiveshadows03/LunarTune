/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package dev.citali.lunartune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import dev.citali.lunartune.ui.component.PreferenceEntry
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account)) },
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
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
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
                        // Delegate to the full account settings page for the switcher.
                        navController.navigate("settings/account")
                    },
                )
            }

            // Listening stats shortcut.
            item(key = "profile_stats") {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.stats)) },
                    description = stringResource(R.string.stats),
                    icon = { Icon(painterResource(R.drawable.stats), null) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.navigate_next),
                            contentDescription = null,
                        )
                    },
                    onClick = { navController.navigate("stats") },
                )
            }

            // History shortcut.
            item(key = "profile_history") {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.history)) },
                    description = stringResource(R.string.history),
                    icon = { Icon(painterResource(R.drawable.history), null) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.navigate_next),
                            contentDescription = null,
                        )
                    },
                    onClick = { navController.navigate("history") },
                )
            }

            // Version info.
            item(key = "profile_version") {
                PreferenceEntry(
                    title = { Text("LunarTune ${BuildConfig.VERSION_NAME}") },
                    description = if (BuildConfig.DEBUG) "Debug build" else "Release build",
                    icon = { Icon(painterResource(R.drawable.info), null) },
                    onClick = { navController.navigate("settings/about") },
                )
            }

            // Open settings — at the bottom after scrolling.
            item(key = "profile_open_settings") {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.settings)) },
                    description = stringResource(R.string.settings),
                    icon = { Icon(painterResource(R.drawable.settings), null) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.navigate_next),
                            contentDescription = null,
                        )
                    },
                    onClick = { navController.navigate("settings") },
                )
            }
        }
    }
}
