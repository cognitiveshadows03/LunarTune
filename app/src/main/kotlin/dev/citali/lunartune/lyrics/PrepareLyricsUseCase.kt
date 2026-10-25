/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import com.google.common.collect.ImmutableList
import dev.citali.lunartune.db.entities.LyricsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.betterlyrics.QRCParser
import moe.rukamori.archivetune.betterlyrics.TTMLParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PrepareLyricsUseCase
    @Inject
    constructor(
        private val repository: LyricsRenderingRepository,
    ) {
        private data class BaseCacheKey(
            val source: String,
            val durationBucket: Long,
            val romanization: LyricsRomanizationPreferences,
            val japaneseLanguagePackInstalled: Boolean,
        )

        private data class PreparedBase(
            val sourceFormat: LyricsSourceFormat,
            val syncType: LyricsSyncType,
            val lines: ImmutableList<PreparedLyricsLine>,
        )

        private data class CachedMediaPrepared(
            val durationMs: Long,
            val prepared: PreparedLyrics,
        )

        private val baseCache =
            object : LinkedHashMap<BaseCacheKey, PreparedBase>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BaseCacheKey, PreparedBase>?): Boolean =
                    size > MAX_BASE_CACHE_ENTRIES
            }

        private val latestByMediaId =
            object : LinkedHashMap<String, CachedMediaPrepared>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMediaPrepared>?): Boolean =
                    size > MAX_MEDIA_CACHE_ENTRIES
            }

        suspend fun setShowTranslations(show: Boolean) {
            repository.setShowTranslations(show)
        }

        fun peekCached(
            mediaId: String,
            durationMs: Long,
        ): PreparedLyrics? =
            synchronized(latestByMediaId) {
                val cached = latestByMediaId[mediaId] ?: return@synchronized null
                val matchesDuration =
                    cached.prepared.sourceFormat != LyricsSourceFormat.LRC ||
                        durationMs <= 0L ||
                        (cached.durationMs / 1000L) == (durationMs / 1000L)
                cached.prepared.takeIf { matchesDuration }
            }

        fun observe(
            mediaId: String,
            durationMs: Long,
        ): Flow<Result<PreparedLyrics?>> =
            combine(
                repository.observeLyrics(mediaId),
                repository.observePreferences(),
            ) { entity, preferences -> entity to preferences }
                .mapLatest { (entity, preferences) ->
                    tryResolveCached(mediaId, entity, durationMs, preferences)?.let { cached ->
                        return@mapLatest Result.success(cached)
                    }
                    withContext(Dispatchers.Default) {
                        prepare(mediaId, entity, durationMs, preferences)
                    }
                }

        private fun resolveEffectivePreferences(
            entity: LyricsEntity,
            preferences: LyricsRenderingPreferences,
        ): LyricsRenderingPreferences =
            if (entity.source == LyricsEntity.Source.AI_TRANSLATION.value && !preferences.showTranslations) {
                preferences.copy(showTranslations = true)
            } else {
                preferences
            }

        private fun resolveSourceText(storedLyrics: String): String =
            if (RAW_TTML_ROOT_REGEX.containsMatchIn(storedLyrics.take(TTML_ROOT_SCAN_LENGTH))) {
                storedLyrics.removePrefix("\uFEFF").trimStart()
            } else {
                LyricsUtils.normalizeLyricsText(storedLyrics)
            }

        private fun tryResolveCached(
            mediaId: String,
            entity: LyricsEntity?,
            durationMs: Long,
            preferences: LyricsRenderingPreferences,
        ): PreparedLyrics? {
            if (entity == null) return null
            val effectivePreferences = resolveEffectivePreferences(entity, preferences)
            val source = resolveSourceText(entity.lyrics)
            if (source.isBlank() || source == LyricsEntity.LYRICS_NOT_FOUND) {
                val empty =
                    PreparedLyrics(
                        sourceFormat = LyricsSourceFormat.PLAIN,
                        syncType = LyricsSyncType.PLAIN,
                        lines = ImmutableList.of(),
                        preferences = effectivePreferences,
                    )
                cacheForMedia(mediaId, durationMs, empty)
                return empty
            }
            val key = buildBaseCacheKey(source, durationMs, effectivePreferences)
            val cachedBase = synchronized(baseCache) { baseCache[key] } ?: return null
            val prepared =
                PreparedLyrics(
                    sourceFormat = cachedBase.sourceFormat,
                    syncType = cachedBase.syncType,
                    lines = cachedBase.lines,
                    preferences = effectivePreferences,
                )
            cacheForMedia(mediaId, durationMs, prepared)
            return prepared
        }

        private fun buildBaseCacheKey(
            source: String,
            durationMs: Long,
            preferences: LyricsRenderingPreferences,
        ): BaseCacheKey {
            val isLrc = !LyricsUtils.isTtml(source) && LyricsUtils.isLineSyncedLrc(source)
            val durationBucket = if (isLrc) durationMs.coerceAtLeast(0L) / 1000L else 0L
            return BaseCacheKey(
                source = source,
                durationBucket = durationBucket,
                romanization = preferences.romanization,
                japaneseLanguagePackInstalled = preferences.japaneseLanguagePackInstalled,
            )
        }

        private fun cacheForMedia(
            mediaId: String,
            durationMs: Long,
            prepared: PreparedLyrics,
        ) {
            synchronized(latestByMediaId) {
                latestByMediaId[mediaId] = CachedMediaPrepared(durationMs.coerceAtLeast(0L), prepared)
            }
        }

        private suspend fun prepare(
            mediaId: String,
            entity: LyricsEntity?,
            durationMs: Long,
            preferences: LyricsRenderingPreferences,
        ): Result<PreparedLyrics?> {
            if (entity == null) {
                synchronized(latestByMediaId) {
                    latestByMediaId.remove(mediaId)
                }
                return Result.success(null)
            }
            val effectivePreferences = resolveEffectivePreferences(entity, preferences)
            val source = resolveSourceText(entity.lyrics)
            if (source.isBlank() || source == LyricsEntity.LYRICS_NOT_FOUND) {
                val empty =
                    PreparedLyrics(
                        sourceFormat = LyricsSourceFormat.PLAIN,
                        syncType = LyricsSyncType.PLAIN,
                        lines = ImmutableList.of(),
                        preferences = effectivePreferences,
                    )
                cacheForMedia(mediaId, durationMs, empty)
                return Result.success(empty)
            }

            return try {
                val key = buildBaseCacheKey(source, durationMs, effectivePreferences)
                val cachedBase = synchronized(baseCache) { baseCache[key] }
                val prepared =
                    if (cachedBase != null) {
                        PreparedLyrics(
                            sourceFormat = cachedBase.sourceFormat,
                            syncType = cachedBase.syncType,
                            lines = cachedBase.lines,
                            preferences = effectivePreferences,
                        )
                    } else {
                        val freshlyPrepared =
                            when {
                                LyricsUtils.isTtml(source) -> prepareTtml(source, effectivePreferences)
                                LyricsUtils.isLineSyncedLrc(source) -> prepareSyncedText(source, durationMs, effectivePreferences)
                                else -> preparePlainText(source, effectivePreferences)
                            }
                        synchronized(baseCache) {
                            baseCache[key] =
                                PreparedBase(
                                    sourceFormat = freshlyPrepared.sourceFormat,
                                    syncType = freshlyPrepared.syncType,
                                    lines = freshlyPrepared.lines,
                                )
                        }
                        freshlyPrepared
                    }
                cacheForMedia(mediaId, durationMs, prepared)
                Result.success(prepared)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Result.failure(throwable)
            }
        }

        private suspend fun prepareTtml(
            source: String,
            preferences: LyricsRenderingPreferences,
        ): PreparedLyrics {
            val normalized = LyricsUtils.normalizeLyricsText(source)
            val parsedLines = TTMLParser.parseTTML(normalized)
            val hasWords = parsedLines.any { line -> line.words.any { word -> word.text.isNotEmpty() } }
            val lines =
                parsedLines.mapIndexed { index, line ->
                    line.toPreparedLine(index, preferences.romanization)
                }
            return PreparedLyrics(
                sourceFormat = LyricsSourceFormat.TTML,
                syncType = if (hasWords) LyricsSyncType.WORD else LyricsSyncType.LINE,
                lines = ImmutableList.copyOf(lines),
                preferences = preferences,
            )
        }

        private suspend fun TTMLParser.ParsedLine.toPreparedLine(
            sourceOrder: Int,
            romanizationPreferences: LyricsRomanizationPreferences,
        ): PreparedLyricsLine {
            val startMs = startTime.toMilliseconds()
            val nonEmptyWords = words.filter { word -> word.text.isNotEmpty() }
            val mainWords =
                nonEmptyWords.filterNot(TTMLParser.ParsedWord::isBackground).map { word ->
                    val wordStartMs = word.startTime.toMilliseconds()
                    PreparedLyricsWord(
                        text = word.text,
                        startMs = wordStartMs,
                        endMs = word.endTime.toMilliseconds().coerceAtLeast(wordStartMs + 1L),
                    )
                }
            val backgroundWords =
                nonEmptyWords.filter(TTMLParser.ParsedWord::isBackground).map { word ->
                    val wordStartMs = word.startTime.toMilliseconds()
                    PreparedLyricsWord(
                        text = word.text,
                        startMs = wordStartMs,
                        endMs = word.endTime.toMilliseconds().coerceAtLeast(wordStartMs + 1L),
                    )
                }

            val mainTrackText =
                if (mainWords.isNotEmpty()) {
                    mainWords.joinToString(separator = "") { word -> word.text }
                } else {
                    text
                }
            val mainTrack =
                PreparedLyricsTrack(
                    text = mainTrackText,
                    language = null,
                    words = ImmutableList.copyOf(mainWords),
                )
            val backgroundTracks =
                if (backgroundWords.isEmpty()) {
                    ImmutableList.of()
                } else {
                    ImmutableList.of(
                        PreparedLyricsTrack(
                            text = backgroundWords.joinToString(separator = "") { word -> word.text },
                            language = null,
                            words = ImmutableList.copyOf(backgroundWords),
                        ),
                    )
                }

            val endMs =
                endTime
                    .toMilliseconds()
                    .coerceAtLeast(
                        (mainWords + backgroundWords).maxOfOrNull(PreparedLyricsWord::endMs) ?: (startMs + 1L),
                    ).coerceAtLeast(startMs + 1L)

            val providerRomanization =
                providerRomanizedText
                    ?.normalizedSupplementaryText()
                    ?.takeIf {
                        LyricsUtils.shouldUseProvidedRomanization(
                            originalText = text,
                            providerRomanizedText = it,
                            providerRomanizedLanguage = providerRomanizedLanguage,
                            preferences = romanizationPreferences,
                        )
                    }
            val cleanedProviderWords =
                providerRomanizedWords
                    ?.map { word -> word.normalizedSupplementaryText() }
                    ?.filter(String::isNotEmpty)
            val romanizedWords =
                when {
                    providerRomanization != null &&
                        cleanedProviderWords != null &&
                        cleanedProviderWords.size == mainTrack.words.size -> {
                        cleanedProviderWords
                    }

                    romanizationPreferences.isEnabled && mainTrack.words.isNotEmpty() -> {
                        mainTrack.words.map { word ->
                            LyricsUtils.romanizeLyricsWordWithLineContext(word.text, text, romanizationPreferences)
                        }
                    }

                    else -> emptyList()
                }
            val lineRomanization =
                providerRomanization
                    ?: LyricsUtils.romanizeLyricsLine(text, romanizationPreferences)
            val phonetics =
                if (romanizedWords.size == mainTrack.words.size) {
                    romanizedWords.mapIndexedNotNull { wordIndex, value ->
                        value?.takeIf(String::isNotBlank)?.let { phoneticText ->
                            PreparedLyricsPhonetic(wordIndex, phoneticText)
                        }
                    }
                } else {
                    emptyList()
                }
            val normalizedTranslation =
                providerTranslationText
                    ?.normalizedSupplementaryText()
                    ?.takeIf { it.isNotEmpty() && !it.equals(text.trim(), ignoreCase = true) }

            return PreparedLyricsLine(
                id = "ttml:$sourceOrder:$startMs:$endMs:${text.hashCode()}",
                startMs = startMs,
                endMs = endMs,
                text = text,
                alignment = agent.toAlignment(),
                direction = resolveDirection(null, text),
                main = mainTrack,
                backgrounds = backgroundTracks,
                translation = normalizedTranslation,
                romanizedText = lineRomanization,
                phonetics = ImmutableList.copyOf(phonetics),
                isInstrumental = false,
            )
        }

        private suspend fun prepareSyncedText(
            source: String,
            durationMs: Long,
            preferences: LyricsRenderingPreferences,
        ): PreparedLyrics {
            val entries = LyricsUtils.insertInstrumentalBreaks(LyricsUtils.parseLyrics(source), durationMs)
            val syncType = if (entries.any { !it.words.isNullOrEmpty() }) LyricsSyncType.WORD else LyricsSyncType.LINE
            val prepared =
                entries.mapIndexed { index, entry ->
                    val words =
                        entry.words.orEmpty().filterNot(WordTimestamp::isBackground).map { word ->
                            val startMs = word.startTime.toMilliseconds()
                            PreparedLyricsWord(
                                text = word.text,
                                startMs = startMs,
                                endMs = word.endTime.toMilliseconds().coerceAtLeast(startMs + 1L),
                            )
                        }
                    val backgroundWords =
                        entry.words.orEmpty().filter(WordTimestamp::isBackground).map { word ->
                            val startMs = word.startTime.toMilliseconds()
                            PreparedLyricsWord(
                                text = word.text,
                                startMs = startMs,
                                endMs = word.endTime.toMilliseconds().coerceAtLeast(startMs + 1L),
                            )
                        }
                    val endMs =
                        when {
                            entry.durationMs > 0L -> entry.time + entry.durationMs
                            words.isNotEmpty() -> words.maxOf(PreparedLyricsWord::endMs)
                            backgroundWords.isNotEmpty() -> backgroundWords.maxOf(PreparedLyricsWord::endMs)
                            else -> entries.getOrNull(index + 1)?.time?.minus(1L) ?: (entry.time + DEFAULT_LINE_DURATION_MS)
                        }.coerceAtLeast(entry.time + 1L)
                    val romanizedWords =
                        if (preferences.romanization.isEnabled && words.isNotEmpty()) {
                            words.map { word ->
                                LyricsUtils.romanizeLyricsWordWithLineContext(word.text, entry.text, preferences.romanization)
                            }
                        } else {
                            emptyList()
                        }
                    val phonetics =
                        romanizedWords.mapIndexedNotNull { wordIndex, value ->
                            value?.takeIf(String::isNotBlank)?.let { phoneticText ->
                                PreparedLyricsPhonetic(wordIndex, phoneticText)
                            }
                        }
                    PreparedLyricsLine(
                        id = "lrc:$index:${entry.time}:${entry.text.hashCode()}",
                        startMs = entry.time,
                        endMs = endMs,
                        text = entry.text,
                        alignment = entry.agent.toAlignment(),
                        direction = resolveDirection(null, entry.text),
                        main = PreparedLyricsTrack(entry.text, null, ImmutableList.copyOf(words)),
                        backgrounds =
                            if (backgroundWords.isEmpty()) {
                                ImmutableList.of()
                            } else {
                                ImmutableList.of(
                                    PreparedLyricsTrack(
                                        text = backgroundWords.joinToString(separator = "") { it.text },
                                        language = null,
                                        words = ImmutableList.copyOf(backgroundWords),
                                    ),
                                )
                            },
                        translation = LyricsUtils.providedTranslationTextForEntry(entry),
                        romanizedText = LyricsUtils.romanizeLyricsLine(entry.text, preferences.romanization),
                        phonetics = ImmutableList.copyOf(phonetics),
                        isInstrumental = entry.isInstrumental,
                    )
                }
            return PreparedLyrics(
                sourceFormat = if (QRCParser.isQrc(source)) LyricsSourceFormat.QRC else LyricsSourceFormat.LRC,
                syncType = syncType,
                lines = ImmutableList.copyOf(prepared),
                preferences = preferences,
            )
        }

        private suspend fun preparePlainText(
            source: String,
            preferences: LyricsRenderingPreferences,
        ): PreparedLyrics {
            val lines =
                source
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList()
                    .mapIndexed { index, text ->
                        val romanization = LyricsUtils.romanizeLyricsLine(text, preferences.romanization)
                        PreparedLyricsLine(
                            id = "plain:$index:${text.hashCode()}",
                            startMs = -1L,
                            endMs = -1L,
                            text = text,
                            alignment = LyricsLineAlignment.START,
                            direction = resolveDirection(null, text),
                            main = PreparedLyricsTrack(text, null, ImmutableList.of()),
                            backgrounds = ImmutableList.of(),
                            translation = null,
                            romanizedText = romanization,
                            phonetics = ImmutableList.of(),
                            isInstrumental = false,
                        )
                    }
            return PreparedLyrics(
                sourceFormat = LyricsSourceFormat.PLAIN,
                syncType = LyricsSyncType.PLAIN,
                lines = ImmutableList.copyOf(lines),
                preferences = preferences,
            )
        }

        private fun String?.toAlignment(): LyricsLineAlignment =
            when (this?.lowercase()) {
                "v2" -> LyricsLineAlignment.END
                null, "v1" -> LyricsLineAlignment.START
                else -> LyricsLineAlignment.CENTER
            }

        private fun resolveDirection(
            language: String?,
            text: String,
        ): LyricsTextDirection {
            val languageCode = language?.substringBefore('-')?.substringBefore('_')?.lowercase()
            if (languageCode in RTL_LANGUAGES) return LyricsTextDirection.RTL
            val firstStrongDirection =
                text.firstNotNullOfOrNull { character ->
                    when (Character.getDirectionality(character)) {
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                        -> LyricsTextDirection.RTL

                        Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                        Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                        Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                        -> LyricsTextDirection.LTR

                        else -> null
                    }
                }
            return firstStrongDirection ?: LyricsTextDirection.LTR
        }

        private fun Double.toMilliseconds(): Long = (this * 1000.0).roundToLong().coerceAtLeast(0L)

        private fun String.normalizedSupplementaryText(): String = replace(WHITESPACE_REGEX, " ").trim()

        private companion object {
            const val DEFAULT_LINE_DURATION_MS = 4000L
            const val TTML_ROOT_SCAN_LENGTH = 4096
            const val MAX_BASE_CACHE_ENTRIES = 32
            const val MAX_MEDIA_CACHE_ENTRIES = 24
            val WHITESPACE_REGEX = Regex("\\s+")
            val RAW_TTML_ROOT_REGEX = Regex("""<(?:[A-Za-z_][\w.-]*:)?tt(?:\s|>)""", RegexOption.IGNORE_CASE)
            val RTL_LANGUAGES = setOf("ar", "dv", "fa", "he", "ku", "ps", "sd", "ug", "ur", "yi")
        }
    }
