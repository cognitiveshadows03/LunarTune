/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.citali.lunartune.constants.LyricsClickKey
import dev.citali.lunartune.constants.LyricsLineBlurKey
import dev.citali.lunartune.constants.LyricsLineSpacingKey
import dev.citali.lunartune.constants.LyricsRomanizeChineseKey
import dev.citali.lunartune.constants.LyricsRomanizeHindiKey
import dev.citali.lunartune.constants.LyricsRomanizeJapaneseKey
import dev.citali.lunartune.constants.LyricsRomanizeKoreanKey
import dev.citali.lunartune.constants.LyricsRomanizeOtherLanguagesKey
import dev.citali.lunartune.constants.LyricsScrollKey
import dev.citali.lunartune.constants.LyricsTextSizeKey
import dev.citali.lunartune.constants.LyricsV2BounceFactorKey
import dev.citali.lunartune.constants.LyricsV2FillTransitionWidthKey
import dev.citali.lunartune.constants.LyricsV2GlowFactorKey
import dev.citali.lunartune.constants.LyricsV2LrcBounceEnabledKey
import dev.citali.lunartune.constants.TranslateLyricsKey
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.LyricsEntity
import dev.citali.lunartune.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRenderingRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
        @ApplicationContext context: Context,
    ) {
        private val dataStore = context.dataStore
        private val preferences = dataStore.data

        suspend fun setShowTranslations(show: Boolean) {
            dataStore.edit { values -> values[TranslateLyricsKey] = show }
        }

        fun observeLyrics(mediaId: String): Flow<LyricsEntity?> =
            database
                .lyrics(mediaId)
                .distinctUntilChanged { old, new ->
                    old?.lyrics == new?.lyrics && old?.source == new?.source
                }

        fun observePreferences(): Flow<LyricsRenderingPreferences> =
            combine(
                preferences,
                JapaneseLanguagePackManager.isInstalled,
            ) { values, japanesePackInstalled ->
                LyricsRenderingPreferences(
                    clickEnabled = values[LyricsClickKey] ?: true,
                    showTranslations = values[TranslateLyricsKey] ?: false,
                    scrollEnabled = values[LyricsScrollKey] ?: true,
                    textSizeSp = values[LyricsTextSizeKey] ?: 26f,
                    lineSpacing = values[LyricsLineSpacingKey] ?: 1.3f,
                    lineBlurEnabled = values[LyricsLineBlurKey] ?: true,
                    v2BounceFactor = values[LyricsV2BounceFactorKey] ?: 1f,
                    v2GlowFactor = values[LyricsV2GlowFactorKey] ?: 1f,
                    v2FillTransitionWidthDp = values[LyricsV2FillTransitionWidthKey] ?: 8f,
                    v2LrcBounceEnabled = values[LyricsV2LrcBounceEnabledKey] ?: true,
                    romanization =
                        LyricsRomanizationPreferences(
                            romanizeJapanese = values[LyricsRomanizeJapaneseKey] ?: true,
                            romanizeKorean = values[LyricsRomanizeKoreanKey] ?: true,
                            romanizeChinese = values[LyricsRomanizeChineseKey] ?: true,
                            romanizeHindi = values[LyricsRomanizeHindiKey] ?: true,
                            romanizeOther = values[LyricsRomanizeOtherLanguagesKey] ?: true,
                        ),
                    japaneseLanguagePackInstalled = japanesePackInstalled,
                )
            }.distinctUntilChanged()
    }
