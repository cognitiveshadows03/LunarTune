/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.viewmodels

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.citali.lunartune.BuildConfig
import dev.citali.lunartune.R
import dev.citali.lunartune.currentBuildHash
import javax.inject.Inject

sealed interface AboutScreenState {
    data object Loading : AboutScreenState

    data class Success(
        val model: AboutUiModel,
    ) : AboutScreenState

    data object Empty : AboutScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : AboutScreenState
}

@Immutable
data class AboutUiModel(
    @StringRes val appNameResId: Int,
    val versionName: String,
    val buildHash: String?,
    val buildVariant: String,
    val primaryLinks: AboutLinkCollection,
    val leadDeveloper: TeamMember,
    val collaborators: TeamMemberCollection,
    val respecters: TeamMemberCollection,
)

@Immutable
data class TeamMember(
    val avatarUrl: String,
    val name: String,
    @StringRes val positionResId: Int,
    val profileUrl: String?,
    val links: AboutLinkCollection,
)

@Immutable
data class TeamMemberCollection private constructor(
    private val values: List<TeamMember>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): TeamMember = values[index]

    companion object {
        fun of(vararg values: TeamMember): TeamMemberCollection = TeamMemberCollection(values.toList())
    }
}

@Immutable
data class AboutLinkUiModel(
    val id: String,
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int,
    val url: String,
)

@Immutable
data class AboutLinkCollection private constructor(
    private val values: List<AboutLinkUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): AboutLinkUiModel = values[index]

    fun forEach(action: (AboutLinkUiModel) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutLinkCollection(emptyList())

        fun of(vararg values: AboutLinkUiModel): AboutLinkCollection = AboutLinkCollection(values.toList())
    }
}

sealed interface AboutScreenEffect {
    data class OpenUri(
        val uri: String,
    ) : AboutScreenEffect
}

@HiltViewModel
class AboutViewModel
    @Inject
    constructor() : ViewModel() {
        private val _state = MutableStateFlow<AboutScreenState>(AboutScreenState.Loading)
        val state: StateFlow<AboutScreenState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<AboutScreenEffect>(extraBufferCapacity = 1)
        val effects = _effects.asSharedFlow()


        init {
            updateState()
        }

        fun openUri(uri: String) {
            if (uri.isBlank()) return
            _effects.tryEmit(AboutScreenEffect.OpenUri(uri))
        }

        private fun updateState() {
            _state.value = AboutScreenState.Success(buildUiModel())
        }

        private fun buildUiModel(): AboutUiModel =
            AboutUiModel(
                appNameResId = R.string.app_name,
                versionName = BuildConfig.VERSION_NAME,
                buildHash = currentBuildHash,
                buildVariant = if (BuildConfig.DEBUG) DebugBuildBadge else BuildConfig.ARCHITECTURE.uppercase(),
                primaryLinks =
                    AboutLinkCollection.of(
                        AboutLinkUiModel(
                            id = "github",
                            iconResId = R.drawable.github,
                            labelResId = R.string.about_content_desc_github,
                            url = "https://github.com/cognitiveshadows03/LunarTune",
                        ),
                        AboutLinkUiModel(
                            id = "website",
                            iconResId = R.drawable.website,
                            labelResId = R.string.about_content_desc_website,
                            url = "https://lunartune-03.vercel.app/",
                        ),
                        AboutLinkUiModel(
                            id = "telegram",
                            iconResId = R.drawable.telegram,
                            labelResId = R.string.about_content_desc_telegram,
                            url = "https://t.me/LunarTuneGC",
                        ),
                        AboutLinkUiModel(
                            id = "privacy_policy",
                            iconResId = R.drawable.lock,
                            labelResId = R.string.privacy,
                            url = "https://github.com/cognitiveshadows03/LunarTune/blob/main/PRIVACY.md",
                        ),
                    ),
                leadDeveloper =
                    TeamMember(
                        avatarUrl = "https://avatars.githubusercontent.com/u/273977667?v=4",
                        name = "cognitiveshadows03",
                        positionResId = R.string.about_position_lead_dev,
                        profileUrl = "https://github.com/cognitiveshadows03",
                        links =
                            AboutLinkCollection.of(
                                AboutLinkUiModel(
                                    id = "github",
                                    iconResId = R.drawable.github,
                                    labelResId = R.string.about_content_desc_github,
                                    url = "https://github.com/cognitiveshadows03",
                                ),
                            ),
                    ),
                collaborators =
                    TeamMemberCollection.of(
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/317606351?v=4",
                            name = "TherealCitali",
                            positionResId = R.string.about_position_developers,
                            profileUrl = "https://github.com/TherealCitali",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/TherealCitali",
                                    ),
                                ),
                        ),
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/169815417?v=4",
                            name = "ΛLΞX JΛMΞS ᗪEV",
                            positionResId = R.string.about_position_beta_tester_contributor,
                            profileUrl = "https://github.com/AlexJamesHQ",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/AlexJamesHQ",
                                    ),
                                ),
                        ),
                    ),
                respecters =
                    TeamMemberCollection.of(
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/107134739?v=4",
                            name = "ArchiveTune",
                            positionResId = R.string.about_position_original,
                            profileUrl = "https://github.com/rukamori/ArchiveTune",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/rukamori/ArchiveTune",
                                    ),
                                ),
                        ),
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/u/80542861?v=4",
                            name = "MO AGAMY",
                            positionResId = R.string.about_position_mo_agamy,
                            profileUrl = "https://github.com/mostafaalagamy",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/mostafaalagamy",
                                    ),
                                ),
                        ),
                        TeamMember(
                            avatarUrl = "https://avatars.githubusercontent.com/vivizzz007?v=4",
                            name = "Vivi Music",
                            positionResId = R.string.about_position_vivi,
                            profileUrl = "https://github.com/vivizzz007/vivi-music",
                            links =
                                AboutLinkCollection.of(
                                    AboutLinkUiModel(
                                        id = "github",
                                        iconResId = R.drawable.github,
                                        labelResId = R.string.about_content_desc_github,
                                        url = "https://github.com/vivizzz007/vivi-music",
                                    ),
                                ),
                        ),
                    ),
            )

        private companion object {
            const val DebugBuildBadge = "DEBUG"
        }
    }
