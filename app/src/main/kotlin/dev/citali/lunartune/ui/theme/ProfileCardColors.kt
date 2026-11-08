/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colours lifted from an account's avatar, so the identity card carries the account's own
 * palette instead of a single house colour. Both entries are `null` until (and unless) the
 * avatar loads.
 */
@Immutable
data class ProfileCardColors(
    val accent: Color? = null,
    val deep: Color? = null,
) {
    val hasTint: Boolean get() = accent != null
}

/**
 * Avatar-derived colours are expensive enough to be worth keeping for the life of the process:
 * the profile screen can be opened a dozen times in a session with the same picture.
 */
private val profileCardColorCache = LinkedHashMap<String, ProfileCardColors>()

private const val AvatarDecodePx = 96

/**
 * Reads the avatar for [avatarUrl] once and returns its dominant/vibrant colours.
 *
 * Extraction is best-effort: a missing, broken or greyscale avatar simply yields
 * [ProfileCardColors] with `hasTint == false` and the caller keeps the plain card.
 */
@Composable
fun rememberProfileCardColors(avatarUrl: String?): ProfileCardColors {
    val context = LocalContext.current
    val cached = remember(avatarUrl) { avatarUrl?.let { synchronized(profileCardColorCache) { profileCardColorCache[it] } } }
    var colors by remember(avatarUrl) { mutableStateOf(cached ?: ProfileCardColors()) }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNullOrBlank() || cached != null) return@LaunchedEffect

        val extracted =
            withContext(Dispatchers.IO) {
                runCatching { extractProfileCardColors(context.applicationContext, avatarUrl) }.getOrNull()
            }

        if (extracted != null && extracted.hasTint) {
            synchronized(profileCardColorCache) {
                if (profileCardColorCache.size > 8) profileCardColorCache.clear()
                profileCardColorCache[avatarUrl] = extracted
            }
            colors = extracted
        }
    }

    return colors
}

private suspend fun extractProfileCardColors(
    context: Context,
    url: String,
): ProfileCardColors {
    val request =
        ImageRequest
            .Builder(context)
            .data(url)
            .size(Size(AvatarDecodePx, AvatarDecodePx))
            .allowHardware(false)
            .build()

    val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return ProfileCardColors()

    val palette = Palette.from(bitmap).maximumColorCount(24).generate()

    val accent =
        palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
            ?: return ProfileCardColors()

    val deep =
        palette.darkVibrantSwatch
            ?: palette.darkMutedSwatch
            ?: palette.dominantSwatch
            ?: accent

    return ProfileCardColors(
        accent = Color(accent.rgb),
        deep = Color(deep.rgb),
    )
}
