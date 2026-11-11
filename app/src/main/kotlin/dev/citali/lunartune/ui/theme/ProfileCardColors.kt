/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Material You colours for the identity card, taken from the system
 * [MaterialTheme.colorScheme] (wallpaper-based on Android 12+ via dynamic
 * colour) instead of extracting from the avatar, so the card always matches
 * the app theme instead of drifting with the picture.
 */
@Immutable
data class ProfileCardColors(
    val accent: Color? = null,
    val deep: Color? = null,
) {
    val hasTint: Boolean get() = accent != null
}

@Composable
fun rememberProfileCardColors(avatarUrl: String?): ProfileCardColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        ProfileCardColors(
            accent = scheme.primary,
            deep = scheme.primaryContainer,
        )
    }
}
