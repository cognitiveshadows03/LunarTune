/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.constants

import dev.citali.lunartune.ui.screens.OptionStats
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class StatPeriod {
    WEEK_1,
    MONTH_1,
    MONTH_3,
    MONTH_6,
    YEAR_1,
    ALL,
    ;

    fun toTimeMillis(now: LocalDateTime = LocalDateTime.now()): Long =
        when (this) {
            WEEK_1 -> {
                now
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH_1 -> {
                now
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH_3 -> {
                now
                    .minusMonths(3)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH_6 -> {
                now
                    .minusMonths(6)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            YEAR_1 -> {
                now
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            ALL -> {
                0
            }
        }
}

fun statToPeriod(
    selection: OptionStats,
    test: Int,
    now: LocalDateTime = LocalDateTime.now(),
): Long =
    when (selection) {
        OptionStats.WEEKS -> {
            now
                .minusWeeks(test.toLong())
                .minusDays(1)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }

        OptionStats.MONTHS -> {
            now
                .withDayOfMonth(1)
                .minusMonths(test.toLong())
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }

        OptionStats.YEARS -> {
            now
                .withDayOfMonth(1)
                .withMonth(1)
                .minusYears(test.toLong())
                .toInstant(
                    ZoneOffset.UTC,
                ).toEpochMilli()
        }

        OptionStats.CONTINUOUS -> {
            val index = test.coerceIn(0, StatPeriod.entries.lastIndex)
            StatPeriod.entries[index].toTimeMillis(now)
        }
    }
