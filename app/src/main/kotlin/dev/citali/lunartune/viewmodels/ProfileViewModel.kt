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
import java.time.LocalDate
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
            database
                .listeningTotals(
                    fromTimestamp = 0L,
                    toTimestamp = System.currentTimeMillis(),
                ).map { it.totalTimeListened }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

        /**
         * Date of the very first play stored in the database, or `null` while the library has
         * never recorded a listen. The profile card shows it as the date the account started
         * listening on this device.
         */
        val firstListenDate: StateFlow<LocalDate?> =
            database
                .firstEvent()
                .map { it?.event?.timestamp?.toLocalDate() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
