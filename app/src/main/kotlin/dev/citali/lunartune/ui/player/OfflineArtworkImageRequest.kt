/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dev.citali.lunartune.utils.DownloadedArtwork

@Composable
internal fun rememberOfflineArtworkImageRequest(imageUrl: String?, fixedSizePx: Int? = null): ImageRequest? {
    val context = LocalContext.current
    // Downloaded songs have their cover on disk, so prefer that over a network
    // fetch: it is what keeps artwork showing once Coil's cache is cleared or
    // the device is offline.
    val localArtwork = remember(context, imageUrl) {
        DownloadedArtwork.localFile(context, imageUrl)
    }
    return remember(context, imageUrl, localArtwork, fixedSizePx) {
        imageUrl
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { url ->
                val source: Any = localArtwork ?: url
                ImageRequest
                    .Builder(context)
                    .data(source)
                    // A fixed decode size (with its own memory key) stops a request measured
                    // at thumbnail size from being reused for full-size artwork.
                    .apply { if (fixedSizePx != null) size(fixedSizePx) }
                    .memoryCacheKey(if (fixedSizePx != null) "$url#$fixedSizePx" else url)
                    .diskCacheKey(url)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
    }
}
