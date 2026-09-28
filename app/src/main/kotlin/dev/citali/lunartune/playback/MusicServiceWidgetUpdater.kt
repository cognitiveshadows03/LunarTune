/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.citali.lunartune.R
import dev.citali.lunartune.extensions.SilentHandler
import dev.citali.lunartune.utils.reportException
import dev.citali.lunartune.widget.AlbumArtWidget
import dev.citali.lunartune.widget.AlbumArtWidgetReceiver
import dev.citali.lunartune.widget.ListeningInsightsWidget
import dev.citali.lunartune.widget.ListeningInsightsWidgetReceiver
import dev.citali.lunartune.widget.LoadWidgetInsightsUseCase
import dev.citali.lunartune.widget.MusicWidget
import dev.citali.lunartune.widget.MusicWidgetKeys
import dev.citali.lunartune.widget.MusicWidgetReceiver
import dev.citali.lunartune.widget.NowPlayingCardWidget
import dev.citali.lunartune.widget.NowPlayingCardWidgetReceiver
import dev.citali.lunartune.widget.PlaybackCapsuleWidget
import dev.citali.lunartune.widget.PlaybackCapsuleWidgetReceiver
import dev.citali.lunartune.widget.PlaybackCommandWidget
import dev.citali.lunartune.widget.PlaybackCommandWidgetReceiver
import dev.citali.lunartune.widget.PlaybackDeckWidget
import dev.citali.lunartune.widget.PlaybackDeckWidgetReceiver
import dev.citali.lunartune.widget.PlaybackSpotlightWidget
import dev.citali.lunartune.widget.PlaybackSpotlightWidgetReceiver
import dev.citali.lunartune.widget.WidgetInsightsSnapshot
import dev.citali.lunartune.widget.toWidgetPreferenceValue
import java.io.File

internal class MusicServiceWidgetUpdater(
    private val service: MusicService,
    private val player: Player,
    private val scope: CoroutineScope,
    private val loadWidgetInsights: LoadWidgetInsightsUseCase,
) {
    private val widgetManager = GlanceAppWidgetManager(service)
    private var stateJob: Job? = null
    private var progressJob: Job? = null

    fun update() {
        stateJob?.cancel()
        stateJob =
            scope.launch(SilentHandler) {
                pushState()
            }
    }

    fun updateProgressTracking() {
        progressJob?.cancel()
        if (player.isPlaying && player.duration > 0) {
            progressJob =
                scope.launch(SilentHandler) {
                    val installedTargets = findInstalledTargets(progressWidgets)
                    if (installedTargets.isEmpty()) return@launch

                    while (isActive && player.isPlaying) {
                        delay(WIDGET_PROGRESS_UPDATE_INTERVAL_MILLIS)
                        if (player.isPlaying) {
                            updateProgress(installedTargets, player.playbackProgress())
                        }
                    }
                }
        }
    }

    private suspend fun pushState() {
        val installedTargets = findInstalledTargets(playbackWidgets)
        if (installedTargets.isEmpty()) return

        val mediaItem = player.currentMediaItem
        val meta = mediaItem?.mediaMetadata
        val artFile = meta?.artworkUri?.let { cacheAlbumArt(it) }
        val dominantColor = artFile?.let { extractDominantColor(it) }
        val snapshot =
            WidgetSnapshot(
                title = meta?.title?.toString() ?: service.getString(R.string.no_track_playing),
                artist = meta?.artist?.toString().orEmpty(),
                isPlaying = player.isPlaying,
                isAvailable = mediaItem != null,
                playbackPosition = player.playbackProgress(),
                artPath = artFile?.absolutePath,
                dominantColor = dominantColor,
                insights = WidgetInsightsSnapshot.Empty,
            )

        installedTargets.forEach { target ->
            runCatching { updateWidget(target, snapshot) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                }
        }
    }

    private suspend fun updateProgress(
        installedTargets: List<InstalledWidgetTarget>,
        progress: Float,
    ) {
        installedTargets.forEach { installedTarget ->
            runCatching {
                installedTarget.ids.forEach { id ->
                    updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                        prefs.toMutableWidgetPreferences().apply {
                            this[MusicWidgetKeys.PLAYBACK_POSITION] = progress
                        }
                    }
                    installedTarget.target.widget.update(service, id)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                reportException(error)
            }
        }
    }

    private suspend fun updateWidget(
        installedTarget: InstalledWidgetTarget,
        snapshot: WidgetSnapshot,
    ) {
        val targetSnapshot =
            if (installedTarget.target.requiresInsights) {
                snapshot.copy(insights = loadInsightsSnapshot())
            } else {
                snapshot
            }

        installedTarget.ids.forEach { id ->
            updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutableWidgetPreferences().apply {
                    writeSnapshot(targetSnapshot)
                }
            }
            installedTarget.target.widget.update(service, id)
        }
    }

    private suspend fun findInstalledTargets(targets: List<WidgetTarget>): List<InstalledWidgetTarget> =
        targets.mapNotNull { target ->
            val glanceIds = widgetManager.getGlanceIds(target.widgetClass)
            healOrphanedWidget(target)
            glanceIds.takeIf { ids -> ids.isNotEmpty() }?.let { ids -> InstalledWidgetTarget(target, ids) }
        }

    /**
     * Re-registers widget instances the launcher still shows but Glance no longer tracks
     * (stale placements from before a reinstall, data clear or update). Without this the
     * updater silently skips them and they sit on the stale placeholder forever.
     */
    private suspend fun healOrphanedWidget(target: WidgetTarget) {
        val installedIds =
            runCatching {
                val manager = AppWidgetManager.getInstance(service)
                manager.getAppWidgetIds(ComponentName(service, target.receiverClass))
            }.getOrDefault(intArrayOf())
        if (installedIds.isEmpty()) return
        val orphanIds =
            installedIds.filter { id ->
                try {
                    widgetManager.getGlanceIdBy(id)
                    false
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    true
                }
            }.toIntArray()
        if (orphanIds.isEmpty()) return
        runCatching {
            service.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = ComponentName(service, target.receiverClass)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, orphanIds)
                },
            )
        }
    }

    private fun Preferences.toMutableWidgetPreferences(): MutablePreferences =
        mutablePreferencesOf().also { mutable ->
            this[MusicWidgetKeys.TRACK_TITLE]?.let { mutable[MusicWidgetKeys.TRACK_TITLE] = it }
            this[MusicWidgetKeys.TRACK_ARTIST]?.let { mutable[MusicWidgetKeys.TRACK_ARTIST] = it }
            this[MusicWidgetKeys.ART_PATH]?.let { mutable[MusicWidgetKeys.ART_PATH] = it }
            this[MusicWidgetKeys.IS_PLAYING]?.let { mutable[MusicWidgetKeys.IS_PLAYING] = it }
            this[MusicWidgetKeys.IS_AVAILABLE]?.let { mutable[MusicWidgetKeys.IS_AVAILABLE] = it }
            this[MusicWidgetKeys.DOMINANT_COLOR]?.let { mutable[MusicWidgetKeys.DOMINANT_COLOR] = it }
            this[MusicWidgetKeys.PLAYBACK_POSITION]?.let { mutable[MusicWidgetKeys.PLAYBACK_POSITION] = it }
            this[MusicWidgetKeys.LISTENING_TIME]?.let { mutable[MusicWidgetKeys.LISTENING_TIME] = it }
            this[MusicWidgetKeys.TOTAL_PLAYS]?.let { mutable[MusicWidgetKeys.TOTAL_PLAYS] = it }
            this[MusicWidgetKeys.RECENT_SONGS]?.let { mutable[MusicWidgetKeys.RECENT_SONGS] = it }
            this[MusicWidgetKeys.GENRES]?.let { mutable[MusicWidgetKeys.GENRES] = it }
            this[MusicWidgetKeys.RECOMMENDATIONS]?.let { mutable[MusicWidgetKeys.RECOMMENDATIONS] = it }
            this[MusicWidgetKeys.TOP_SONG_SUMMARY]?.let { mutable[MusicWidgetKeys.TOP_SONG_SUMMARY] = it }
        }

    private fun MutablePreferences.writeSnapshot(snapshot: WidgetSnapshot) {
        this[MusicWidgetKeys.TRACK_TITLE] = snapshot.title
        this[MusicWidgetKeys.TRACK_ARTIST] = snapshot.artist
        this[MusicWidgetKeys.IS_PLAYING] = snapshot.isPlaying
        this[MusicWidgetKeys.IS_AVAILABLE] = snapshot.isAvailable
        this[MusicWidgetKeys.PLAYBACK_POSITION] = snapshot.playbackPosition

        val artPath = snapshot.artPath
        if (artPath != null) {
            this[MusicWidgetKeys.ART_PATH] = artPath
        } else {
            remove(MusicWidgetKeys.ART_PATH)
        }

        val dominantColor = snapshot.dominantColor
        if (dominantColor != null) {
            this[MusicWidgetKeys.DOMINANT_COLOR] = dominantColor
        } else {
            remove(MusicWidgetKeys.DOMINANT_COLOR)
        }

        writeInsights(snapshot.insights)
    }

    private fun MutablePreferences.writeInsights(insights: WidgetInsightsSnapshot) {
        if (insights.listeningTime.isNotBlank()) {
            this[MusicWidgetKeys.LISTENING_TIME] = insights.listeningTime
        } else {
            remove(MusicWidgetKeys.LISTENING_TIME)
        }
        if (insights.totalPlays.isNotBlank()) {
            this[MusicWidgetKeys.TOTAL_PLAYS] = insights.totalPlays
        } else {
            remove(MusicWidgetKeys.TOTAL_PLAYS)
        }
        writeList(MusicWidgetKeys.RECENT_SONGS, insights.recentSongs)
        writeList(MusicWidgetKeys.GENRES, insights.genres)
        writeList(MusicWidgetKeys.RECOMMENDATIONS, insights.recommendations)

        val topSongSummary = insights.topSongSummary
        if (!topSongSummary.isNullOrBlank()) {
            this[MusicWidgetKeys.TOP_SONG_SUMMARY] = topSongSummary
        } else {
            remove(MusicWidgetKeys.TOP_SONG_SUMMARY)
        }
    }

    private fun MutablePreferences.writeList(
        key: Preferences.Key<String>,
        values: List<String>,
    ) {
        if (values.isEmpty()) {
            remove(key)
        } else {
            this[key] = values.toWidgetPreferenceValue()
        }
    }

    private suspend fun loadInsightsSnapshot(): WidgetInsightsSnapshot =
        try {
            loadWidgetInsights()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportException(error)
            WidgetInsightsSnapshot.Empty
        }

    private suspend fun cacheAlbumArt(uri: Uri): File? =
        withContext(Dispatchers.IO) {
            val dest = File(service.cacheDir, "widget_art_${Integer.toHexString(uri.toString().hashCode())}.jpg")
            if (dest.isFile && dest.length() > 0L) return@withContext dest

            if (uri.scheme == "content" || uri.scheme == "file") {
                return@withContext try {
                    service.contentResolver.openInputStream(uri)?.use { src ->
                        dest.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    if (dest.exists() && dest.length() > 0) dest else null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }

            if (uri.scheme == "https" || uri.scheme == "http") {
                return@withContext try {
                    val loader = service.applicationContext.imageLoader
                    val request =
                        ImageRequest
                            .Builder(service.applicationContext)
                            .data(uri.toString())
                            .size(512, 512)
                            .allowHardware(false)
                            .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                        dest.outputStream().use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                        }
                        if (dest.exists() && dest.length() > 0) dest else null
                    } else {
                        null
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }

            null
        }

    private suspend fun extractDominantColor(file: File): Int? =
        withContext(Dispatchers.Default) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                val palette = Palette.from(bitmap).generate()
                palette.getDarkVibrantColor(
                    palette.getDominantColor(android.graphics.Color.DKGRAY),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }

    private fun Player.playbackProgress(): Float =
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    private data class WidgetSnapshot(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val isAvailable: Boolean,
        val playbackPosition: Float,
        val artPath: String?,
        val dominantColor: Int?,
        val insights: WidgetInsightsSnapshot,
    )

    private data class WidgetTarget(
        val widgetClass: Class<out GlanceAppWidget>,
        val widget: GlanceAppWidget,
        val receiverClass: Class<out GlanceAppWidgetReceiver>,
        val requiresInsights: Boolean = false,
    )

    private data class InstalledWidgetTarget(
        val target: WidgetTarget,
        val ids: List<GlanceId>,
    )

    private companion object {
        const val WIDGET_PROGRESS_UPDATE_INTERVAL_MILLIS = 30_000L

        val playbackWidgets =
            listOf(
                WidgetTarget(MusicWidget::class.java, MusicWidget(), MusicWidgetReceiver::class.java),
                WidgetTarget(NowPlayingCardWidget::class.java, NowPlayingCardWidget(), NowPlayingCardWidgetReceiver::class.java),
                WidgetTarget(PlaybackDeckWidget::class.java, PlaybackDeckWidget(), PlaybackDeckWidgetReceiver::class.java),
                WidgetTarget(AlbumArtWidget::class.java, AlbumArtWidget(), AlbumArtWidgetReceiver::class.java),
                WidgetTarget(PlaybackCapsuleWidget::class.java, PlaybackCapsuleWidget(), PlaybackCapsuleWidgetReceiver::class.java),
                WidgetTarget(PlaybackSpotlightWidget::class.java, PlaybackSpotlightWidget(), PlaybackSpotlightWidgetReceiver::class.java),
                WidgetTarget(PlaybackCommandWidget::class.java, PlaybackCommandWidget(), PlaybackCommandWidgetReceiver::class.java),
                WidgetTarget(
                    widgetClass = ListeningInsightsWidget::class.java,
                    widget = ListeningInsightsWidget(),
                    receiverClass = ListeningInsightsWidgetReceiver::class.java,
                    requiresInsights = true,
                ),
            )

        val progressWidgets =
            listOf(
                WidgetTarget(MusicWidget::class.java, MusicWidget(), MusicWidgetReceiver::class.java),
                WidgetTarget(NowPlayingCardWidget::class.java, NowPlayingCardWidget(), NowPlayingCardWidgetReceiver::class.java),
                WidgetTarget(PlaybackDeckWidget::class.java, PlaybackDeckWidget(), PlaybackDeckWidgetReceiver::class.java),
                WidgetTarget(PlaybackCapsuleWidget::class.java, PlaybackCapsuleWidget(), PlaybackCapsuleWidgetReceiver::class.java),
                WidgetTarget(PlaybackSpotlightWidget::class.java, PlaybackSpotlightWidget(), PlaybackSpotlightWidgetReceiver::class.java),
                WidgetTarget(PlaybackCommandWidget::class.java, PlaybackCommandWidget(), PlaybackCommandWidgetReceiver::class.java),
            )
    }
}
