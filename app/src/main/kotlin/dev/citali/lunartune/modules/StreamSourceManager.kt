/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.modules

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.citali.lunartune.constants.ModuleEnabledKey
import dev.citali.lunartune.constants.ModulePicksKey
import dev.citali.lunartune.constants.StreamSourceOrderKey
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.dataStore
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

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
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

    private val modules: Map<String, SourceModule> by lazy {
        factories.map { it() }.associateBy { it.info.id }
    }

    /** every inbuilt module, in default priority order */
    fun allModules(): List<SourceModule> = modules.values.toList()

    fun module(id: String): SourceModule? = modules[id]

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

    private fun defaultEnabled(id: String): Boolean = id == MonochromeModule.Id

    /** enabled modules in priority order */
    fun activeModules(): List<SourceModule> =
        priorityOrder().mapNotNull { modules[it] }.filter { isEnabled(it.info.id) }

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
    suspend fun resolve(track: TrackQuery): SourceStream? {
        for (module in activeModules()) {
            val result = runCatching {
                module.resolve(track, picksFor(module.info.id))
            }.onFailure { e ->
                Timber.tag("StreamSource").w(e, "Module %s failed to resolve", module.info.id)
            }.getOrNull() ?: continue
            when (result) {
                is ResolveResult.Found -> {
                    Timber.tag("StreamSource").i(
                        "Module %s served %s (confidence %.2f)",
                        module.info.id, track.title, result.stream.matched.confidence,
                    )
                    return result.stream
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
        modules.values.forEach { module ->
            runCatching { module.attach(Host(context.applicationContext, module.info.id)) }
                .onFailure { e -> Timber.tag("StreamSource").w(e, "Module %s attach failed", module.info.id) }
        }
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
            val builder = Request.Builder().url(request.url).method(request.method, request.body?.toRequestBody(request.contentType?.toMediaTypeOrNull()))
            request.headers.forEach { (name, value) -> builder.header(name, value) }
            httpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.bytes() ?: ByteArray(0)
                val headers = response.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() }
                return HttpResponse(response.code, body, headers)
            }
        }

        override fun log(message: String) {
            Timber.tag("Module:$moduleId").d(message)
        }

        override val folder: File
            get() = File(context.cacheDir, "modules/$moduleId").apply { mkdirs() }
    }

    private fun String.toMediaTypeOrNull() =
        runCatching { this.toMediaType() }.getOrNull()
}
