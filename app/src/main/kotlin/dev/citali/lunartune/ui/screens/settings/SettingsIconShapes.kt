/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon

/** Material 3 Expressive shape behind each settings category icon (no two neighbours share one). */
private val SettingsIconPolygons: Map<String, RoundedPolygon> =
    mapOf(
        "account" to MaterialShapes.Cookie9Sided,
        "stats" to MaterialShapes.Clover4Leaf,
        "appearance" to MaterialShapes.Sunny,
        "playback" to MaterialShapes.Flower,
        "lyrics" to MaterialShapes.Cookie6Sided,
        "content" to MaterialShapes.SoftBurst,
        "behavior" to MaterialShapes.Pentagon,
        "integration" to MaterialShapes.Clover8Leaf,
        "ai_integration" to MaterialShapes.VerySunny,
        "internet" to MaterialShapes.Cookie7Sided,
        "storage" to MaterialShapes.Gem,
        "downloads" to MaterialShapes.Puffy,
        "backup_restore" to MaterialShapes.Cookie12Sided,
        "developer_options" to MaterialShapes.Burst,
        "default_links" to MaterialShapes.Cookie4Sided,
        "updates" to MaterialShapes.SoftBoom,
        "about" to MaterialShapes.Flower,
    )

private val FallbackPolygons =
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Pentagon,
    )

@Composable
internal fun settingsIconShape(key: String): Shape =
    (SettingsIconPolygons[key] ?: FallbackPolygons[Math.floorMod(key.hashCode(), FallbackPolygons.size)])
        .toShape()
