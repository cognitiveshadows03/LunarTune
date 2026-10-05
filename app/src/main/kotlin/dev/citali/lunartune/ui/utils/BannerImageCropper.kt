/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.citali.lunartune.ui.utils

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Crops static images to 16:9 and preserves animated GIFs for display-time cropping. */
@Composable
fun rememberBannerImageCropper(
    outputName: String,
    onCropped: (Uri) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let(UCrop::getOutput)?.let(onCropped)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        source ?: return@rememberLauncherForActivityResult
        val directory = File(context.filesDir, "cropped_banners").apply { mkdirs() }
        val mimeType = context.contentResolver.getType(source).orEmpty()
        val isGif = mimeType.equals("image/gif", ignoreCase = true) || source.toString().substringBefore('?').endsWith(".gif", ignoreCase = true)
        if (isGif) {
            // uCrop renders animation sources to a single bitmap. Preserve GIF bytes
            // and let the fixed header viewport crop them at display time instead.
            scope.launch(Dispatchers.IO) {
                val destination = File(directory, "$outputName.gif").apply { delete() }
                val copied = runCatching {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        destination.outputStream().use(input::copyTo)
                    } ?: error("Unable to open GIF")
                    true
                }.getOrDefault(false)
                if (copied) withContext(Dispatchers.Main) { onCropped(Uri.fromFile(destination)) }
            }
            return@rememberLauncherForActivityResult
        }
        val destination = File(directory, "$outputName.jpg").apply { delete() }
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(false)
            setHideBottomControls(false)
            setToolbarTitle("Crop banner")
            setToolbarColor(surface)
            setToolbarWidgetColor(onSurface)
            setRootViewBackgroundColor(surface)
            setActiveControlsWidgetColor(primary)
            setCompressionQuality(92)
        }
        val intent = UCrop.of(source, Uri.fromFile(destination))
            .withAspectRatio(16f, 9f)
            .withMaxResultSize(1600, 900)
            .withOptions(options)
            .getIntent(context)
        cropLauncher.launch(intent)
    }
    return remember(picker) { { picker.launch(arrayOf("image/*")) } }
}
