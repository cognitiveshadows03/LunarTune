/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OkHttp-backed [Downloader] for NewPipe Extractor. Only used by the download resolution path,
 * so it stays out of the way of normal playback.
 */
internal class NewPipeDownloader : Downloader() {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        var hasUserAgent = false
        var contentType: String? = null
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
            if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            if (name.equals("Content-Type", ignoreCase = true)) contentType = values.firstOrNull()
        }
        if (!hasUserAgent) builder.header("User-Agent", FALLBACK_USER_AGENT)

        val data = request.dataToSend()
        val method = request.httpMethod().uppercase()
        val body =
            when {
                data != null -> data.toRequestBody(contentType?.toMediaTypeOrNull())
                method == "POST" || method == "PUT" || method == "PATCH" -> EMPTY_BODY
                else -> null
            }
        builder.method(method, body)

        client.newCall(builder.build()).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 60L
        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}

/**
 * Initializes NewPipe Extractor exactly once, lazily on first download resolution.
 */
internal object NewPipeBootstrap {
    private const val TAG = "NewPipeFallback"
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(): Boolean {
        if (initialized.get()) return true
        synchronized(this) {
            if (initialized.get()) return true
            return runCatching {
                NewPipe.init(NewPipeDownloader())
                initialized.set(true)
                Timber.tag(TAG).d("NewPipe Extractor initialized")
                true
            }.getOrElse { error ->
                Timber.tag(TAG).e(error, "Failed to initialize NewPipe Extractor")
                reportException(error)
                false
            }
        }
    }
}
