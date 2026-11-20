/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.citali.lunartune.recap.ObserveRecapUseCase
import dev.citali.lunartune.recap.RecapChapter
import dev.citali.lunartune.recap.RecapData
import dev.citali.lunartune.utils.reportException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RecapReelUiState(
    val year: Int? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val empty: Boolean = false,
    val data: RecapData? = null,
    val chapter: RecapChapter = RecapChapter.INTRO,
)

/** Drives the highlight reel: loads the year's recap and tracks which chapter is showing. */
@HiltViewModel
class RecapReelViewModel @Inject constructor(
    private val observeRecap: ObserveRecapUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(RecapReelUiState())
    val uiState: StateFlow<RecapReelUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    fun open(year: Int) {
        if (state.value.year == year && loadJob?.isActive == true) return
        loadJob?.cancel()
        state.value = RecapReelUiState(year = year)
        loadJob =
            viewModelScope.launch {
                observeRecap(year)
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        reportException(failure)
                        state.update { it.copy(loading = false, failed = true) }
                    }.collect { result ->
                        val data = result.data
                        val empty = data.milliseconds == 0L
                        state.update { current ->
                            current.copy(
                                loading = false,
                                failed = false,
                                empty = empty,
                                data = if (empty) null else data,
                                chapter = current.chapter.takeIf { it in data.chapters } ?: RecapChapter.INTRO,
                            )
                        }
                    }
            }
    }

    fun selectChapter(chapter: RecapChapter) {
        val chapters = state.value.data?.chapters ?: return
        if (chapter !in chapters) return
        state.update { it.copy(chapter = chapter) }
    }

    fun replay() = selectChapter(RecapChapter.INTRO)
}
