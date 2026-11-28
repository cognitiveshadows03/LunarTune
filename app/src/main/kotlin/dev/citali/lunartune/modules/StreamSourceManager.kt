/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.citali.lunartune.constants.ModuleEnabledKey
import dev.citali.lunartune.constants.ModulesEnabledKey
import dev.citali.lunartune.constants.ModuleMatchToleranceKey
import dev.citali.lunartune.constants.ModulePicksKey
import dev.citali.lunartune.constants.StreamSourceOrderKey
import dev.citali.lunartune.modules.addon.AddonStore
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Registry + priority resolution for stream source modules (beta).
 *
 * Enabled modules are tried in the user's priority order; the first one that
 * returns a stream wins. YouTube Music is not part of this list — it stays the
 * always-active fallback behind every module, handled by the caller.
 */
object StreamSourceManager {

    /** matches the slider's own default, used until the user moves it */
    private const val DEFAULT_MATCH_TOLERANCE_SECONDS = 10

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            // the whole call, not just each read: a module that hangs must give up while the
            // song is still about to start, not after the fallback has been waited for twice
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** inbuilt modules, in default priority order */
    private val factories: List<() -> SourceModule> = listOf(
        { MonochromeModule() },
        { JioSaavnModule() },
    )

    /** set once from the application, so a module added later can still be attached */
    @Volatile private var appContext: Context? = null

    /** in-app modules, built once; their set cannot change while the app runs */
    private val inbuilt: Map<String, SourceModule> by lazy {
        factories.map { it() }.associateBy { it.info.id }
    }

    /** loaded modules, rebuilt whenever the installed set changes */
    @Volatile private var loaded: Map<String, SourceModule> = emptyMap()

    /** everything that can serve a stream, in-built first, then whatever is loaded */
    private val modules: Map<String, SourceModule>
        get() = inbuilt + loaded.filterKeys { it !in inbuilt }

    /** every module, in default priority order: in-app ones first, then loaded ones */
    fun allModules(): List<SourceModule> = modules.values.toList()

    fun module(id: String): SourceModule? = modules[id]

    /** the marker every addon's module id carries, so the two groups can be told apart */
    const val ADDON_ID_PREFIX = "addon-"

    /** module ids in the user's priority order (unknown ids dropped, missing ones appended) */
    fun priorityOrder(): List<String> {
        val saved = PreferenceStore.get(StreamSourceOrderKey)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val known = modules.keys
        val ordered = saved.filter { it in known }
        return ordered + known.filter { it !in ordered }
    }

    fun isEnabled(id: String): Boolean =
        PreferenceStore.get(ModuleEnabledKey(id)) ?: defaultEnabled(id)

    private fun defaultEnabled(id: String): Boolean = false

    /**
     * Whether modules are consulted at all.
     *
     * The per-module switches say which ones are wanted; this says whether the
     * whole mechanism runs. Default on, so an existing install keeps behaving as
     * it did before the switch existed.
     */
    fun modulesEnabled(): Boolean = PreferenceStore.get(ModulesEnabledKey) ?: true

    /** enabled modules in priority order; empty when the master switch is off */
    fun activeModules(): List<SourceModule> {
        if (!modulesEnabled()) return emptyList()
        return priorityOrder().mapNotNull { modules[it] }.filter { isEnabled(it.info.id) }
    }

    /**
     * Moves a module one place earlier or later in the saved order.
     *
     * The order is an explicit list, so a move is a rewrite of it: take the
     * current order, swap the two positions, save. Modules that were never saved
     * are appended first, so moving anything works on the order the user is
     * actually looking at rather than the one in the preference.
     *
     * A move is confined to the module's own group. The page shows built-ins and
     * addons as two lists, and their arrows say what happens in that list; if a
     * move crossed between them the row would jump out from under the finger and
     * the two groups would stop describing the real order. Built-ins therefore
     * always outrank addons, which is also the sane default: the sources compiled
     * into the app are the ones that were chosen for it.
     */
    suspend fun move(
        context: Context,
        id: String,
        towards: Int,
    ) {
        val current = priorityOrder().toMutableList()
        val from = current.indexOf(id)
        if (from < 0) return
        val addon = id.startsWith(ADDON_ID_PREFIX)
        // the stretch of the list this module may move within
        val first = if (addon) current.indexOfFirst { it.startsWith(ADDON_ID_PREFIX) } else 0
        val last = if (addon) current.lastIndex else current.indexOfFirst { it.startsWith(ADDON_ID_PREFIX) }
            .let { if (it < 0) current.lastIndex else it - 1 }
        if (first < 0) return
        val to = (from + towards).coerceIn(first, last.coerceAtLeast(first))
        if (to == from) return
        current.removeAt(from)
        current.add(to, id)
        context.dataStore.edit { prefs -> prefs[StreamSourceOrderKey] = current.joinToString(",") }
    }

    fun picksFor(id: String): Map<String, String> {
        val raw = PreferenceStore.get(ModulePicksKey(id)) ?: return emptyMap()
        return raw.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
    }

    /**
     * Try every active module in priority order. Returns the first stream found,
     * or null when no module can serve the track (caller falls back to YouTube).
     */
    suspend fun resolve(track: TrackQuery, mediaId: String? = null): SourceStream? {
        TrackMatcher.toleranceSeconds =
            (PreferenceStore.get(ModuleMatchToleranceKey) ?: DEFAULT_MATCH_TOLERANCE_SECONDS).toFloat()
        for (module in activeModules()) {
            val result = runCatching {
                module.resolve(track, picksFor(module.info.id))
            }.onFailure { e ->
                Timber.tag("StreamSource").w(e, "Module %s failed to resolve", module.info.id)
            }.getOrNull() ?: continue
            when (result) {
                is ResolveResult.Found -> {
                    val stream = result.stream
                    // media id and the service's own id side by side, so a log line can be
                    // followed from LunarTune's item to the record that was matched
                    Timber.tag("StreamSource").i(
                        "Module %s served \"%s\" | media %s -> catalogue %s | %s | confidence %.2f",
                        module.info.id,
                        track.title,
                        mediaId ?: "?",
                        stream.matched.id,
                        stream.qualityLabel,
                        stream.matched.confidence,
                    )
                    return stream
                }
                is ResolveResult.NotFound -> Timber.tag("StreamSource").d(
                    "Module %s: %s", module.info.id, result.reason,
                )
                is ResolveResult.Unavailable -> Timber.tag("StreamSource").d(
                    "Module %s unavailable: %s", module.info.id, result.reason,
                )
            }
        }
        return null
    }

    /** attach every module with its host; call once from the application */
    fun attachAll(context: Context) {
        appContext = context.applicationContext
        inbuilt.values.forEach { module ->
            runCatching { module.attach(Host(context.applicationContext, module.info.id)) }
                .onFailure { e -> Timber.tag("StreamSource").w(e, "Module %s attach failed", module.info.id) }
        }
        reloadLoaded()
    }

    /**
     * Re-reads the loaded modules and attaches them.
     *
     * Called at start-up and again whenever a module is added or removed, because
     * the installed set changes while the app is running and a registry built once
     * would keep offering a module that is no longer there — or miss one that was
     * just added until the next launch.
     */
    fun reloadLoaded() {
        val context = appContext ?: return
        loaded =
            AddonStore.modules()
                .onEach { module ->
                    runCatching { module.attach(Host(context, module.info.id)) }
                        .onFailure { e ->
                            Timber.tag("StreamSource").w(e, "Loaded module %s attach failed", module.info.id)
                        }
                }
                .associateBy { it.info.id }
    }

    private class Host(
        private val context: Context,
        private val moduleId: String,
    ) : ModuleHost {

        override val storage: ModuleStorage = object : ModuleStorage {
            override fun get(key: String): String? =
                PreferenceStore.get(storageKey(key))

            override fun set(key: String, value: String?) {
                runBlocking {
                    context.dataStore.edit { prefs ->
                        if (value == null) prefs.remove(storageKey(key)) else prefs[storageKey(key)] = value
                    }
                }
            }

            private fun storageKey(key: String) = stringPreferencesKey("module_${moduleId}_$key")
        }

        override fun http(request: HttpRequest): HttpResponse {
            // Module calls are suspend by contract, but a UI callback (the sheet's
            // "Check connection", for one) can still reach a module from the main
            // thread. A blocking socket there throws NetworkOnMainThreadException,
            // which every module reports as "didn't answer (offline)" - so never let
            // the call run on main.
            return if (Looper.myLooper() == Looper.getMainLooper()) {
                runBlocking(Dispatchers.IO) { execute(request) }
            } else {
                execute(request)
            }
        }

        private fun execute(request: HttpRequest): HttpResponse {
            val startedAt = System.currentTimeMillis()
            return try {
                executeOnce(request)
            } catch (e: java.io.IOException) {
                // a call that fails straight away is usually a stale socket rather than a
                // server that has gone away, so one quick second try is worth it
                if (System.currentTimeMillis() - startedAt > TransientRetryWindowMs) throw e
                executeOnce(request)
            }
        }

        private fun executeOnce(request: HttpRequest): HttpResponse {
            val builder = Request.Builder().url(request.url).method(request.method, request.body?.toRequestBody(request.contentType?.toMediaTypeOrNull()))
            request.headers.forEach { (name, value) -> builder.header(name, value) }
            httpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.bytes() ?: ByteArray(0)
                val headers =
                    response.headers
                        .toMultimap()
                        .entries
                        .associate { entry -> entry.key.lowercase() to entry.value.firstOrNull().orEmpty() }
                return HttpResponse(response.code, body, headers)
            }
        }

        override fun log(message: String) {
            Timber.tag("Module:$moduleId").d(message)
        }

        override val folder: File
            get() = File(context.cacheDir, "modules/$moduleId").apply { mkdirs() }
    }

    /** how soon a failed call has to have failed to be worth retrying at once */
    private const val TransientRetryWindowMs = 1_500L

    private fun String.toMediaTypeOrNull() =
        runCatching { this.toMediaType() }.getOrNull()
}
