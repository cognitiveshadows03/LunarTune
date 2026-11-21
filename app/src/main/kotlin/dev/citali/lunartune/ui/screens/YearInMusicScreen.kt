/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.citali.lunartune.viewmodels.YearInMusicViewModel

/**
 * Year in Music entry point.
 *
 * The reel itself lives in `ui.screens.yearinmusic`; this file keeps the navigation-facing name
 * stable for NavigationBuilder.
 */
@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) = dev.citali.lunartune.ui.screens.yearinmusic.YearInMusicScreen(
    navController = navController,
    initialYear = initialYear,
    viewModel = viewModel,
)
