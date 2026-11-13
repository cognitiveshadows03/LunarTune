/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.runtime.Composable
import dev.citali.lunartune.constants.PlayerBackgroundStyle
import dev.citali.lunartune.constants.PlayerBackgroundStyleKey
import dev.citali.lunartune.constants.PlayerBackgroundStyleKeyFor
import dev.citali.lunartune.constants.PlayerButtonsStyle
import dev.citali.lunartune.constants.PlayerButtonsStyleKey
import dev.citali.lunartune.constants.PlayerButtonsStyleKeyFor
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.constants.PlayerDesignStyleKey
import dev.citali.lunartune.constants.SliderStyle
import dev.citali.lunartune.constants.SliderStyleKey
import dev.citali.lunartune.constants.SliderStyleKeyFor
import dev.citali.lunartune.utils.PreferenceStore
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference

/**
 * The player design styles that offer the customise sheet. Only these carry
 * their own slider/buttons/background set; the rest read the global keys, so a
 * style that was never customised looks exactly as it always did.
 */
val CustomisablePlayerStyles: Set<PlayerDesignStyle> =
    setOf(
        PlayerDesignStyle.V2,
        PlayerDesignStyle.V4,
        PlayerDesignStyle.V5,
        PlayerDesignStyle.V6,
    )

@Composable
fun rememberActiveDesignStyle(): PlayerDesignStyle {
    val (style) = rememberEnumPreference(PlayerDesignStyleKey, PlayerDesignStyle.V7)
    return style
}

@Composable
fun rememberSliderStyleFor(designStyle: PlayerDesignStyle): SliderStyle {
    val (global) = rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)
    val (own) = rememberPreference(SliderStyleKeyFor(designStyle.name), "")
    return SliderStyle.entries.firstOrNull { it.name == own } ?: global
}

@Composable
fun rememberButtonsStyleFor(designStyle: PlayerDesignStyle): PlayerButtonsStyle {
    val (global) = rememberEnumPreference(PlayerButtonsStyleKey, PlayerButtonsStyle.DEFAULT)
    val (own) = rememberPreference(PlayerButtonsStyleKeyFor(designStyle.name), "")
    return PlayerButtonsStyle.entries.firstOrNull { it.name == own } ?: global
}

@Composable
fun rememberBackgroundStyleFor(designStyle: PlayerDesignStyle): PlayerBackgroundStyle {
    val (global) = rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val (own) = rememberPreference(PlayerBackgroundStyleKeyFor(designStyle.name), "")
    return PlayerBackgroundStyle.entries.firstOrNull { it.name == own } ?: global
}

/** The three above, resolved for whichever style is currently in use. */
@Composable
fun rememberActiveSliderStyle(): SliderStyle = rememberSliderStyleFor(rememberActiveDesignStyle())

@Composable
fun rememberActiveButtonsStyle(): PlayerButtonsStyle =
    rememberButtonsStyleFor(rememberActiveDesignStyle())

@Composable
fun rememberActiveBackgroundStyle(): PlayerBackgroundStyle =
    rememberBackgroundStyleFor(rememberActiveDesignStyle())

/**
 * One-shot readers for places that load values outside composition (the
 * carousel seeding its drafts when the selection changes). Null means the style
 * has never been customised and the caller should keep the global value.
 */
object StyleOverrides {
    fun sliderStyle(designStyle: PlayerDesignStyle): SliderStyle? =
        own(designStyle, ::SliderStyleKeyFor).let { raw ->
            SliderStyle.entries.firstOrNull { it.name == raw }
        }

    fun buttonsStyle(designStyle: PlayerDesignStyle): PlayerButtonsStyle? =
        own(designStyle, ::PlayerButtonsStyleKeyFor).let { raw ->
            PlayerButtonsStyle.entries.firstOrNull { it.name == raw }
        }

    fun backgroundStyle(designStyle: PlayerDesignStyle): PlayerBackgroundStyle? =
        own(designStyle, ::PlayerBackgroundStyleKeyFor).let { raw ->
            PlayerBackgroundStyle.entries.firstOrNull { it.name == raw }
        }

    private fun own(
        designStyle: PlayerDesignStyle,
        keyOf: (String) -> androidx.datastore.preferences.core.Preferences.Key<String>,
    ): String? = PreferenceStore.get(keyOf(designStyle.name))?.takeIf { it.isNotBlank() }
}
