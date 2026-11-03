/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import dev.citali.lunartune.aicontentfilter.FilterAiContentUseCase
import dev.citali.lunartune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import dev.citali.lunartune.constants.HideExplicitKey
import dev.citali.lunartune.constants.HideVideoKey
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get
import dev.citali.lunartune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        private val browseId = savedStateHandle.get<String>("browseId")!!
        private val params = savedStateHandle.get<String>("params")

        val result = MutableStateFlow<BrowseResult?>(null)

        /**
         * The personalised mixes the Home category chips used to surface. They now
         * live at the top of a mood/genre page instead of behind a chip on Home.
         * Kept as its own flow so a slow chip lookup cannot hold up the shelves.
         */
        val mixedForYou = MutableStateFlow<List<YTItem>>(emptyList())

        init {
            viewModelScope.launch {
                mixedForYou.value = runCatching { loadMixedForYou() }.getOrDefault(emptyList())
            }
            viewModelScope.launch {
                YouTube
                    .browse(browseId, params)
                    .onSuccess {
                        val hideVideo = context.dataStore.get(HideVideoKey, false)
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val contentFilteredResult =
                            it
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(hideVideo)
                                .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                        result.value =
                            contentFilteredResult.copy(
                                items =
                                    contentFilteredResult.items.mapNotNull { section ->
                                        section
                                            .copy(items = filterAiContent(section.items, aiContentFilterPolicy))
                                            .takeIf { filteredSection -> filteredSection.items.isNotEmpty() }
                                    },
                            )
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        /**
         * Each Home chip is really a filtered Home feed whose first shelf is the
         * personalised mixes ("Mixed for you"). Fetch a few of them in parallel and
         * flatten those shelves into one row, so the mixes stay reachable after the
         * chips themselves were removed from Home.
         */
        private suspend fun loadMixedForYou(): List<YTItem> {
            cachedMixedForYou?.let { cached -> return cached }

            val chips =
                YouTube
                    .home()
                    .getOrNull()
                    ?.chips
                    .orEmpty()
                    .filterNot { chip -> chip.title.contains("podcasts", ignoreCase = true) }
                    .filter { chip -> !chip.endpoint?.params.isNullOrBlank() }
                    .take(MaxMixedForYouChips)
            if (chips.isEmpty()) return emptyList()

            val pages =
                supervisorScope {
                    chips
                        .map { chip ->
                            async { YouTube.home(params = chip.endpoint?.params).getOrNull() }
                        }.awaitAll()
                }

            val mixes =
                pages
                    .flatMap { page ->
                        val sections = page?.sections.orEmpty()
                        val mixedShelf =
                            sections.firstOrNull { section ->
                                section.title.contains("mixed for you", ignoreCase = true)
                            } ?: sections.firstOrNull { section ->
                                section.title.contains("mix", ignoreCase = true)
                            } ?: sections.firstOrNull()
                        mixedShelf?.items.orEmpty().take(MixesPerChip)
                    }.distinctBy(YTItem::id)
            if (mixes.isEmpty()) return emptyList()

            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val blockedArtistIds = database.getBlockedArtistIds().toSet()
            val aiContentFilterPolicy = loadAiContentFilterPolicy()
            return filterAiContent(
                mixes
                    .filterExplicit(hideExplicit)
                    .filterVideo(hideVideo)
                    .filterBlockedArtists(blockedArtistIds),
                aiContentFilterPolicy,
            ).take(MaxMixedForYouItems).also { cached ->
                if (cached.isNotEmpty()) cachedMixedForYou = cached
            }
        }

        private companion object {
            const val MaxMixedForYouChips = 4
            const val MixesPerChip = 3
            const val MaxMixedForYouItems = 12

            /** One process-wide lookup: the same mixes are shown on every mood page. */
            @Volatile
            var cachedMixedForYou: List<YTItem>? = null
        }
    }
