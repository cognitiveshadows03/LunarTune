/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.addon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The wire shapes of the BitChord addon protocol.
 *
 * An addon is an ordinary HTTPS server, not code. It answers three routes with
 * JSON and nothing of it is ever executed here: the search route says what the
 * catalogue has, the stream route says where the audio is, and the player opens
 * that address. That is the whole protocol, and it is why supporting it costs a
 * few data classes rather than an interpreter.
 *
 * Everything unknown is ignored on purpose. Addons in the wild publish more than
 * this — video renditions, chapters, artwork sizes, DRM descriptions — and a
 * parser that rejected what it did not recognise would fail on the ones that are
 * most thorough.
 */
@Serializable
data class AddonManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val author: String = "",
    /** the routes it serves; `search` is the one that has to be there */
    val resources: List<String> = emptyList(),
    /**
     * Switches the addon declares, as defaults.
     *
     * Read for the defaults only. The protocol has the host render a form and
     * send what the user picked as query parameters on every request, and a
     * `quality` setting is how an addon gates its lossless tier — sending
     * nothing leaves it on whatever its server-side default happens to be,
     * which is not always the one it advertises.
     */
    val settings: List<AddonSetting> = emptyList(),
) {
    val playable: Boolean get() = resources.isEmpty() || resources.any { it.equals("search", true) }
}

@Serializable
data class AddonSetting(
    val key: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "",
    val default: JsonElement? = null,
    val options: List<AddonOption> = emptyList(),
) {
    /** the default as the query-parameter text it would be sent as */
    val defaultText: String
        get() =
            when (val value = default) {
                null -> ""
                is JsonPrimitive -> value.contentOrNull.orEmpty()
                else -> value.toString()
            }

    /** the value to send when the user has not chosen: its default, else a usable one */
    val fallbackValue: String
        get() =
            defaultText.ifBlank {
                when {
                    options.isNotEmpty() -> options.first().value
                    type.equals("boolean", true) || type.equals("switch", true) -> "0"
                    else -> ""
                }
            }
}

@Serializable
data class AddonOption(
    val name: String = "",
    val value: String = "",
)

// ── /search?q= ──────────────────────────────────────────────────────────────

@Serializable
data class AddonSearchResponse(
    val tracks: List<AddonTrack> = emptyList(),
    /** some addons answer a miss with this instead of an empty list */
    val error: String? = null,
)

@Serializable
data class AddonTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    /** seconds; read as a double so `240.0` is not a parse failure */
    val duration: Double? = null,
    val artworkURL: String? = null,
    val albumArtworkURL: String? = null,
    val format: String = "",
    val audioQuality: String = "",
    /**
     * A stream address on the row itself.
     *
     * When present the protocol says the host skips the stream call entirely, so
     * this does too — one round trip saved on the path that has to finish before
     * audio can start.
     */
    val streamURL: String? = null,
) {
    val durationSec: Int? get() = duration?.takeIf { it > 0 }?.toInt()

    val artwork: String? get() = artworkURL?.ifBlank { null } ?: albumArtworkURL?.ifBlank { null }
}

// ── /stream/{id} ────────────────────────────────────────────────────────────

@Serializable
data class AddonStream(
    val url: String = "",
    /** free text — `mp3`, `flac`, `dash`; describes the audio, not the transport */
    val format: String = "",
    val quality: String = "",
    val streamQuality: String = "",
    val audioQuality: String = "",
    val codec: String? = null,
    val fileCodec: String? = null,
    val container: String? = null,
    /** `none`, `hls` or `dash` — what the address points at */
    val manifest: String? = null,
    val mediaType: String? = null,
    val mimeType: String? = null,
    /** `false`, or the name of a DRM scheme; anything but false is refused */
    val encrypted: JsonElement? = null,
    val sampleRate: Double? = null,
    val bitDepth: Double? = null,
    /** bits per second */
    val bitrate: Double? = null,
    /**
     * Why there is no [url], when the addon bothers to say.
     *
     * A live addon was measured answering `200` with `{"error": "Deezer ARL
     * required — set it in the setup page"}` rather than a status code. Keeping
     * the text turns "this track silently played from YouTube" into a reason a
     * person can act on.
     */
    val error: String? = null,
) {
    val statedCodec: String? get() = (codec?.ifBlank { null } ?: fileCodec?.ifBlank { null })?.lowercase()

    val qualityText: String get() = "$quality $streamQuality $audioQuality $format".trim()

    /**
     * Whether the stream can be played at all.
     *
     * A DRM scheme here means the bytes are encrypted, and handing an encrypted
     * URL to the player produces a failure much later and much less clearly than
     * refusing it now.
     */
    val playable: Boolean
        get() {
            if (url.isBlank()) return false
            val drm = encrypted
            if (drm == null) return true
            return when (drm) {
                is JsonPrimitive ->
                    if (drm.isString) {
                        drm.contentOrNull?.let { it.isBlank() || it.equals("false", true) || it.equals("none", true) } == true
                    } else {
                        drm.booleanOrNull == false
                    }
                else -> false
            }
        }
}

/** how an addon describes its artwork-less self when nothing else is available */
internal val Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

internal fun JsonElement?.textOrNull(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        else -> this?.toString()
    }

/** the protocol's own param name for the audio tier */
internal const val QualityKey = "quality"

@Serializable
internal data class AddonErrorBody(
    @SerialName("error") val error: String? = null,
)
