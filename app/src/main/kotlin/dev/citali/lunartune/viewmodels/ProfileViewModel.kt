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
import dev.citali.lunartune.db.MusicDatabase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        database: MusicDatabase,
    ) : ViewModel() {
        /** All-time total listening time in milliseconds. */
        val totalListeningTimeMs: StateFlow<Long> =
            database.dao
                .listeningTotals(
                    fromTimestamp = 0L,
                    toTimestamp = System.currentTimeMillis(),
                ).map { it.totalTimeListened }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    }
