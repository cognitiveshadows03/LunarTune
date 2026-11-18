/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.citali.lunartune.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.*
import moe.rukamori.archivetune.innertube.YouTube
import dev.citali.lunartune.ui.component.EditTextPreference
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.ListPreference
import dev.citali.lunartune.ui.component.PreferenceEntry
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.utils.setAppLocale
import dev.citali.lunartune.viewmodels.AiContentFilterSettingsEffect
import dev.citali.lunartune.viewmodels.AiContentFilterSettingsState
import dev.citali.lunartune.viewmodels.ContentSettingsViewModel
import java.util.Locale

@Composable
fun ContentSettings(
    navController: NavController,
    viewModel: ContentSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val aiContentFilterState by viewModel.aiContentFilterState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, context) {
        viewModel.aiContentFilterEffects.collect { effect ->
            when (effect) {
                is AiContentFilterSettingsEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(effect.messageResId))
                }

                is AiContentFilterSettingsEffect.OpenUrl -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
                }
            }
        }
    }

    val (useSystemLanguage, onUseSystemLanguageChange) = rememberPreference(key = UseSystemLanguageKey, defaultValue = true)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (playlistSuggestionSource, onPlaylistSuggestionSourceChange) =
        rememberEnumPreference(
            key = PlaylistSuggestionSourceKey,
            defaultValue = PlaylistSuggestionSource.BOTH,
        )
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideo, onHideVideoChange) = rememberPreference(key = HideVideoKey, defaultValue = false)
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        PreferenceGroup(title = stringResource(R.string.general)) {
            item {
                ListPreference(
                    title = { Text(stringResource(R.string.content_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    selectedValue = contentLanguage,
                    values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                    valueText = {
                        LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()
                        val languageTag = locale.toLanguageTag().replace("-Hant", "")

                        YouTube.locale =
                            YouTube.locale.copy(
                                hl =
                                    newValue.takeIf { it != SYSTEM_DEFAULT }
                                        ?: locale.language.takeIf { it in LanguageCodeToName }
                                        ?: languageTag.takeIf { it in LanguageCodeToName }
                                        ?: "en",
                            )

                        onContentLanguageChange(newValue)
                    },
                )
            }

            item {
                ListPreference(
                    title = { Text(stringResource(R.string.content_country)) },
                    icon = { Icon(painterResource(R.drawable.location_on), null) },
                    selectedValue = contentCountry,
                    values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                    valueText = {
                        CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { newValue ->
                        val locale = Locale.getDefault()

                        YouTube.locale =
                            YouTube.locale.copy(
                                gl =
                                    newValue.takeIf { it != SYSTEM_DEFAULT }
                                        ?: locale.country.takeIf { it in CountryCodeToName }
                                        ?: "US",
                            )

                        onContentCountryChange(newValue)
                    },
                )
            }

            item {
                ListPreference(
                    title = { Text(stringResource(R.string.you_might_like_source)) },
                    icon = { Icon(painterResource(R.drawable.playlist_play), null) },
                    selectedValue = playlistSuggestionSource,
                    values =
                        listOf(
                            PlaylistSuggestionSource.PLAYLIST_TITLE,
                            PlaylistSuggestionSource.PLAYLIST_CONTENT,
                            PlaylistSuggestionSource.BOTH,
                        ),
                    valueText = {
                        when (it) {
                            PlaylistSuggestionSource.PLAYLIST_TITLE -> stringResource(R.string.playlist_suggestion_source_title)
                            PlaylistSuggestionSource.PLAYLIST_CONTENT -> stringResource(R.string.playlist_suggestion_source_content)
                            PlaylistSuggestionSource.BOTH -> stringResource(R.string.playlist_suggestion_source_both)
                        }
                    },
                    onValueSelected = onPlaylistSuggestionSourceChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    icon = { Icon(painterResource(R.drawable.explicit), null) },
                    checked = hideExplicit,
                    onCheckedChange = onHideExplicitChange,
                )
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.hide_video)) },
                    icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
                    checked = hideVideo,
                    onCheckedChange = onHideVideoChange,
                )
            }
        }

        AiContentFilterPreferences(
            state = aiContentFilterState,
            onEnabledChange = viewModel::setAiContentFilterEnabled,
            onIncludeModerateChange = viewModel::setAiContentFilterIncludeModerate,
            onRefresh = viewModel::refreshAiContentFilter,
            onOpenSource = viewModel::openAiContentFilterSource,
        )

        PreferenceGroup(title = stringResource(R.string.app_language)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.use_system_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    checked = useSystemLanguage,
                    onCheckedChange = { checked ->
                        onUseSystemLanguageChange(checked)
                        val newLocale = if (checked) Locale.getDefault() else Locale.ENGLISH
                        setAppLocale(context, newLocale)
                        (context as? android.app.Activity)?.recreate()
                    },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.misc)) {
            item {
                EditTextPreference(
                    title = { Text(stringResource(R.string.top_length)) },
                    icon = { Icon(painterResource(R.drawable.trending_up), null) },
                    value = lengthTop,
                    isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                    onValueChange = onLengthTopChange,
                )
            }

            item {
                ListPreference(
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                    selectedValue = quickPicks,
                    values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN, QuickPicks.DONT_SHOW),
                    valueText = {
                        when (it) {
                            QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                            QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                            QuickPicks.DONT_SHOW -> stringResource(R.string.dont_show)
                        }
                    },
                    onValueSelected = onQuickPicksChange,
                )
            }
        }
    }

    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.content)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AiContentFilterPreferences(
    state: AiContentFilterSettingsState,
    onEnabledChange: (Boolean) -> Unit,
    onIncludeModerateChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenSource: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.ai_content_filter)) {
        when (state) {
            AiContentFilterSettingsState.Loading -> {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_content_filter)) },
                        description = stringResource(R.string.loading),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                        isEnabled = false,
                    )
                }
            }

            AiContentFilterSettingsState.Empty -> {
                Unit
            }

            is AiContentFilterSettingsState.Error -> {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_content_filter)) },
                        description = stringResource(state.messageResId),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                        onClick = onRefresh,
                    )
                }
            }

            is AiContentFilterSettingsState.Success -> {
                val model = state.model
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ai_content_filter_hide)) },
                        description = stringResource(R.string.ai_content_filter_hide_summary),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                        checked = model.enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ai_content_filter_moderate)) },
                        description = stringResource(R.string.ai_content_filter_moderate_summary),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = model.includeModerateConfidence,
                        onCheckedChange = onIncludeModerateChange,
                        isEnabled = model.enabled,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_content_filter_update)) },
                        description =
                            if (model.refreshing) {
                                stringResource(R.string.loading)
                            } else {
                                stringResource(
                                    R.string.ai_content_filter_list_counts,
                                    model.blocklistCount,
                                    model.warnlistCount,
                                )
                            },
                        icon = { Icon(painterResource(R.drawable.sync), null) },
                        onClick = onRefresh,
                        isEnabled = !model.refreshing,
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_content_filter_source)) },
                        description = stringResource(R.string.ai_content_filter_source_summary),
                        icon = { Icon(painterResource(R.drawable.info), null) },
                        onClick = onOpenSource,
                    )
                }
            }
        }
    }
}
