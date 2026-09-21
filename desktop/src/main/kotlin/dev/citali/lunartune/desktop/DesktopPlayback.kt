/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.desktop

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** VIVI-inspired desktop playback boundary for the shared LunarTune player. */
class DesktopPlayback {
    private val manager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        configuration.outputFormat = StandardAudioDataFormats.COMMON_PCM_S16_LE
        AudioSourceManagers.registerRemoteSources(this)
    }
    private val player = manager.createPlayer()
    private val _state = MutableStateFlow(DesktopPlaybackState())
    val state: StateFlow<DesktopPlaybackState> = _state.asStateFlow()

    fun load(url: String, title: String) {
        _state.value = DesktopPlaybackState(title = title, buffering = true)
        manager.loadItem(AudioReference(url, title), object : AudioLoadResultHandler {
            override fun trackLoaded(track: com.sedmelluq.discord.lavaplayer.track.AudioTrack) {
                player.playTrack(track)
                _state.value = DesktopPlaybackState(title = title, playing = true)
            }
            override fun playlistLoaded(playlist: com.sedmelluq.discord.lavaplayer.track.AudioPlaylist) {
                playlist.selectedTrack?.let { trackLoaded(it) } ?: fail("No playable track")
            }
            override fun noMatches() = fail("No playable stream")
            override fun loadFailed(exception: com.sedmelluq.discord.lavaplayer.tools.FriendlyException) = fail(exception.message)
        })
    }

    fun toggle() {
        player.isPaused = !player.isPaused
        _state.value = _state.value.copy(playing = !player.isPaused)
    }

    fun release() {
        player.destroy()
        manager.shutdown()
    }

    private fun fail(message: String?) {
        _state.value = _state.value.copy(buffering = false, error = message)
    }
}

data class DesktopPlaybackState(
    val title: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
)
