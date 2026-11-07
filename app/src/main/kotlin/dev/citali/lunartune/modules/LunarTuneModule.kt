/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules

import androidx.compose.runtime.Immutable
import dev.citali.lunartune.BuildConfig

/**
 * Feature flags that a module can provide.
 */
enum class ModuleFeature {
    AudioSource,
    Lyrics,
    Metadata,
    MusicRecognition,
    Scrobbling,
    Look,
}

/**
 * Metadata about a module — its identity, version, and capabilities.
 */
@Immutable
data class ModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String? = null,
    val features: Set<ModuleFeature> = emptySet(),
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = true,
)

/**
 * The module interface — a module provides one or more [ModuleFeature]s.
 * Built-in modules are compiled into the app; external modules (loaded
 * via DexClassLoader) will implement this interface through a bridge.
 */
interface LunarTuneModule {
    val info: ModuleInfo
}

/**
 * Registry of all known modules — built-in and (future) external.
 */
object ModuleRegistry {
    val builtInModules: List<ModuleInfo> = listOf(
        ModuleInfo(
            id = "youtube_music",
            name = "YouTube Music",
            version = BuildConfig.VERSION_NAME,
            description = "Default audio source — streams from YouTube Music.",
            author = "LunarTune",
            features = setOf(ModuleFeature.AudioSource),
        ),
        ModuleInfo(
            id = "spotify",
            name = "Spotify",
            version = BuildConfig.VERSION_NAME,
            description = "Spotify library sync, playlists, and playback resolution.",
            author = "LunarTune",
            features = setOf(ModuleFeature.AudioSource, ModuleFeature.Metadata),
        ),
        ModuleInfo(
            id = "lyrics_providers",
            name = "Lyrics Providers",
            version = BuildConfig.VERSION_NAME,
            description = "Multi-source lyrics: LrcLib, YouTube, Musixmatch, Netease, KuGou, and more.",
            author = "LunarTune",
            features = setOf(ModuleFeature.Lyrics),
        ),
        ModuleInfo(
            id = "shazam",
            name = "Music Recognition",
            version = BuildConfig.VERSION_NAME,
            description = "Identify songs playing nearby using ShazamKit.",
            author = "LunarTune",
            features = setOf(ModuleFeature.MusicRecognition),
        ),
        ModuleInfo(
            id = "lastfm",
            name = "Last.fm / Libre.fm",
            version = BuildConfig.VERSION_NAME,
            description = "Scrobble listening history to Last.fm and Libre.fm.",
            author = "LunarTune",
            features = setOf(ModuleFeature.Scrobbling),
        ),
        ModuleInfo(
            id = "discord_presence",
            name = "Discord Rich Presence",
            version = BuildConfig.VERSION_NAME,
            description = "Show what you're listening to on your Discord profile.",
            author = "LunarTune",
            features = setOf(ModuleFeature.Metadata),
        ),
    )

    val allModules: List<ModuleInfo>
        get() = builtInModules
}
