/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** One scrobble that could not be delivered to the service yet. */
@Serializable
data class PendingScrobble(
    val artist: String,
    val track: String,
    val timestamp: Long,
    val album: String? = null,
    val duration: Int? = null,
)

/**
 * A tiny durable queue for offline scrobbles. It lives in the app's private
 * files dir rather than the music database so enabling/disabling the backup
 * never changes a Room schema, and a corrupt or missing file is simply an
 * empty queue instead of a crash.
 */
class ScrobbleBackupStore(context: Context) {
    private val file = File(context.filesDir, "lastfm_pending_scrobbles.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun <T> edit(block: suspend (MutableList<PendingScrobble>) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.lock()
            try {
                val items = read().toMutableList()
                val result = block(items)
                write(items)
                result
            } finally {
                mutex.unlock()
            }
        }

    suspend fun pendingCount(): Int =
        withContext(Dispatchers.IO) { mutex.withLock { read().size } }

    private fun read(): List<PendingScrobble> =
        if (!file.exists()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<PendingScrobble>>(file.readText()) }
                .getOrDefault(emptyList())
        }

    private fun write(items: List<PendingScrobble>) {
        runCatching { file.writeText(json.encodeToString(items)) }
    }
}
