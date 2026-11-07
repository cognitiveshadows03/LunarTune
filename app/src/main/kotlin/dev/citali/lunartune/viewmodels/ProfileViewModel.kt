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
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Last values the profile card painted, kept for the life of the process.
 *
 * The screen gets a fresh view model every time it is opened, and a fresh flow starts from its
 * initial value, so the card used to flash "0h 0m" and an empty date line before the database
 * answered. Seeding the flows from here means a reopened card paints the numbers it already had
 * and only touches them when they actually change.
 */
private object ProfileSnapshotCache {
    @Volatile
    var totalListeningTimeMs: Long = 0L

    /** `null` until a listen has ever been recorded. */
    @Volatile
    var firstListenDate: LocalDate? = null
}

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
                .onEach { ProfileSnapshotCache.totalListeningTimeMs = it }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    ProfileSnapshotCache.totalListeningTimeMs,
                )

        /**
         * Date of the very first play stored in the database, or `null` while the library has
         * never recorded a listen. The profile card shows it as the date the account started
         * listening on this device.
         */
        val firstListenDate: StateFlow<LocalDate?> =
            database
                .firstEvent()
                .map { it?.event?.timestamp?.toLocalDate() }
                .onEach { ProfileSnapshotCache.firstListenDate = it }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    ProfileSnapshotCache.firstListenDate,
                )
    }
