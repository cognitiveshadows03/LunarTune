/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.view.drawToBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import dev.citali.lunartune.R
import dev.citali.lunartune.ui.component.LyricsShareImageOptions
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

object ComposeToImage {
    private tailrec fun Context.findActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        val config = bitmap.config
        if (config != Bitmap.Config.HARDWARE && config != null) return bitmap
        return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: bitmap
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pixelCopyViewBitmap(view: View): Bitmap? {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
        val activity = view.context.findActivity() ?: return null
        val window = activity.window ?: return null

        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect =
            Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height,
            )

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val copyResult =
            suspendCancellableCoroutine { cont ->
                PixelCopy.request(
                    window,
                    rect,
                    bitmap,
                    { result -> cont.resume(result) },
                    Handler(Looper.getMainLooper()),
                )
            }
        return if (copyResult == PixelCopy.SUCCESS) bitmap else null
    }

    suspend fun captureViewBitmap(
        view: View,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        backgroundColor: Int? = null,
    ): Bitmap {
        val fallbackBitmap =
            runCatching {
                view.drawToBitmap()
            }.getOrElse {
                val w = view.width.coerceAtLeast(1)
                val h = view.height.coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    backgroundColor?.let { Canvas(bmp).drawColor(it) }
                }
            }

        val original =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopyViewBitmap(view) ?: fallbackBitmap
            } else {
                fallbackBitmap
            }
        val needsScale =
            (targetWidth != null && targetWidth > 0 && targetWidth != original.width) ||
                (targetHeight != null && targetHeight > 0 && targetHeight != original.height)
        val base =
            if (needsScale) {
                val safeOriginal = ensureSoftwareBitmap(original)
                val tw = targetWidth ?: original.width
                val th = targetHeight ?: (original.height * tw / original.width)
                ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeOriginal, tw, th, true))
            } else {
                ensureSoftwareBitmap(original)
            }
        if (backgroundColor != null) {
            val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(backgroundColor)
            c.drawBitmap(base, 0f, 0f, null)
            return out
        }
        return base
    }

    fun cropBitmap(
        source: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val safeLeft = left.coerceIn(0, safeSource.width.coerceAtLeast(1) - 1)
        val safeTop = top.coerceIn(0, safeSource.height.coerceAtLeast(1) - 1)
        val safeWidth = width.coerceIn(1, safeSource.width - safeLeft)
        val safeHeight = height.coerceIn(1, safeSource.height - safeTop)
        return ensureSoftwareBitmap(Bitmap.createBitmap(safeSource, safeLeft, safeTop, safeWidth, safeHeight))
    }

    fun fitBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        backgroundColor: Int,
    ): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(backgroundColor)

        val scale =
            minOf(
                targetWidth.toFloat() / safeSource.width.coerceAtLeast(1),
                targetHeight.toFloat() / safeSource.height.coerceAtLeast(1),
            )
        val scaledW = (safeSource.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (safeSource.height * scale).toInt().coerceAtLeast(1)
        val scaled =
            if (scaledW != safeSource.width || scaledH != safeSource.height) {
                ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeSource, scaledW, scaledH, true))
            } else {
                safeSource
            }

        val dx = ((targetWidth - scaled.width) / 2f)
        val dy = ((targetHeight - scaled.height) / 2f)
        canvas.drawBitmap(scaled, dx, dy, null)
        return out
    }

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        glassStyle: dev.citali.lunartune.ui.component.LyricsGlassStyle? = null,
        shareOptions: LyricsShareImageOptions = LyricsShareImageOptions(),
    ): Bitmap =
        withContext(Dispatchers.Default) {
            val style = glassStyle ?: dev.citali.lunartune.ui.component.LyricsGlassStyle.FrostedDark
            val canvasWidth = width.coerceAtLeast(1)
            val canvasHeight = height.coerceAtLeast(1)
            val baseSize = minOf(canvasWidth, canvasHeight)
            val bitmap = createBitmap(canvasWidth, canvasHeight)
            val canvas = Canvas(bitmap)

            val mainTextColor =
                textColor
                    ?: style.textColor.let {
                        ((it.alpha * 255).toInt() shl 24) or
                            ((it.red * 255).toInt() shl 16) or
                            ((it.green * 255).toInt() shl 8) or
                            (it.blue * 255).toInt()
                    }
            val secondaryTxtColor =
                secondaryTextColor
                    ?: style.secondaryTextColor.let {
                        ((it.alpha * 255).toInt() shl 24) or
                            ((it.red * 255).toInt() shl 16) or
                            ((it.green * 255).toInt() shl 8) or
                            (it.blue * 255).toInt()
                    }
            val bgColor = backgroundColor ?: 0xFF121212.toInt()

            var coverArtBitmap: Bitmap? = null
            if (coverArtUrl != null) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverArtUrl)
                            .size(max(canvasWidth, canvasHeight))
                            .allowHardware(false)
                            .build()
                    val result = imageLoader.execute(request)
                    coverArtBitmap = result.image?.toBitmap()
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            val fittedArt =
                coverArtBitmap?.let {
                    fitBitmap(
                        source = it,
                        targetWidth = canvasWidth,
                        targetHeight = canvasHeight,
                        backgroundColor = bgColor,
                    )
                }

            if (fittedArt != null) {
                val blurredBackground = blurBitmap(fittedArt, shareOptions.sanitizedBlurRadius)
                canvas.drawBitmap(blurredBackground, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                canvas.drawColor(bgColor)
            }

            val dimPaint =
                Paint().apply {
                    color =
                        android.graphics.Color.argb(
                            ((style.backgroundDimAlpha * shareOptions.sanitizedDimAmount).coerceIn(0f, 0.95f) * 255).toInt(),
                            0,
                            0,
                            0,
                        )
                    isAntiAlias = true
                }
            canvas.drawRect(RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat()), dimPaint)

            val glassMargin = baseSize * 0.045f
            val glassLeft = glassMargin
            val glassTop = glassMargin
            val glassRight = canvasWidth - glassMargin
            val glassBottom = canvasHeight - glassMargin
            val glassWidth = glassRight - glassLeft
            val glassHeight = glassBottom - glassTop
            val glassCornerRadius = baseSize * 0.05f

            val glassRect = RectF(glassLeft, glassTop, glassRight, glassBottom)
            val glassPath =
                Path().apply {
                    addRoundRect(glassRect, glassCornerRadius, glassCornerRadius, Path.Direction.CW)
                }

            if (fittedArt != null) {
                val frostedCrop = blurBitmap(fittedArt, (shareOptions.sanitizedBlurRadius + 10f).coerceIn(8f, 48f))
                canvas.withClip(glassPath) {
                    drawBitmap(frostedCrop, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }

            val glassBgPaint =
                Paint().apply {
                    color =
                        style.surfaceTint.let {
                            android.graphics.Color.argb(
                                (style.surfaceAlpha * 255).toInt(),
                                (it.red * 255).toInt(),
                                (it.green * 255).toInt(),
                                (it.blue * 255).toInt(),
                            )
                        }
                    isAntiAlias = true
                }
            canvas.drawRoundRect(glassRect, glassCornerRadius, glassCornerRadius, glassBgPaint)

            val overlayPaint =
                Paint().apply {
                    color =
                        style.overlayColor.let {
                            android.graphics.Color.argb(
                                (style.overlayAlpha * 255).toInt(),
                                (it.red * 255).toInt(),
                                (it.green * 255).toInt(),
                                (it.blue * 255).toInt(),
                            )
                        }
                    isAntiAlias = true
                }
            canvas.drawRoundRect(glassRect, glassCornerRadius, glassCornerRadius, overlayPaint)

            val borderPaint =
                Paint().apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                    color = android.graphics.Color.argb(25, 255, 255, 255)
                    isAntiAlias = true
                }
            canvas.drawRoundRect(glassRect, glassCornerRadius, glassCornerRadius, borderPaint)

            val contentPadding = minOf(glassWidth, glassHeight) * 0.08f
            val contentLeft = glassLeft + contentPadding
            val contentTop = glassTop + contentPadding
            val contentRight = glassRight - contentPadding

            val imageCornerRadius = baseSize * 0.035f
            val coverSize = minOf(glassWidth * 0.18f, glassHeight * 0.15f)
            val topRowGap = baseSize * 0.035f

            val titlePaint =
                TextPaint().apply {
                    color = mainTextColor
                    textSize = baseSize * 0.038f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                    letterSpacing = -0.02f
                }
            val artistPaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = baseSize * 0.028f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }

            val showingArtwork = shareOptions.showArtwork && coverArtBitmap != null
            if (showingArtwork) {
                val rect = RectF(contentLeft, contentTop, contentLeft + coverSize, contentTop + coverSize)
                val path =
                    Path().apply {
                        addRoundRect(rect, imageCornerRadius, imageCornerRadius, Path.Direction.CW)
                    }
                canvas.withClip(path) {
                    drawBitmap(coverArtBitmap ?: return@withClip, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                val artBorderPaint =
                    Paint().apply {
                        this.style = Paint.Style.STROKE
                        strokeWidth = 1f
                        color = android.graphics.Color.argb(38, 255, 255, 255)
                        isAntiAlias = true
                    }
                canvas.drawRoundRect(rect, imageCornerRadius, imageCornerRadius, artBorderPaint)
            }

            val textMaxWidth =
                if (showingArtwork) {
                    (contentRight - contentLeft - coverSize - topRowGap).toInt()
                } else {
                    (contentRight - contentLeft).toInt()
                }
            val textStartX = if (showingArtwork) contentLeft + coverSize + topRowGap else contentLeft
            val headerAlignment = if (showingArtwork) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER

            val titleLayout =
                StaticLayout.Builder
                    .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth)
                    .setAlignment(headerAlignment)
                    .setMaxLines(1)
                    .build()
            val artistLayout =
                StaticLayout.Builder
                    .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth)
                    .setAlignment(headerAlignment)
                    .setMaxLines(1)
                    .build()

            val topBlockHeight = if (showingArtwork) coverSize else (titleLayout.height + artistLayout.height + 6f)
            val imageCenter = contentTop + topBlockHeight / 2f
            val textBlockHeight = titleLayout.height + artistLayout.height + 6f
            val textBlockY = imageCenter - textBlockHeight / 2f

            canvas.withTranslation(textStartX, textBlockY) {
                titleLayout.draw(this)
                translate(0f, titleLayout.height.toFloat() + 6f)
                artistLayout.draw(this)
            }

            val lyricsPaint =
                TextPaint().apply {
                    color = mainTextColor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    letterSpacing = -0.01f
                }

            val lyricsMaxWidth = (glassWidth * 0.85f).toInt()
            val logoBlockHeight = (baseSize * 0.08f).toInt()
            val headerBottom = if (showingArtwork) contentTop + coverSize else (textBlockY + textBlockHeight)
            val lyricsTop = headerBottom + baseSize * 0.045f
            val lyricsBottom = glassBottom - (logoBlockHeight + contentPadding)
            val availableLyricsHeight = lyricsBottom - lyricsTop

            var lyricsTextSize = baseSize * 0.055f
            var lyricsLayout: StaticLayout
            do {
                lyricsPaint.textSize = lyricsTextSize
                lyricsLayout =
                    StaticLayout.Builder
                        .obtain(
                            lyrics,
                            0,
                            lyrics.length,
                            lyricsPaint,
                            lyricsMaxWidth,
                        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .setLineSpacing(8f, 1.35f)
                        .setMaxLines(10)
                        .build()
                if (lyricsLayout.height > availableLyricsHeight) {
                    lyricsTextSize -= 2f
                } else {
                    break
                }
            } while (lyricsTextSize > 22f)

            val lyricsYOffset = lyricsTop + (availableLyricsHeight - lyricsLayout.height) / 2f
            canvas.withTranslation(glassLeft + (glassWidth - lyricsMaxWidth) / 2f, lyricsYOffset) {
                lyricsLayout.draw(this)
            }

            AppLogo(
                context = context,
                canvas = canvas,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                padding = contentLeft,
                bottomPadding = glassBottom - contentPadding,
                textColor = secondaryTxtColor,
            )

            return@withContext bitmap
        }

    private fun blurBitmap(
        source: Bitmap,
        radius: Float,
    ): Bitmap {
        val safe = ensureSoftwareBitmap(source)
        val safeRadius = radius.coerceIn(0f, 48f)
        if (safeRadius <= 0.5f) return safe
        val maxDimension = max(safe.width, safe.height)
        if (maxDimension <= 720 || safeRadius < 8f) {
            return stackBlur(safe, safeRadius.roundToInt().coerceAtLeast(1))
        }

        val scale = 720f / maxDimension.toFloat()
        val scaledWidth = (safe.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (safe.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = ensureSoftwareBitmap(Bitmap.createScaledBitmap(safe, scaledWidth, scaledHeight, true))
        val blurred = stackBlur(scaled, (safeRadius * scale).roundToInt().coerceAtLeast(1))
        return ensureSoftwareBitmap(Bitmap.createScaledBitmap(blurred, safe.width, safe.height, true))
    }

    private fun stackBlur(
        source: Bitmap,
        radius: Int,
    ): Bitmap {
        val bitmap = ensureSoftwareBitmap(source.copy(Bitmap.Config.ARGB_8888, true))
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val wm = width - 1
        val hm = height - 1
        val div = radius + radius + 1
        val red = IntArray(width * height)
        val green = IntArray(width * height)
        val blue = IntArray(width * height)
        val vMin = IntArray(max(width, height))
        val divSum = ((div + 1) shr 1).let { it * it }
        val divTable = IntArray(256 * divSum) { it / divSum }
        val stack = Array(div) { IntArray(3) }

        var yi = 0
        var yw = 0
        for (y in 0 until height) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0

            for (i in -radius..radius) {
                val p = pixels[yi + i.coerceIn(0, wm)]
                val sir = stack[i + radius]
                sir[0] = p shr 16 and 0xFF
                sir[1] = p shr 8 and 0xFF
                sir[2] = p and 0xFF
                val rbs = radius + 1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }

            var stackPointer = radius
            for (x in 0 until width) {
                red[yi] = divTable[rsum]
                green[yi] = divTable[gsum]
                blue[yi] = divTable[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                val stackStart = (stackPointer - radius + div) % div
                val sir = stack[stackStart]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vMin[x] = (x + radius + 1).coerceAtMost(wm)
                }
                val p = pixels[yw + vMin[x]]
                sir[0] = p shr 16 and 0xFF
                sir[1] = p shr 8 and 0xFF
                sir[2] = p and 0xFF

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackPointer = (stackPointer + 1) % div
                val nextSir = stack[stackPointer]

                routsum += nextSir[0]
                goutsum += nextSir[1]
                boutsum += nextSir[2]

                rinsum -= nextSir[0]
                ginsum -= nextSir[1]
                binsum -= nextSir[2]
                yi++
            }
            yw += width
        }

        for (x in 0 until width) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var yp = -radius * width

            for (i in -radius..radius) {
                val yiIndex = max(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = red[yiIndex]
                sir[1] = green[yiIndex]
                sir[2] = blue[yiIndex]
                val rbs = radius + 1 - kotlin.math.abs(i)
                rsum += red[yiIndex] * rbs
                gsum += green[yiIndex] * rbs
                bsum += blue[yiIndex] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) yp += width
            }

            var yiIndex = x
            var stackPointer = radius
            for (y in 0 until height) {
                pixels[yiIndex] =
                    pixels[yiIndex] and -0x1000000 or
                    (divTable[rsum] shl 16) or
                    (divTable[gsum] shl 8) or
                    divTable[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                val stackStart = (stackPointer - radius + div) % div
                val sir = stack[stackStart]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vMin[y] = ((y + radius + 1).coerceAtMost(hm)) * width
                }
                val p = x + vMin[y]
                sir[0] = red[p]
                sir[1] = green[p]
                sir[2] = blue[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackPointer = (stackPointer + 1) % div
                val nextSir = stack[stackPointer]

                routsum += nextSir[0]
                goutsum += nextSir[1]
                boutsum += nextSir[2]

                rinsum -= nextSir[0]
                ginsum -= nextSir[1]
                binsum -= nextSir[2]

                yiIndex += width
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun AppLogo(
        context: Context,
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        padding: Float,
        bottomPadding: Float = canvasHeight - padding,
        textColor: Int,
        logoColor: Int? = null,
    ) {
        val baseSize = minOf(canvasWidth, canvasHeight).toFloat()
        val logoSize = (baseSize * 0.055f).toInt().coerceAtLeast(1)

        val brand =
            context.getDrawable(R.drawable.lunartune_brand_round)?.toBitmap(logoSize, logoSize)
                ?: context.getDrawable(R.drawable.small_icon)?.toBitmap(logoSize, logoSize)

        val appName = context.getString(R.string.app_name)
        val appNamePaint =
            TextPaint().apply {
                color = textColor
                textSize = baseSize * 0.028f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                isAntiAlias = true
                letterSpacing = 0.02f
            }

        val logoLeft = padding
        val logoTop = bottomPadding - logoSize
        val bitmapPaint =
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                if (logoColor != null) {
                    colorFilter = PorterDuffColorFilter(logoColor, PorterDuff.Mode.SRC_IN)
                }
            }

        brand?.let { source ->
            val clip =
                Path().apply {
                    addRoundRect(
                        RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize),
                        logoSize / 2f,
                        logoSize / 2f,
                        Path.Direction.CW,
                    )
                }
            canvas.withClip(clip) {
                drawBitmap(source, null, RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize), bitmapPaint)
            }
        }

        val textX = padding + logoSize + 10f
        val textY = logoTop + logoSize / 2f + appNamePaint.textSize * 0.3f
        canvas.drawText(appName, textX, textY, appNamePaint)
    }

    /** Story/portrait card geometry: 9:16 and nothing else. */
    private const val PlayerCardWidth = 1080
    private const val PlayerCardHeight = 1920

    /**
     * A portrait player card in the story format: the artwork fills the whole
     * 9:16 canvas, lightly blurred and dimmed, and a translucent centred card
     * sits on top of it with the square cover, the song and the app mark. No
     * progress indicator or drop shadow. One fixed portrait layout - no
     * landscape variant, no caller-supplied size. The position/duration
     * arguments stay in the signature for existing callers but are not drawn.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createPlayerCardImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        positionMs: Long,
        durationMs: Long,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            val w = PlayerCardWidth
            val h = PlayerCardHeight
            val bitmap = createBitmap(w, h)
            val canvas = Canvas(bitmap)
            val bg = 0xFF14100F.toInt()
            val white = android.graphics.Color.WHITE
            val secondary = android.graphics.Color.argb(190, 255, 255, 255)

            // Load the artwork once; it is used both full-bleed and in the card.
            var cover: Bitmap? = null
            if (!coverArtUrl.isNullOrBlank()) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverArtUrl)
                            .size(max(w, h))
                            .allowHardware(false)
                            .build()
                    cover = ImageLoader(context).execute(request).image?.toBitmap()
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            // Full-bleed backdrop: crop the cover to 9:16 so there are no bars,
            // then soften and dim it so the card in front stays readable.
            val backdrop = createBitmap(w, h)
            val backdropCanvas = Canvas(backdrop)
            backdropCanvas.drawColor(bg)
            if (cover != null) {
                val scale = maxOf(w.toFloat() / cover.width, h.toFloat() / cover.height)
                val scaledW = (cover.width * scale).roundToInt().coerceAtLeast(1)
                val scaledH = (cover.height * scale).roundToInt().coerceAtLeast(1)
                val scaled =
                    ensureSoftwareBitmap(Bitmap.createScaledBitmap(cover, scaledW, scaledH, true))
                val srcLeft = ((scaledW - w) / 2).coerceAtLeast(0)
                val srcTop = ((scaledH - h) / 2).coerceAtLeast(0)
                backdropCanvas.drawBitmap(
                    scaled,
                    Rect(srcLeft, srcTop, srcLeft + w, srcTop + h),
                    RectF(0f, 0f, w.toFloat(), h.toFloat()),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
            }
            canvas.drawBitmap(
                blurBitmap(backdrop, 20f),
                0f,
                0f,
                Paint(Paint.FILTER_BITMAP_FLAG),
            )

            // The glass follows the artwork: dark covers get a dark translucent
            // card, bright covers a light one. Sample the cover once, cheaply,
            // so the words and app mark can keep their contrast on either.
            val lightArt = (cover?.averageLuminance() ?: 0f) > 0.55f
            val cardFill =
                if (lightArt) {
                    android.graphics.Color.argb(150, 246, 246, 250)
                } else {
                    android.graphics.Color.argb(120, 20, 18, 24)
                }
            val cardRim =
                if (lightArt) {
                    android.graphics.Color.argb(70, 40, 38, 44)
                } else {
                    android.graphics.Color.argb(78, 255, 255, 255)
                }
            val onCard = if (lightArt) 0xFF17161B.toInt() else white
            val secondaryOnCard =
                if (lightArt) {
                    android.graphics.Color.argb(200, 23, 22, 27)
                } else {
                    secondary
                }
            val scrim =
                Paint().apply {
                    shader =
                        LinearGradient(
                            0f,
                            0f,
                            0f,
                            h.toFloat(),
                            android.graphics.Color.argb(105, 10, 8, 10),
                            android.graphics.Color.argb(220, 10, 8, 10),
                            Shader.TileMode.CLAMP,
                        )
                }
            canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), scrim)

            // Centred glass card, a little wider and noticeably taller so the
            // proportions read like the reference song card. The full-bleed
            // blurred artwork above is deliberately untouched.
            val cardWidth = w * 0.72f
            val cardHeight = h * 0.64f
            val cardLeft = (w - cardWidth) / 2f
            val cardRight = cardLeft + cardWidth
            val cardTop = (h - cardHeight) / 2f
            val cardBottom = cardTop + cardHeight
            val inset = cardWidth * 0.055f
            val artLeft = cardLeft + inset
            val artSize = cardWidth - inset * 2f
            val artTop = cardTop + inset
            val gap = cardWidth * 0.05f
            val textWidth = artSize.toInt()
            val corner = cardWidth * 0.055f
            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

            val titlePaint =
                TextPaint().apply {
                    color = onCard
                    textSize = cardWidth * 0.060f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    isAntiAlias = true
                }
            val artistPaint =
                TextPaint().apply {
                    color = secondaryOnCard
                    textSize = cardWidth * 0.038f
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    isAntiAlias = true
                }

            fun measure(
                text: String,
                paint: TextPaint,
                maxLines: Int,
            ): StaticLayout =
                StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(maxLines)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()

            val titleLayout = measure(songTitle, titlePaint, 2)
            val artistLayout = measure(artistName, artistPaint, 1)

            // Translucent glass instead of an opaque slab: no drop shadow, just
            // a quiet hairline rim. The blurred cover now shows through the
            // empty lower part of the card.
            canvas.drawRoundRect(
                cardRect,
                corner,
                corner,
                Paint().apply {
                    color = cardFill
                    isAntiAlias = true
                },
            )
            canvas.drawRoundRect(
                cardRect,
                corner,
                corner,
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = cardRim
                    isAntiAlias = true
                },
            )

            val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
            val artRadius = cardWidth * 0.035f
            val artPath =
                Path().apply { addRoundRect(artRect, artRadius, artRadius, Path.Direction.CW) }
            if (cover != null) {
                // Crop to the square, so a non-square cover never gets letterbox bars.
                val artPx = artSize.toInt()
                val scale = maxOf(artSize / cover.width, artSize / cover.height)
                val scaledW = (cover.width * scale).roundToInt().coerceAtLeast(artPx)
                val scaledH = (cover.height * scale).roundToInt().coerceAtLeast(artPx)
                val scaled =
                    ensureSoftwareBitmap(Bitmap.createScaledBitmap(cover, scaledW, scaledH, true))
                val srcLeft = ((scaledW - artPx) / 2).coerceAtLeast(0)
                val srcTop = ((scaledH - artPx) / 2).coerceAtLeast(0)
                canvas.withClip(artPath) {
                    drawBitmap(
                        scaled,
                        Rect(srcLeft, srcTop, srcLeft + artPx, srcTop + artPx),
                        artRect,
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                }
            } else {
                canvas.drawRoundRect(
                    artRect,
                    artRadius,
                    artRadius,
                    Paint().apply {
                        color = android.graphics.Color.argb(70, 201, 182, 255)
                        isAntiAlias = true
                    },
                )
            }
            canvas.drawRoundRect(
                artRect,
                artRadius,
                artRadius,
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = android.graphics.Color.argb(45, 255, 255, 255)
                    isAntiAlias = true
                },
            )

            var y = artRect.bottom + gap
            canvas.withTranslation(artLeft, y) { titleLayout.draw(this) }
            y += titleLayout.height.toFloat() + gap * 0.4f
            canvas.withTranslation(artLeft, y) { artistLayout.draw(this) }

            // No progress bar or elapsed/duration labels: the words sit just
            // under the cover and the app mark stays pinned to the bottom,
            // leaving the translucent space between them.
            AppLogo(
                context = context,
                canvas = canvas,
                canvasWidth = w,
                canvasHeight = h,
                padding = artLeft,
                bottomPadding = cardBottom - inset,
                textColor = secondaryOnCard,
                logoColor = onCard,
            )

            bitmap
        }

    /** Average perceived brightness of a bitmap in 0..1, sampled small and fast. */
    private fun Bitmap.averageLuminance(): Float {
        val safe = ensureSoftwareBitmap(this)
        val sample = Bitmap.createScaledBitmap(safe, 24, 24, true)
        var sum = 0.0
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                sum +=
                    0.2126 * android.graphics.Color.red(pixel) +
                        0.7152 * android.graphics.Color.green(pixel) +
                        0.0722 * android.graphics.Color.blue(pixel)
            }
        }
        val total = (sample.width * sample.height * 255).toDouble().coerceAtLeast(1.0)
        return (sum / total).toFloat().coerceIn(0f, 1f)
    }

    fun saveBitmapAsFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Uri {
        val safeBitmap = ensureSoftwareBitmap(bitmap)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LunarTune")
                }
            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: throw IllegalStateException("Failed to create new MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                imageFile,
            )
        }
    }
}
