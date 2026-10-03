/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.appicon

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Restores a usable launcher entry after an update removes the currently selected icon alias.
 * PackageManager preserves component overrides across updates, while icon aliases are generated
 * from IconPack IDs; if an ID changes, the user's enabled alias can disappear from the package.
 */
class LauncherAliasRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val packageManager = context.packageManager
        val launcherIntent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
        if (packageManager.queryIntentActivities(launcherIntent, 0).isNotEmpty()) return

        val defaultAlias =
            ComponentName(
                context.packageName,
                "${context.packageName}.launcher.DefaultIconAlias",
            )
        packageManager.setComponentEnabledSetting(
            defaultAlias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
