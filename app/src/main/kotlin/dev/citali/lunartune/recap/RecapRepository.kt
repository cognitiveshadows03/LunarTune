/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.recap

import dev.citali.lunartune.db.MusicDatabase
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecapRepository @Inject constructor(private val database: MusicDatabase) {
    internal fun changes(): Flow<Unit> = database.recapChanges()

    internal suspend fun load(start: LocalDateTime, end: LocalDateTime): RecapSnapshot =
        withContext(Dispatchers.IO) {
            val from = start.toInstant(ZoneOffset.UTC).toEpochMilli()
            val until = end.toInstant(ZoneOffset.UTC).toEpochMilli() + if (end.nano % 1_000_000 == 0) 0L else 1L
            database.withTransaction {
                val dao = recapDao
                RecapSnapshot(
                    firstTimestamp = dao.firstTimestamp(),
                    totals = dao.totals(from, until),
                    songs = dao.songs(from, until),
                    artists = dao.artists(from, until),
                    albums = dao.albums(from, until),
                    dayHours = dao.dayHours(from, until),
                    monthlyArtists = dao.monthlyArtists(from, until),
                )
            }
        }
}
