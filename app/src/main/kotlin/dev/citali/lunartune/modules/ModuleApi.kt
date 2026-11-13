/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules

import dev.citali.lunartune.db.entities.FormatEntity
import java.io.File

/**
 * Stream source module API (beta).
 *
 * A module is a service that can serve audio for a track LunarTune already knows.
 * Modules only resolve streams — they never touch the library, queue or playback
 * state. YouTube Music stays the always-active fallback behind every module.
 */
const val MODULE_API_VERSION = 1

data class SourceModuleInfo(
    /** stable, lowercase: "jiosaavn" */
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val apiVersion: Int = MODULE_API_VERSION,
    val sourceUrl: String? = null,
    val author: String? = null,
)

/** a module's own pictures: a square icon for its row; null keeps LunarTune's own */
interface ModuleArt {
    val icon: String? get() = null
}

/** a setting a module asks people for, shown on its page */
data class ModuleField(
    val id: String,
    val label: String,
    val kind: FieldKind,
    val options: List<StreamOption> = emptyList(),
    val default: String = "",
    val hint: String = "",
    val description: String = "",
)

enum class FieldKind { Text, Select, Toggle }

/** What LunarTune gives a module. */
interface ModuleHost {
    /** small key-value storage private to this module: tokens, choices */
    val storage: ModuleStorage

    /** HTTP through LunarTune's own client, so its proxy/DNS settings apply. Blocking. */
    fun http(request: HttpRequest): HttpResponse

    fun log(message: String)

    /** a folder only this module uses, for things too big for [storage] */
    val folder: File
}

interface ModuleStorage {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
)

// headers come with lowercase names, read them through header()
class HttpResponse(val code: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap()) {
    val ok: Boolean get() = code in 200..299
    val text: String get() = body.decodeToString()

    fun header(name: String): String? = headers[name.lowercase()]
}

/** The track LunarTune wants to play, as LunarTune knows it. */
data class TrackQuery(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val label: String) : AccountState
    data class Expired(val reason: String) : AccountState
}

/** One of a choice's options, best or most usual first. */
data class StreamOption(val id: String, val label: String, val description: String)

/**
 * A setting the module offers, shown as a row of chips: the stream quality, the catalog region.
 * The first option is the default; LunarTune keeps what was picked and passes it to every call.
 */
data class ModuleChoice(val id: String, val label: String, val options: List<StreamOption>)

enum class StreamFormat { Progressive, Hls, Dash }

data class MatchedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    /** 0..1, how sure the module is it's the same recording */
    val confidence: Float,
)

/** Something the player can open. */
data class SourceStream(
    val url: String,
    val format: StreamFormat,
    val headers: Map<String, String> = emptyMap(),
    val sourceLabel: String,
    val qualityLabel: String,
    val matched: MatchedTrack,
    /** the audio's codec as listeners know it ("AAC", "FLAC"), for the player's format badge */
    val codec: String? = null,
    /** the audio's sample rate, when the module knows it */
    val sampleRateHz: Int? = null,
    /** the stream's average bitrate, when the module knows it */
    val bitrateBps: Int? = null,
    /** the whole file's size, when the module knows it */
    val contentLengthBytes: Long? = null,
)

/**
 * What a module actually served, kept beside the media item it was served for so the
 * player can describe the stream that is really playing.
 */
data class ModulePlaybackFormat(
    val mediaId: String,
    val sourceLabel: String,
    /** the service's own id for the recording it matched */
    val catalogueId: String,
    val qualityLabel: String,
    val format: FormatEntity,
)

sealed interface ResolveResult {
    data class Found(val stream: SourceStream) : ResolveResult
    /** the service doesn't have this song */
    data class NotFound(val reason: String) : ResolveResult
    /** the service couldn't be asked: offline, region, down */
    data class Unavailable(val reason: String) : ResolveResult
}

/** One line of a module's self-test. [ok] null while the step runs. */
data class DiagnosticStep(val name: String, val ok: Boolean?, val detail: String)

/** every module: its name, what it adds, and its account when it needs one */
interface SourceModule {
    val info: SourceModuleInfo

    /** called once, before anything else */
    fun attach(host: ModuleHost)

    /** null for a module that needs no account */
    fun accountState(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    /** asks the service whether the saved account still works */
    suspend fun verifyAccount(): AccountState = accountState()

    fun signOut() {}

    val choices: List<ModuleChoice> get() = emptyList()

    /**
     * Settings the module asks people for (an endpoint, a region). Rendered on the
     * module's page and read back through [fieldValue] / [setFieldValue]; module
     * code is free to ignore this and keep its own defaults.
     */
    val fields: List<ModuleField> get() = emptyList()

    /** the value a field currently holds, already resolved against its default */
    fun fieldValue(field: ModuleField): String = field.default

    /** stores a new value; null resets the field to its default */
    fun setFieldValue(field: ModuleField, value: String?) = Unit

    /**
     * What the last stream this module served looked like ("Hi-Res FLAC · 2304 kbps"),
     * remembered so the module's page can show the quality it really gave.
     */
    fun lastStreamLabel(): String? = null

    /** forgets the remembered stream, so the module's page goes back to empty */
    fun clearLastStream() = Unit

    /** [picks]: choice id to option id, for the choices the user changed */
    suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult

    /** the module's own checks, step by step, for the test button */
    suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult
}

/** what a module that needs no account says about it */
const val NO_ACCOUNT_LABEL = "No account needed"
