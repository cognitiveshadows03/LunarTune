/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.citali.lunartune.storage

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import dev.citali.lunartune.constants.MaxBannerStorageSizeKey
import dev.citali.lunartune.constants.SmartTrimmerKey
import dev.citali.lunartune.ui.screens.settings.AccountBanner
import dev.citali.lunartune.utils.dataStore
import java.io.File

object BannerStorageManager {
    private val sharedKey = stringPreferencesKey("sharedScreenHeaderImageUri")
    private val homeKey = stringPreferencesKey("homeHeaderImageUri")
    private val settingsKey = stringPreferencesKey("settingsHeaderImageUri")
    private val accountSourceKey = stringPreferencesKey("accountBannerSource")

    fun managedFiles(context: Context): List<File> = buildList {
        File(context.filesDir, "cropped_banners").listFiles()?.let(::addAll)
        AccountBanner.customFile(context).takeIf(File::exists)?.let(::add)
    }

    fun sizeBytes(context: Context): Long = managedFiles(context).sumOf(File::length)

    private suspend fun protectedFiles(context: Context, extraProtected: File? = null): Set<String> {
        val preferences = context.dataStore.data.first()
        return buildSet {
            listOf(preferences[sharedKey], preferences[homeKey], preferences[settingsKey])
                .mapNotNull { value -> value?.takeIf(String::isNotBlank) }
                .mapNotNull { runCatching { Uri.parse(it).path }.getOrNull() }
                .forEach { add(File(it).canonicalPath) }
            if (preferences[accountSourceKey] == AccountBanner.SOURCE_CUSTOM) {
                add(AccountBanner.customFile(context).canonicalPath)
            }
            extraProtected?.let { add(it.canonicalPath) }
        }
    }

    suspend fun trimIfEnabled(context: Context, extraProtected: File? = null) {
        val preferences = context.dataStore.data.first()
        if (preferences[SmartTrimmerKey] != true) return
        val limitMb = preferences[MaxBannerStorageSizeKey] ?: 100
        if (limitMb <= 0) return
        val limitBytes = limitMb.toLong() * 1024L * 1024L
        val protected = protectedFiles(context, extraProtected)
        var total = sizeBytes(context)
        managedFiles(context)
            .filterNot { it.canonicalPath in protected }
            .sortedBy(File::lastModified)
            .forEach { file ->
                if (total <= limitBytes) return
                val length = file.length()
                if (file.delete()) total -= length
            }
    }

    suspend fun clearUnused(context: Context) {
        val protected = protectedFiles(context)
        managedFiles(context).filterNot { it.canonicalPath in protected }.forEach { it.delete() }
    }

    suspend fun clearAll(context: Context) {
        managedFiles(context).forEach { it.delete() }
        context.dataStore.edit {
            it.remove(sharedKey)
            it.remove(homeKey)
            it.remove(settingsKey)
            it[accountSourceKey] = AccountBanner.SOURCE_NONE
        }
    }
}
