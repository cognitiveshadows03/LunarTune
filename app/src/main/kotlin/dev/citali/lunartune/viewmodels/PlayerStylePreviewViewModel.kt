/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.Song
import javax.inject.Inject

/**
 * Supplies the newest entry in play history as the subject every style preview
 * renders. One row, read once: the carousel is a frozen frame, so this never
 * observes playback and never asks the network for anything.
 */
@HiltViewModel
class PlayerStylePreviewViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val _lastPlayedSong = MutableStateFlow<Song?>(null)
        val lastPlayedSong: StateFlow<Song?> = _lastPlayedSong.asStateFlow()

        init {
            viewModelScope.launch {
                val song =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            database.events(limit = 1, offset = 0).firstOrNull()?.song
                        }.getOrNull()
                    }
                _lastPlayedSong.value = song
            }
        }
    }
