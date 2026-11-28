/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules.addon

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.citali.lunartune.modules.SourceModule
import dev.citali.lunartune.modules.SourceModuleInfo
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.dataStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * The addons the user has added.
 *
 * An addon is stored as its base address and whatever it called itself when it
 * was added. The address is the identity — readding the same address replaces
 * the entry rather than making a second one — because two rows pointing at the
 * same server would be consulted twice for every track.
 *
 * Names are cached rather than fetched every time the module list is built: the
 * list is read when playback starts, and a network call there would put a round
 * trip in front of every track.
 */
object AddonStore {

    private val AddonsKey = stringPreferencesKey("addon_modules_v1")

    /** how many addons can be added at once */
    const val MAX_ADDONS = 24

    private data class Record(
        val url: String,
        val name: String,
        val version: String,
        /** the manifest exactly as the addon sent it, so its switches survive a restart */
        val manifest: String = "",
    )

    private fun records(): List<Record> =
        PreferenceStore.get(AddonsKey)
            ?.split(RecordSeparator)
            ?.mapNotNull { record ->
                val parts = record.split(FieldSeparator, limit = 4)
                if (parts.size < 2 || parts[0].isBlank()) return@mapNotNull null
                Record(
                    url = parts[0],
                    name = parts[1],
                    version = parts.getOrElse(2) { "" },
                    manifest = parts.getOrElse(3) { "" },
                )
            }.orEmpty()

    /** every added addon as a module the registry can serve from */
    fun modules(): List<SourceModule> =
        records().map { record ->
            AddonModule(info = infoFor(record), address = record.url)
        }

    /**
     * The module an address turns into before its manifest has been read.
     *
     * The id is derived from the address so it is stable across launches and
     * cannot collide with a built-in module's plain name.
     */
    private fun infoFor(record: Record): SourceModuleInfo =
        SourceModuleInfo(
            id = idFor(record.url),
            name = record.name.ifBlank { hostOf(record.url) },
            version = record.version.ifBlank { "1.0" },
            description = record.url,
            sourceUrl = record.url,
        )

    /**
     * Adds an address, reading its manifest first.
     *
     * Nothing is stored until the address has answered and described itself: a
     * server that does not speak the protocol would otherwise sit in the list
     * producing a failure on every track, and the reason would only show up much
     * later, in a log.
     */
    fun add(
        context: Context,
        rawAddress: String,
    ): Result<SourceModuleInfo> {
        if (!AddonClient.accepts(rawAddress)) {
            return Result.failure(IllegalArgumentException("only http and https addresses can be added"))
        }
        val base = AddonClient.normalizeBase(rawAddress)
        val existing = records()
        if (existing.none { it.url == base } && existing.size >= MAX_ADDONS) {
            return Result.failure(IllegalStateException("there is a limit of $MAX_ADDONS addons"))
        }

        val fetched =
            AddonFetch.manifest(base).getOrElse { error ->
                return Result.failure(error)
            }
        val manifest = fetched.first
        if (!manifest.playable) {
            return Result.failure(
                IllegalStateException("that addon does not offer search, so it cannot serve tracks"),
            )
        }

        val name = manifest.name.ifBlank { hostOf(base) }
        val record = Record(url = base, name = name, version = manifest.version, manifest = fetched.second)
        val updated =
            if (existing.any { it.url == base }) {
                existing.map { if (it.url == base) record else it }
            } else {
                existing + record
            }
        write(context, updated)
        Timber.tag("Addon").i("added %s at %s", name, AddonClient.redact(base))
        return Result.success(infoFor(record))
    }

    /**
     * Removes one, along with everything it stored.
     *
     * An addon leaves three kinds of key behind: the storage it was given (all
     * under `module_<id>_`), its on/off switch, and the picks it was offered. All
     * three go, or readding the same address would come back with stale switches
     * from a previous version of it. The values are removed by name because they
     * are string keys; a boolean key cannot be removed through the string
     * accessor, so the enabled switch is cleared by setting it to its default
     * through the same key type the UI reads.
     */
    fun remove(
        context: Context,
        id: String,
    ) {
        val kept = records().filterNot { idFor(it.url) == id }
        write(context, kept)
        runBlocking {
            context.dataStore.edit { prefs ->
                val names =
                    prefs.asMap().keys.map { it.name }.filter {
                        it.startsWith("module_${id}_") ||
                            it == "module_picks_$id" ||
                            it == "module_enabled_$id"
                    }
                names.forEach { name ->
                    when {
                        name == "module_enabled_$id" -> {
                            // the switch is read as a boolean, so it has to be
                            // removed as one
                            prefs.remove(booleanPreferencesKey(name))
                        }
                        else ->
                            prefs.remove(
                                androidx.datastore.preferences.core.stringPreferencesKey(name),
                            )
                    }
                }
            }
        }
    }

    /**
     * The manifest recorded when an addon was added.
     *
     * Kept so that the module list, the switches on a page and the first track
     * after a restart all work without a request. An addon that changes what it
     * offers is picked up the next time it answers a search, which is when the
     * fresh copy is read anyway.
     */
    fun savedManifest(base: String): AddonManifest? {
        val normalized = AddonClient.normalizeBase(base)
        val raw = records().firstOrNull { it.url == normalized }?.manifest.orEmpty()
        if (raw.isBlank()) return null
        return runCatching { Json.decodeFromString<AddonManifest>(raw) }.getOrNull()
    }

    /** whether an address is already in the list, so the page can say so */
    fun contains(rawAddress: String): Boolean {
        val base = AddonClient.normalizeBase(rawAddress)
        return records().any { it.url == base }
    }

    private fun write(
        context: Context,
        records: List<Record>,
    ) {
        if (records.isEmpty()) {
            runBlocking { context.dataStore.edit { prefs -> prefs.remove(AddonsKey) } }
            return
        }
        val encoded =
            records.joinToString(RecordSeparator.toString()) { record ->
                listOf(record.url, record.name, record.version, record.manifest)
                    .joinToString(FieldSeparator.toString())
            }
        runBlocking { context.dataStore.edit { prefs -> prefs[AddonsKey] = encoded } }
    }

    // ---- identity ------------------------------------------------------------

    /**
     * A stable id for an address.
     *
     * Built from the host and path rather than a hash so it stays readable in
     * preference keys and log lines, and prefixed so it cannot collide with the
     * built-in modules' plain names.
     */
    fun idFor(base: String): String {
        val withoutScheme = base.substringAfter("://")
        val cleaned =
            withoutScheme
                .lowercase()
                .map { char -> if (char.isLetterOrDigit()) char else '-' }
                .joinToString("")
                .trim('-')
                .replace(Regex("-+"), "-")
        return "addon-$cleaned".take(56)
    }

    private fun hostOf(base: String): String = base.substringAfter("://").substringBefore('/').ifBlank { base }

    private const val RecordSeparator = "\u0001"
    private const val FieldSeparator = '\u0002'
}

/**
 * Reading a manifest before there is a registry to read it through.
 *
 * Every other call an addon makes goes through the module host, so the app's
 * proxy and DNS settings apply. This one cannot: it happens while the addon is
 * being added, before it has a host to call through. It therefore uses a client
 * of its own, and is the only request in the addon path that does.
 */
internal object AddonFetch {
    private const val UserAgent = "LunarTune (Android)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** the parsed manifest, and its text as received */
    fun manifest(base: String): Result<Pair<AddonManifest, String>> {
        val request =
            Request.Builder()
                .url("$base/manifest.json")
                .header("Accept", "application/json")
                .header("User-Agent", UserAgent)
                .get()
                .build()
        val body =
            runCatching { client.newCall(request).execute().use { response -> response.code to response.body?.string().orEmpty() } }
                .getOrElse { error ->
                    return Result.failure(AddonException.Network(error.message ?: "the addon could not be reached"))
                }
        val (code, text) = body
        if (code == 404) {
            return Result.failure(AddonException.NotFound("no manifest at that address"))
        }
        if (code !in 200..299) {
            return Result.failure(AddonException.Http("the address answered $code"))
        }
        return runCatching { Json.decodeFromString<AddonManifest>(text) to text }
            .recoverCatching { _ ->
                // a page that is not JSON, or JSON that is not a manifest, is not
                // an addon; say that rather than leaking a serializer message
                throw AddonException.Http("that address did not describe an addon")
            }
    }
}
