/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.citali.lunartune.R
import dev.citali.lunartune.ai.AiLyricsTranslator
import dev.citali.lunartune.ai.AiServiceConfig
import dev.citali.lunartune.constants.AiApiKeyKey
import dev.citali.lunartune.constants.AiApiValidationStatus
import dev.citali.lunartune.constants.AiApiValidationStatusKey
import dev.citali.lunartune.constants.AiCustomEndpointKey
import dev.citali.lunartune.constants.AiCustomModelKey
import dev.citali.lunartune.constants.AiProvider
import dev.citali.lunartune.constants.AiProviderKey
import dev.citali.lunartune.constants.AiSelectedModelKey
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.LyricsEntity
import dev.citali.lunartune.extensions.toEnum
import dev.citali.lunartune.lyrics.LyricsHelper
import dev.citali.lunartune.lyrics.LyricsResult
import dev.citali.lunartune.lyrics.LyricsTranslationBackup
import dev.citali.lunartune.lyrics.LyricsUtils
import dev.citali.lunartune.lyrics.LyricsUtils.displayLyricsText
import dev.citali.lunartune.lyrics.LyricsUtils.isLineSyncedLrc
import dev.citali.lunartune.lyrics.LyricsUtils.isTtml
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.utils.NetworkConnectivityObserver
import dev.citali.lunartune.utils.dataStore
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface LyricsSearchScreenState {
    data object Loading : LyricsSearchScreenState

    @Immutable
    data class Success(
        val results: ImmutableList<LyricsSearchResultUiModel>,
        val isSearching: Boolean,
    ) : LyricsSearchScreenState

    data object Empty : LyricsSearchScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : LyricsSearchScreenState
}

@Immutable
data class LyricsSearchResultUiModel(
    val id: String,
    val providerName: String,
    val lyrics: String,
    val preview: String,
    val lineCount: Int,
    val characterCount: Int,
    val isLineSynced: Boolean,
    val isWordSynced: Boolean,
)

@HiltViewModel
class LyricsMenuViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val lyricsHelper: LyricsHelper,
        val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) : ViewModel() {
        private var job: Job? = null
        private var aiTranslationJob: Job? = null
        private val searchGeneration = AtomicLong(0L)
        private val _lyricsSearchState = MutableStateFlow<LyricsSearchScreenState>(LyricsSearchScreenState.Empty)
        val lyricsSearchState: StateFlow<LyricsSearchScreenState> = _lyricsSearchState.asStateFlow()
        private val _isRefetching = MutableStateFlow(false)
        val isRefetching: StateFlow<Boolean> = _isRefetching.asStateFlow()
        private val _refetchCompletionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val refetchCompletionEvents: SharedFlow<Unit> = _refetchCompletionEvents.asSharedFlow()
        val isAiTranslating = MutableStateFlow(false)

        private val _aiTranslationEvents = MutableSharedFlow<String>()
        val aiTranslationEvents: SharedFlow<String> = _aiTranslationEvents.asSharedFlow()

        private val _isNetworkAvailable = MutableStateFlow(false)
        val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

        init {
            viewModelScope.launch {
                networkConnectivity.networkStatus.collect { isConnected ->
                    _isNetworkAvailable.value = isConnected
                }
            }

            _isNetworkAvailable.value =
                try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }
        }

        fun search(
            mediaId: String,
            title: String,
            artist: String,
            album: String?,
            duration: Int,
        ) {
            val generation = searchGeneration.incrementAndGet()
            job?.cancel()
            _lyricsSearchState.value = LyricsSearchScreenState.Loading
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val resultModels = mutableListOf<LyricsSearchResultUiModel>()
                    try {
                        lyricsHelper.getAllLyrics(
                            mediaId = mediaId,
                            songTitle = title,
                            songArtists = artist,
                            songAlbum = album,
                            duration = duration,
                            forceRefresh = true,
                        ) { result ->
                            if (generation != searchGeneration.get()) return@getAllLyrics
                            val model = result.toUiModel(resultModels.size)
                            if (model.preview.isBlank()) return@getAllLyrics

                            resultModels += model
                            _lyricsSearchState.value =
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = true,
                                )
                        }
                        if (generation != searchGeneration.get()) return@launch
                        _lyricsSearchState.value =
                            if (resultModels.isEmpty()) {
                                LyricsSearchScreenState.Empty
                            } else {
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = false,
                                )
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (generation == searchGeneration.get()) {
                            _lyricsSearchState.value = LyricsSearchScreenState.Error(R.string.error_unknown)
                        }
                    }
                }
        }

        fun cancelSearch() {
            searchGeneration.incrementAndGet()
            job?.cancel()
            job = null
        }

        fun resetSearchState() {
            cancelSearch()
            _lyricsSearchState.value = LyricsSearchScreenState.Empty
        }

        fun refetchLyrics(mediaMetadata: MediaMetadata) {
            if (!_isRefetching.compareAndSet(expect = false, update = true)) return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val lyrics = lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
                    database.withTransaction {
                        replaceLyrics(
                            id = mediaMetadata.id,
                            lyrics = lyrics,
                            source = LyricsEntity.Source.REMOTE.value,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                } finally {
                    _isRefetching.value = false
                    _refetchCompletionEvents.tryEmit(Unit)
                }
            }
        }

        fun updateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            source: LyricsEntity.Source = LyricsEntity.Source.USER_EDIT,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                if (source == LyricsEntity.Source.AI_TRANSLATION) {
                    backupBeforeTranslation(mediaMetadata.id)
                }
                val lyricsToSave =
                    when (source) {
                        LyricsEntity.Source.REMOTE,
                        LyricsEntity.Source.EMBEDDED,
                        LyricsEntity.Source.USER_SELECTION,
                        -> LyricsUtils.lyricsOrNotFound(lyrics)

                        LyricsEntity.Source.USER_EDIT,
                        LyricsEntity.Source.AI_TRANSLATION,
                        -> lyrics
                    }
                database.query {
                    replaceLyrics(
                        id = mediaMetadata.id,
                        lyrics = lyricsToSave,
                        source = source.value,
                    )
                }
            }
        }

        fun translateLyricsWithAi(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            targetLanguage: String,
        ) {
            if (isAiTranslating.value || lyrics.isBlank()) return
            aiTranslationJob =
                viewModelScope.launch(Dispatchers.IO) {
                    isAiTranslating.value = true
                    try {
                        val prefs = context.dataStore.data.first()
                        val translatedLyrics =
                            AiLyricsTranslator().translate(
                                config =
                                    AiServiceConfig(
                                        provider = prefs[AiProviderKey].toEnum(AiProvider.NONE),
                                        apiKey = prefs[AiApiKeyKey].orEmpty(),
                                        customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
                                        model =
                                            if (prefs[AiProviderKey].toEnum(AiProvider.NONE) == AiProvider.CUSTOM) {
                                                prefs[AiCustomModelKey].orEmpty()
                                            } else {
                                                prefs[AiSelectedModelKey].orEmpty()
                                            },
                                    ),
                                lyrics = lyrics,
                                targetLanguage = targetLanguage.ifBlank { "ENGLISH" },
                            )
                        backupBeforeTranslation(mediaMetadata.id)
                        database.query {
                            replaceLyrics(
                                id = mediaMetadata.id,
                                lyrics = translatedLyrics,
                                source = LyricsEntity.Source.AI_TRANSLATION.value,
                            )
                        }
                        val msg = context.getString(R.string.translation_success)
                        _aiTranslationEvents.emit(msg)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = context.getString(R.string.translation_failed) + ": " + (e.localizedMessage ?: e.toString())
                        _aiTranslationEvents.emit(msg)
                    } finally {
                        isAiTranslating.value = false
                        aiTranslationJob = null
                    }
                }
        }

        fun cancelAiTranslation() {
            aiTranslationJob?.cancel()
            aiTranslationJob = null
            isAiTranslating.value = false
        }


        /**
         * AI and standard translation both overwrite the lyrics row, so the text
         * that was there first is copied aside. Without this the original would be
         * unrecoverable and "remove translation" could only refetch from a provider.
         */
        private suspend fun backupBeforeTranslation(mediaId: String) {
            val existing = database.getLyricsById(mediaId) ?: return
            if (existing.source == LyricsEntity.Source.AI_TRANSLATION.value) return
            if (existing.lyrics.isBlank() || existing.lyrics == LyricsEntity.LYRICS_NOT_FOUND) return
            LyricsTranslationBackup.save(
                context = context,
                mediaId = mediaId,
                lyrics = existing.lyrics,
                source = existing.source,
            )
        }

        /**
         * Puts the untranslated lyrics back. When there is no saved copy - a
         * translation made before this was recorded, or one saved by another
         * install - the lyrics are fetched fresh instead.
         */
        fun removeTranslation(mediaMetadata: MediaMetadata) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val saved = LyricsTranslationBackup.load(context, mediaMetadata.id)
                    if (saved != null) {
                        val (lyrics, source) = saved
                        database.query {
                            replaceLyrics(
                                id = mediaMetadata.id,
                                lyrics = lyrics,
                                source = source,
                            )
                        }
                        LyricsTranslationBackup.clear(context, mediaMetadata.id)
                    } else {
                        val lyrics = lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
                        database.query {
                            replaceLyrics(
                                id = mediaMetadata.id,
                                lyrics = lyrics,
                                source = LyricsEntity.Source.REMOTE.value,
                            )
                        }
                    }
                    _aiTranslationEvents.emit(context.getString(R.string.translation_removed))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _aiTranslationEvents.emit(
                        context.getString(R.string.translation_remove_failed) +
                            ": " + (e.localizedMessage ?: e.toString()),
                    )
                }
            }
        }

        private fun LyricsResult.toUiModel(index: Int): LyricsSearchResultUiModel {
            val preview = displayLyricsText(lyrics)
            val lineCount = preview.lineSequence().count { it.isNotBlank() }
            val isTtmlLyrics = isTtml(lyrics)
            val ttmlEntries =
                if (isTtmlLyrics) {
                    runCatching { LyricsUtils.parseTtml(lyrics) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            val isWordSynced = ttmlEntries.any { !it.words.isNullOrEmpty() }

            return LyricsSearchResultUiModel(
                id = "${providerName}_${lyrics.hashCode()}_$index",
                providerName = providerName,
                lyrics = lyrics,
                preview = preview,
                lineCount = lineCount,
                characterCount = preview.length,
                isLineSynced =
                    if (isTtmlLyrics) {
                        ttmlEntries.isNotEmpty() && !isWordSynced
                    } else {
                        isLineSyncedLrc(lyrics)
                    },
                isWordSynced = isWordSynced,
            )
        }
    }
