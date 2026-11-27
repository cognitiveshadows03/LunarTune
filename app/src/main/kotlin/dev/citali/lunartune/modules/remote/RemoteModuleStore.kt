/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.citali.lunartune.modules.HttpResponse
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.dataStore
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.net.URI

/**
 * Where loaded modules live.
 *
 * A module is stored as the exact text the user supplied, not as a parsed object,
 * so that what is on disk is what they can read, edit and delete. Parsing happens
 * on the way in and on the way out; a stored document that no longer parses — an
 * older schema, a hand edit that broke the JSON — is dropped from the running list
 * rather than crashing the settings screen that reads it.
 *
 * Reading is synchronous off the preference snapshot, which the app fills at
 * start-up; writing takes a context because that is the only way the app writes
 * preferences at all.
 */
object RemoteModuleStore {

    /** the largest document accepted, so a mistaken file choice cannot exhaust memory */
    const val MAX_DOCUMENT_BYTES = 64 * 1024

    /** how many modules can be installed at once */
    const val MAX_MODULES = 24

    private val ModulesKey = stringPreferencesKey("remote_modules_v1")

    private fun rawList(): List<Pair<String, String>> =
        PreferenceStore.get(ModulesKey)?.let(::decodeList).orEmpty()

    /**
     * The installed modules, parsed.
     *
     * Called by [dev.citali.lunartune.modules.StreamSourceManager] when it builds
     * its registry, so from that point on a loaded module is indistinguishable
     * from one compiled into the app.
     */
    fun modules(): List<RemoteModule> =
        rawList().mapNotNull { (storedId, text) ->
            RemoteModuleDocument.parse(text)
                .onFailure { Timber.tag("RemoteModules").w(it, "Dropping module %s: %s", storedId, it.message) }
                .getOrNull()
                ?.let(::RemoteModule)
        }

    /** what the settings screen lists, including anything that failed to parse */
    fun installed(): List<InstalledModule> =
        rawList().map { (storedId, text) ->
            val parsed = RemoteModuleDocument.parse(text)
            InstalledModule(
                id = storedId,
                document = parsed.getOrNull(),
                error = parsed.exceptionOrNull()?.message,
            )
        }

    /**
     * Adds or replaces a module.
     *
     * The document is validated and *then* stored, so a bad one never reaches the
     * list — the caller gets the reason and nothing changes. Re-adding an existing
     * id replaces it in place, which is how a module is updated: paste the same id
     * with a newer version.
     */
    fun install(
        context: Context,
        text: String,
    ): Result<RemoteModuleDocument> {
        if (text.encodeToByteArray().size > MAX_DOCUMENT_BYTES) {
            return Result.failure(
                IllegalArgumentException("that file is larger than ${MAX_DOCUMENT_BYTES / 1024} KB"),
            )
        }
        val document =
            RemoteModuleDocument.parse(text).getOrElse { return Result.failure(it) }

        val existing = rawList()
        val replacing = existing.any { it.first == document.id }
        if (!replacing && existing.size >= MAX_MODULES) {
            return Result.failure(
                IllegalStateException("there is a limit of $MAX_MODULES installed modules"),
            )
        }
        val updated =
            if (replacing) {
                existing.map { (id, stored) -> if (id == document.id) id to text else id to stored }
            } else {
                existing + (document.id to text)
            }
        write(context, encodeList(updated))
        return Result.success(document)
    }

    /** removes one, by id, along with the values it stored for itself */
    fun remove(
        context: Context,
        id: String,
    ) {
        write(context, encodeList(rawList().filterNot { it.first == id }))
        forgetModuleStorage(context, id)
    }

    /**
     * Fetches a document from an http(s) address and installs it.
     *
     * Blocks deliberately — callers run it on IO. The address is checked to be
     * http(s) before anything is fetched, so a `file:` or `content:` URL cannot be
     * talked into reading the device's own storage.
     */
    fun installFromUrl(
        context: Context,
        url: String,
        userAgent: String,
    ): Result<RemoteModuleDocument> {
        val cleaned = url.trim()
        val scheme = runCatching { URI(cleaned).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("only http and https addresses can be fetched"))
        }
        val response: HttpResponse =
            runCatching { RemoteFetch.get(cleaned, userAgent) }
                .getOrElse { return Result.failure(it) }
        if (!response.ok) {
            return Result.failure(IllegalStateException("the address answered ${response.code}"))
        }
        return install(context, response.text)
    }

    // ---- storage -------------------------------------------------------------

    private fun write(
        context: Context,
        value: String,
    ) {
        runBlocking {
            context.dataStore.edit { prefs -> prefs[ModulesKey] = value }
        }
    }

    /** drops the per-module keys a removed module left behind */
    private fun forgetModuleStorage(
        context: Context,
        id: String,
    ) {
        val prefix = "module_${id}_"
        val snapshot = PreferenceStore.snapshot() ?: return
        val stale = snapshot.asMap().keys.map { it.name }.filter { it.startsWith(prefix) }
        if (stale.isEmpty()) return
        runBlocking {
            context.dataStore.edit { prefs ->
                stale.forEach { name -> prefs.remove(stringPreferencesKey(name)) }
            }
        }
    }

    /**
     * Documents are stored as one preference string rather than one per module, so
     * the list order is implicit and adding a module is a single write. Entries are
     * `id`, a separator, then the document; the document is JSON and never contains
     * a raw control character, so the separators cannot be forged by its content.
     */
    private fun encodeList(entries: List<Pair<String, String>>): String =
        entries.joinToString(RecordSeparator) { (id, text) ->
            id + FieldSeparator + text.replace(RecordSeparator, " ")
        }

    private fun decodeList(raw: String): List<Pair<String, String>> =
        raw.split(RecordSeparator).mapNotNull { record ->
            val split = record.indexOf(FieldSeparator)
            if (split <= 0) return@mapNotNull null
            record.substring(0, split) to record.substring(split + 1)
        }

    private const val RecordSeparator = "\u0001"
    private const val FieldSeparator = '\u0002'
}

/** One row on the sources page: what was stored, and whether it still parses. */
data class InstalledModule(
    val id: String,
    val document: RemoteModuleDocument?,
    val error: String? = null,
) {
    val usable: Boolean get() = document != null
}

/**
 * The one HTTP call the loader makes for itself.
 *
 * It goes through OkHttp directly rather than through a module's host, because at
 * the moment a module is being fetched there is no module to host it yet.
 */
internal object RemoteFetch {

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun get(
        url: String,
        userAgent: String,
    ): HttpResponse {
        val request =
            okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.bytes() ?: ByteArray(0)
            val headers =
                response.headers
                    .toMultimap()
                    .entries
                    .associate { entry -> entry.key.lowercase() to entry.value.firstOrNull().orEmpty() }
            return HttpResponse(response.code, body, headers)
        }
    }
}
