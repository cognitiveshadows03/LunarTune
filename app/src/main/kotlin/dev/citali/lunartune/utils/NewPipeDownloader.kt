/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import timber.log.Timber
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OkHttp-backed [Downloader] for NewPipe Extractor. Used for JS player deobfuscation,
 * stream resolution, and download fallback; response deadlines keep a stalled resolver
 * from blocking playback indefinitely.
 */
internal class NewPipeDownloader : Downloader() {
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .proxySelector(
                object : ProxySelector() {
                    override fun select(uri: URI?): List<Proxy> = listOf(YouTube.streamOkHttpProxy)

                    override fun connectFailed(
                        uri: URI?,
                        sa: SocketAddress?,
                        ioe: IOException?,
                    ) {
                        Timber.tag("NewPipeFallback").v(ioe, "Proxy connection failed for %s", uri)
                    }
                },
            ).dns(Dns { hostname -> YouTube.dns.lookup(hostname) })
            .retryOnConnectionFailure(true)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = request.url()
        val builder = okhttp3.Request.Builder().url(url)
        var hasUserAgent = false
        var contentType: String? = null
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
            if (name.equals("User-Agent", ignoreCase = true) && values.isNotEmpty()) hasUserAgent = true
            if (name.equals("Content-Type", ignoreCase = true)) contentType = values.firstOrNull()
        }
        if (!hasUserAgent) builder.header("User-Agent", YouTubeClient.USER_AGENT_WEB)

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
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
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
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 30L
        const val CALL_TIMEOUT_SECONDS = 30L
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}

/**
 * Initializes NewPipe Extractor exactly once with the proxy/DoH-aware [NewPipeDownloader].
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
