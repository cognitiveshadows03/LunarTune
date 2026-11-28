/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.addon

import dev.citali.lunartune.modules.HttpRequest
import dev.citali.lunartune.modules.HttpResponse
import dev.citali.lunartune.modules.ModuleHost
import timber.log.Timber
import java.net.URI

/**
 * Talking to one addon over its three routes.
 *
 * The host is passed in rather than a client, so every call goes through the
 * same HTTP path a module does: the app's proxy, DNS and timeout settings apply
 * to addons exactly as they do to the built-in modules.
 *
 * A base address is whatever the user pasted. Both forms the protocol names are
 * accepted — `https://host/` and `https://host/manifest.json` — because both are
 * what people copy: an addon's page lists one, the browser's address bar shows
 * the other. A token-bearing path is left intact apart from that filename, since
 * the token is part of the path and every later call has to carry it.
 */
class AddonClient(
    rawBase: String,
    private val host: ModuleHost,
) {
    val base: String = normalizeBase(rawBase)

    /** where the manifest lives, derived from [base] */
    private val manifestUrl: String get() = "$base/manifest.json"

    fun manifest(): Result<AddonManifest> =
        request(manifestUrl)
            .mapCatching { body -> Json.decodeFromString<AddonManifest>(body) }

    /**
     * Searches the addon's catalogue.
     *
     * [params] carries the settings the manifest declared — the quality tier
     * above all — because an addon that gates its lossless tier behind a switch
     * reads that switch from the query string, not from anything remembered
     * between calls.
     */
    fun search(
        query: String,
        params: Map<String, String> = emptyMap(),
    ): Result<List<AddonTrack>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        val url = url("search", mapOf("q" to trimmed) + params)
        return request(url)
            .mapCatching { body ->
                val response = Json.decodeFromString<AddonSearchResponse>(body)
                response.tracks
            }
    }

    /** asks for one track's audio */
    fun stream(
        trackId: String,
        params: Map<String, String> = emptyMap(),
    ): Result<AddonStream> {
        val url = url("stream/$trackId", params)
        return request(url)
            .mapCatching { body -> Json.decodeFromString<AddonStream>(body) }
    }

    /** what the addon's own page calls its settings, resolved to sendable values */
    fun manifestParams(manifest: AddonManifest): Map<String, String> =
        manifest.settings
            .mapNotNull { setting ->
                val value = setting.fallbackValue
                if (setting.key.isBlank() || value.isBlank()) null else setting.key to value
            }.toMap()

    // ---- plumbing ------------------------------------------------------------

    private fun url(
        path: String,
        params: Map<String, String>,
    ): String {
        val builder = StringBuilder(base).append('/').append(path)
        if (params.isNotEmpty()) {
            builder.append('?')
            builder.append(
                params.entries
                    .filter { it.value.isNotBlank() }
                    .joinToString("&") { (name, value) ->
                        "${encode(name)}=${encode(value)}"
                    },
            )
        }
        return builder.toString()
    }

    /**
     * One GET, with the addon's errors turned into sentences.
     *
     * The protocol answers failures in two ways: a status code, and — in the
     * wild — `200` with an `error` field. Both end up as a failed [Result]
     * carrying the addon's own words, because "the catalogue is missing this"
     * and "the addon needs a credential you have not set" are different problems
     * and only one of them is the user's to fix.
     */
    private fun request(url: String): Result<String> {
        val response: HttpResponse =
            runCatching {
                host.http(
                    HttpRequest(
                        url = url,
                        headers = mapOf("Accept" to "application/json"),
                    ),
                )
            }.getOrElse { error ->
                Timber.tag("Addon").d(error, "request failed: %s", redact(url))
                return Result.failure(AddonException.Network(error.message ?: "the addon could not be reached"))
            }

        if (response.code == 404) {
            return Result.failure(AddonException.NotFound("the addon has no such route"))
        }
        if (!response.ok) {
            return Result.failure(
                AddonException.Http("the addon answered ${response.code}"),
            )
        }
        val body = response.text
        // a 200 that carries an error is still a miss; read the field rather than
        // parsing the whole document twice
        runCatching {
            Json.decodeFromString<AddonErrorBody>(body)
        }.getOrNull()?.error?.takeIf { it.isNotBlank() }?.let { message ->
            return Result.failure(AddonException.Addon(message))
        }
        return Result.success(body)
    }

    private fun encode(value: String): String = encodeToUriComponent(value)

    private fun encodeToUriComponent(value: String): String {
        val out = StringBuilder(value.length)
        for (byte in value.encodeToByteArray()) {
            val char = byte.toInt() and 0xFF
            val unreserved =
                char in 'a'.code..'z'.code ||
                    char in 'A'.code..'Z'.code ||
                    char in '0'.code..'9'.code ||
                    char == '-'.code || char == '_'.code || char == '.'.code || char == '~'.code
            if (unreserved) {
                out.append(char.toChar())
            } else {
                out.append('%')
                out.append(Hex[char shr 4])
                out.append(Hex[char and 0x0F])
            }
        }
        return out.toString()
    }

    companion object {
        private const val Hex = "0123456789ABCDEF"

        /**
         * Turns whatever was pasted into a base address.
         *
         * Any trailing `.json` filename is dropped rather than only
         * `manifest.json`: a manifest served as `addon.json` should give the same
         * base, not a base with a document stuck on the end that would send
         * `/search` to `…/addon.json/search`.
         */
        fun normalizeBase(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            val lastSegment = trimmed.substringAfterLast('/', "")
            val withoutDocument =
                if (lastSegment.contains('.') && lastSegment.substringAfterLast('.').equals("json", true) && trimmed.contains("://")) {
                    trimmed.dropLast(lastSegment.length).trimEnd('/')
                } else {
                    trimmed
                }
            return withoutDocument
        }

        /** whether an address is something an addon could live at */
        fun accepts(raw: String): Boolean {
            val scheme = runCatching { URI(raw.trim()).scheme?.lowercase() }.getOrNull() ?: return false
            return scheme == "http" || scheme == "https"
        }

        /** a URL with its credentials and query taken out before it reaches a log */
        fun redact(url: String): String {
            val noQuery = url.substringBefore('?')
            return runCatching {
                val uri = URI(noQuery)
                val port = if (uri.port > 0) ":${uri.port}" else ""
                "${uri.scheme}://${uri.host}$port${uri.path.orEmpty()}"
            }.getOrDefault(noQuery)
        }
    }
}

/** why an addon produced nothing, in the terms the caller has to act on */
sealed class AddonException(
    message: String,
) : Exception(message) {
    /** the addon says this catalogue does not have the track */
    class NotFound(message: String) : AddonException(message)

    /** the addon could not be asked: offline, region, down */
    class Network(message: String) : AddonException(message)

    /** the addon answered, but not with success */
    class Http(message: String) : AddonException(message)

    /** the addon answered with its own explanation of the miss */
    class Addon(message: String) : AddonException(message)
}
