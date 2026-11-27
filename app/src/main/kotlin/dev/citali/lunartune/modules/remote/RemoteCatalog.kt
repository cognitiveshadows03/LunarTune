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
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.dataStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.URI

/**
 * Catalogs: lists of modules that somebody else keeps up to date.
 *
 * A catalog is just a JSON document at an address. It lists modules by name and
 * points at where each one can be downloaded, so a user adds one address once
 * and gets whatever that list offers, including later additions, without having
 * to hunt for individual files.
 *
 * What this deliberately does NOT do is install anything automatically. A
 * catalog entry is shown as something available; installing it is still one
 * deliberate action per module. A list that could put working code on the device
 * by being added would be a much bigger promise than "here are some providers
 * you might want".
 */
@Serializable
data class CatalogDocument(
    val name: String = "",
    val version: Int = 1,
    val updated_at: String = "",
    /** the field everything published so far uses */
    val extensions: List<CatalogEntry> = emptyList(),
    /** accepted as an alias, for lists written by other tools */
    val modules: List<CatalogEntry> = emptyList(),
) {
    val entries: List<CatalogEntry> get() = if (extensions.isNotEmpty()) extensions else modules

    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        fun parse(text: String): Result<CatalogDocument> =
            runCatching {
                val document = json.decodeFromString<CatalogDocument>(text)
                require(document.name.isNotBlank() || document.entries.isNotEmpty()) {
                    "that address is not a catalog"
                }
                document
            }
    }
}

@Serializable
data class CatalogEntry(
    val id: String,
    /** the catalog's own file name for the module */
    val name: String = "",
    val display_name: String = "",
    /** accepted as an alias for display_name */
    val displayName: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val download_url: String = "",
    val icon_url: String = "",
    val labels: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
) {
    val title: String get() = display_name.ifBlank { displayName.ifBlank { name.ifBlank { id } } }
}

/** A catalog the user has added, with whatever its last fetch produced. */
data class InstalledCatalog(
    val url: String,
    val name: String,
    val entries: List<CatalogEntry>,
    val fetchedAt: Long = 0L,
    val error: String? = null,
)

object RemoteCatalogStore {

    private val CatalogsKey = stringPreferencesKey("remote_catalogs_v1")

    /** how long a catalog is considered fresh; a refresh is a network call */
    const val STALE_AFTER_MS = 6L * 60L * 60L * 1000L

    fun catalogs(): List<InstalledCatalog> =
        PreferenceStore.get(CatalogsKey)
            ?.split(RecordSeparator)
            ?.mapNotNull { record ->
                val parts = record.split(FieldSeparator, limit = 3)
                if (parts.size < 3) return@mapNotNull null
                InstalledCatalog(
                    url = parts[0],
                    name = parts[1],
                    entries = emptyList(),
                    fetchedAt = parts[2].toLongOrNull() ?: 0L,
                )
            }.orEmpty()

    /**
     * The catalogs, with their contents.
     *
     * Contents are cached in memory rather than persisted: they are a snapshot of
     * something that lives elsewhere, and a stale copy that a user reads as
     * current is worse than a fetch that fails and says so.
     */
    fun catalogsWithEntries(): List<InstalledCatalog> =
        catalogs().map { catalog ->
            val cached = entryCache[catalog.url]
            catalog.copy(
                entries = cached?.entries.orEmpty(),
                error = cached?.error,
            )
        }

    private val entryCache = HashMap<String, CachedEntries>()

    private data class CachedEntries(
        val entries: List<CatalogEntry>,
        val error: String? = null,
    )

    /** adds an address and fetches it once, so the page has something to show */
    fun add(
        context: Context,
        url: String,
        userAgent: String,
    ): Result<InstalledCatalog> {
        val cleaned = url.trim()
        val scheme = runCatching { URI(cleaned).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("only http and https addresses can be read"))
        }
        if (catalogs().any { it.url == cleaned }) {
            return Result.failure(IllegalStateException("that catalog is already added"))
        }
        val document =
            fetch(cleaned, userAgent).getOrElse { return Result.failure(it) }

        val record = InstalledCatalog(url = cleaned, name = document.name, entries = document.entries)
        entryCache[cleaned] = CachedEntries(document.entries)
        write(context, catalogs() + record.copy(fetchedAt = System.currentTimeMillis()))
        return Result.success(record.copy(entries = document.entries))
    }

    /** re-reads one catalog; the cached entries are replaced either way */
    fun refresh(
        context: Context,
        url: String,
        userAgent: String,
    ): Result<List<CatalogEntry>> {
        val document = fetch(url, userAgent)
        document
            .onSuccess { entryCache[url] = CachedEntries(it.entries) }
            .onFailure { entryCache[url] = CachedEntries(entryCache[url]?.entries.orEmpty(), it.message) }
        val stamp =
            catalogs().map { catalog ->
                if (catalog.url == url) catalog.copy(fetchedAt = System.currentTimeMillis()) else catalog
            }
        write(context, stamp)
        return document.map { it.entries }
    }

    fun remove(
        context: Context,
        url: String,
    ) {
        entryCache.remove(url)
        write(context, catalogs().filterNot { it.url == url })
    }

    /**
     * Installs one entry from a catalog.
     *
     * It fetches what the entry points at and then goes through the same install
     * path as a hand-pasted document, so a catalog module is validated by exactly
     * the rules a pasted one is. A published module in a format this build cannot
     * read fails here, with its own reason, rather than silently doing nothing.
     */
    fun installEntry(
        context: Context,
        entry: CatalogEntry,
        userAgent: String,
    ): Result<RemoteModuleDocument> {
        val address = entry.download_url.trim()
        if (address.isBlank()) {
            return Result.failure(IllegalStateException("that entry has no download address"))
        }
        val scheme = runCatching { URI(address).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("that entry points somewhere that cannot be fetched"))
        }
        val response =
            runCatching { RemoteFetch.get(address, userAgent) }
                .getOrElse { return Result.failure(it) }
        if (!response.ok) {
            return Result.failure(IllegalStateException("the download answered ${response.code}"))
        }
        return RemoteModuleStore.install(context, response.text)
    }

    private fun fetch(
        url: String,
        userAgent: String,
    ): Result<CatalogDocument> {
        val response =
            runCatching { RemoteFetch.get(url, userAgent) }
                .getOrElse { return Result.failure(it) }
        if (!response.ok) {
            return Result.failure(IllegalStateException("the address answered ${response.code}"))
        }
        return CatalogDocument.parse(response.text)
            .onFailure { Timber.tag("RemoteCatalogs").w(it, "Unreadable catalog at %s", url) }
    }

    /**
     * Stores the addresses and their names. What each catalog contains is not
     * persisted — it is fetched, and a cached copy that reads as current when it
     * is a week old is worse than a refresh that fails and says so.
     */
    private fun write(
        context: Context,
        catalogs: List<InstalledCatalog>,
    ) {
        if (catalogs.isEmpty()) {
            runBlocking { context.dataStore.edit { prefs -> prefs.remove(CatalogsKey) } }
            return
        }
        val encoded =
            catalogs.joinToString(RecordSeparator) { catalog ->
                listOf(catalog.url, catalog.name, catalog.fetchedAt.toString()).joinToString(FieldSeparator.toString())
            }
        runBlocking { context.dataStore.edit { prefs -> prefs[CatalogsKey] = encoded } }
    }

    private const val RecordSeparator = "\u0001"
    private const val FieldSeparator = "\u0002"
}
