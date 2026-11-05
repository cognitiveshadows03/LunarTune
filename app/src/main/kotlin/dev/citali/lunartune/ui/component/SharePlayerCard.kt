/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.citali.lunartune.R
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.utils.ComposeToImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * The share action lives in a menu that leaves composition as soon as it is
 * tapped. A rememberCoroutineScope tied to that menu is cancelled immediately,
 * which used to kill the export before it started and surface as "unknown
 * error". This scope outlives the menu on purpose.
 */
private val shareCardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/**
 * One-tap export of the stock 9:16 player card: blurred artwork backdrop and
 * the adaptive translucent card. It is saved straight into Pictures/LunarTune
 * (where the lyrics card goes too) and that saved image is handed to the share
 * sheet; if MediaStore refuses the insert we fall back to a cache copy so the
 * share still works.
 */
fun sharePlayerCard(
    context: Context,
    mediaMetadata: MediaMetadata?,
    artworkUrl: String?,
    position: Long,
    duration: Long,
) {
    val metadata = mediaMetadata ?: return
    shareCardScope.launch {
        val bitmap =
            runCatching {
                ComposeToImage.createPlayerCardImage(
                    context = context,
                    coverArtUrl = artworkUrl,
                    songTitle = metadata.title,
                    artistName = metadata.artists.joinToString { it.name },
                    positionMs = position,
                    durationMs = duration,
                )
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.e("SharePlayerCard", "Failed to build the player card", throwable)
            }.getOrNull()

        if (bitmap == null) {
            Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
            return@launch
        }

        val uri =
            withContext(Dispatchers.IO) {
                runCatching {
                    ComposeToImage.saveBitmapAsFile(
                        context = context,
                        bitmap = bitmap,
                        fileName = shareCardFileName(),
                    )
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w("SharePlayerCard", "Gallery save failed, using the cache copy", throwable)
                }.getOrNull() ?: writeShareCardToCache(context, bitmap)
            }

        if (uri == null) {
            Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
            return@launch
        }

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        runCatching {
            context.startActivity(
                Intent
                    .createChooser(intent, context.getString(R.string.share_card))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.e("SharePlayerCard", "Could not open the share sheet", throwable)
            Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareCardFileName(): String = "LunarTune-card-${System.currentTimeMillis()}"

private fun writeShareCardToCache(
    context: Context,
    bitmap: Bitmap,
): Uri? =
    runCatching {
        val directory = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(directory, "${shareCardFileName()}.png")
        FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    }.getOrNull()
