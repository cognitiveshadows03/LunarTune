/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import com.atilika.kuromoji.util.ResourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

sealed interface JapaneseLanguagePackState {
    data object NotInstalled : JapaneseLanguagePackState

    data class Downloading(
        val progressPercent: Int?,
    ) : JapaneseLanguagePackState

    data object Removing : JapaneseLanguagePackState

    data class Installed(
        val sizeBytes: Long,
    ) : JapaneseLanguagePackState

    data object Failed : JapaneseLanguagePackState
}

/**
 * Downloads the optional Kuromoji IPADIC data used for offline Japanese romanization.
 * The dictionary is excluded from the APK and kept in app-private storage instead.
 */
object JapaneseLanguagePackManager {
    private const val PACK_DIRECTORY = "language_packs"
    private const val PACK_FILE = "kuromoji-ipadic-0.9.0.jar"
    private const val RESOURCE_PREFIX = "com/atilika/kuromoji/ipadic/"
    private const val DOWNLOAD_URL =
        "https://repo.maven.apache.org/maven2/com/atilika/kuromoji/kuromoji-ipadic/0.9.0/kuromoji-ipadic-0.9.0.jar"
    private const val EXPECTED_SHA256 =
        "24909fd751c0b439f7af5131b080eb65bc85062f0c4977ca4a71c76abe74e0b6"

    private val operationMutex = Mutex()
    private val tokenizerLock = Any()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<JapaneseLanguagePackState>(JapaneseLanguagePackState.NotInstalled)
    private val mutableIsInstalled = MutableStateFlow(false)

    val state: StateFlow<JapaneseLanguagePackState> = mutableState.asStateFlow()

    /** Emits only when the installed/uninstalled status changes, not for each progress update. */
    val isInstalled: StateFlow<Boolean> = mutableIsInstalled.asStateFlow()

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    @Volatile
    private var installJob: Job? = null

    @Volatile
    private var cachedTokenizer: DownloadedJapaneseTokenizer? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        val pack = packFile(context)
        partialPackFile(context).delete()
        setState(
            if (pack.isFile && pack.length() > 0L) {
                JapaneseLanguagePackState.Installed(pack.length())
            } else {
                JapaneseLanguagePackState.NotInstalled
            },
        )
    }

    @Synchronized
    fun install() {
        if (installJob?.isActive == true ||
            mutableState.value is JapaneseLanguagePackState.Downloading ||
            mutableState.value is JapaneseLanguagePackState.Removing ||
            mutableState.value is JapaneseLanguagePackState.Installed
        ) {
            return
        }

        val context = applicationContext
        if (context == null) {
            setState(JapaneseLanguagePackState.Failed)
            return
        }

        setState(JapaneseLanguagePackState.Downloading(progressPercent = 0))
        val job =
            managerScope.launch {
                operationMutex.withLock {
                    installPack(context)
                }
            }
        installJob = job
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException && mutableState.value is JapaneseLanguagePackState.Downloading) {
                setState(installedStateOrNotInstalled(context))
            }
        }
    }

    fun cancelDownload() {
        activeConnection?.disconnect()
        installJob?.cancel()
    }

    fun remove() {
        if (mutableState.value is JapaneseLanguagePackState.Downloading ||
            mutableState.value is JapaneseLanguagePackState.Removing
        ) {
            return
        }

        val context = applicationContext ?: return
        setState(JapaneseLanguagePackState.Removing)
        managerScope.launch {
            operationMutex.withLock {
                val pack = packFile(context)
                synchronized(tokenizerLock) { cachedTokenizer = null }
                val removed = !pack.exists() || pack.delete()
                if (removed) {
                    setState(JapaneseLanguagePackState.NotInstalled)
                } else {
                    Timber.tag("JapaneseLanguagePack").w("Could not remove the Japanese language pack")
                    setState(JapaneseLanguagePackState.Installed(pack.length()))
                }
            }
        }
    }

    /** Returns null when the user has not installed the optional pack. */
    fun tokenizerOrNull(): DownloadedJapaneseTokenizer? {
        if (!mutableIsInstalled.value) return null
        val context = applicationContext ?: return null
        val pack = packFile(context)

        return synchronized(tokenizerLock) {
            if (!mutableIsInstalled.value) return@synchronized null
            cachedTokenizer?.let { return@synchronized it }
            if (!pack.isFile) {
                setState(JapaneseLanguagePackState.NotInstalled)
                return@synchronized null
            }

            try {
                val tokenizer =
                    ZipFile(pack).use { archive ->
                        val resolver =
                            ResourceResolver { resourceName ->
                                val entry =
                                    archive.getEntry(RESOURCE_PREFIX + resourceName)
                                        ?: throw IOException("Missing Japanese dictionary resource: $resourceName")
                                archive.getInputStream(entry)
                            }
                        DownloadedJapaneseTokenizer.create(resolver)
                    }
                cachedTokenizer = tokenizer
                if (mutableState.value !is JapaneseLanguagePackState.Removing) {
                    setState(JapaneseLanguagePackState.Installed(pack.length()))
                }
                tokenizer
            } catch (error: Exception) {
                cachedTokenizer = null
                pack.delete()
                setState(JapaneseLanguagePackState.Failed)
                Timber.tag("JapaneseLanguagePack").w(error, "Could not load the Japanese language pack")
                null
            }
        }
    }

    private suspend fun installPack(context: Context) {
        val target = packFile(context)
        val partial = partialPackFile(context)

        try {
            val parent = checkNotNull(target.parentFile) { "Language pack directory is unavailable" }
            check(parent.isDirectory || parent.mkdirs()) { "Could not create the language pack directory" }
            if (partial.exists()) check(partial.delete()) { "Could not clear an incomplete language pack" }

            downloadPack(partial)
            currentCoroutineContext().ensureActive()
            check(sha256(partial).equals(EXPECTED_SHA256, ignoreCase = true)) {
                "Downloaded language pack failed integrity verification"
            }
            currentCoroutineContext().ensureActive()
            if (target.exists()) check(target.delete()) { "Could not replace the installed language pack" }
            check(partial.renameTo(target)) { "Could not install the downloaded language pack" }

            synchronized(tokenizerLock) { cachedTokenizer = null }
            setState(JapaneseLanguagePackState.Installed(target.length()))
        } catch (cancelled: CancellationException) {
            partial.delete()
            setState(installedStateOrNotInstalled(context))
            throw cancelled
        } catch (error: Exception) {
            partial.delete()
            if (!currentCoroutineContext().isActive) {
                setState(installedStateOrNotInstalled(context))
                throw CancellationException("Japanese language pack download cancelled")
            }
            setState(JapaneseLanguagePackState.Failed)
            Timber.tag("JapaneseLanguagePack").w(error, "Could not download the Japanese language pack")
        }
    }

    private suspend fun downloadPack(destination: File) {
        currentCoroutineContext().ensureActive()
        val connection =
            (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
        activeConnection = connection
        try {
            currentCoroutineContext().ensureActive()
            check(connection.responseCode in 200..299) {
                "Language pack server returned HTTP ${connection.responseCode}"
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var downloadedBytes = 0L
            var lastProgressPercent = -1
            setState(JapaneseLanguagePackState.Downloading(progressPercent = 0))

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count

                        val progressPercent =
                            totalBytes?.let {
                                ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 100)
                            }
                        if (progressPercent != null && progressPercent != lastProgressPercent) {
                            setState(JapaneseLanguagePackState.Downloading(progressPercent))
                            lastProgressPercent = progressPercent
                        }
                    }
                }
            }
            setState(JapaneseLanguagePackState.Downloading(progressPercent = 100))
        } finally {
            activeConnection = null
            connection.disconnect()
        }
    }

    private fun installedStateOrNotInstalled(context: Context): JapaneseLanguagePackState {
        val pack = packFile(context)
        return if (pack.isFile && pack.length() > 0L) {
            JapaneseLanguagePackState.Installed(pack.length())
        } else {
            JapaneseLanguagePackState.NotInstalled
        }
    }

    private fun packFile(context: Context): File = File(File(context.filesDir, PACK_DIRECTORY), PACK_FILE)

    private fun partialPackFile(context: Context): File = File(File(context.filesDir, PACK_DIRECTORY), "$PACK_FILE.partial")

    private fun setState(state: JapaneseLanguagePackState) {
        mutableState.value = state
        mutableIsInstalled.value = state is JapaneseLanguagePackState.Installed
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
