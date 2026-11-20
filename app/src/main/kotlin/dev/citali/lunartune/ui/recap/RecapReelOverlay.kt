/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.recap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.citali.lunartune.R
import dev.citali.lunartune.recap.RecapText
import dev.citali.lunartune.viewmodels.RecapReelViewModel

private val ReelBlack = Color(0xFF000000)

/**
 * Full-screen highlight reel. Only opened from the year screen once the hidden unlock is set.
 */
@Composable
fun RecapReelOverlay(
    year: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecapReelViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val text = remember(context) { RecapText(context) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(year) { viewModel.open(year) }
    BackHandler(onBack = onClose)

    Box(modifier = modifier.background(ReelBlack)) {
        val data = state.data
        if (data != null) {
            RecapStories(
                data = data,
                chapter = state.chapter,
                text = text,
                callbacks =
                    RecapCallbacks(
                        story = { show -> if (!show) onClose() },
                        chapter = { viewModel.selectChapter(it) },
                        replay = { viewModel.replay() },
                    ),
            )
        } else {
            RecapReelStatus(loading = state.loading, failed = state.failed, onClose = onClose)
        }
    }
}

@Composable
private fun RecapReelStatus(
    loading: Boolean,
    failed: Boolean,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            failed -> Text(stringResource(R.string.recap_load_error), Modifier.align(Alignment.Center), color = Color.White)
            else -> Text(stringResource(R.string.no_listening_data), Modifier.align(Alignment.Center), color = Color.White)
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
        ) {
            Icon(painterResource(R.drawable.close), stringResource(R.string.close), tint = Color.White)
        }
    }
}
