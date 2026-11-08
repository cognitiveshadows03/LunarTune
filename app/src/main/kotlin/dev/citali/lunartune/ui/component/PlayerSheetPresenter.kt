/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * A one-way handle to the player sheet that lives in the activity scaffold.
 *
 * The sheet state is created deep inside the main screen, while the screens that
 * want to hand the user over to the player sit inside the nav graph and cannot
 * reach it. Passing the whole sheet state down would drag the scaffold's layout
 * geometry into every settings screen, so only the one thing those screens need
 * is exposed here: expand the player.
 *
 * [bind] is called once by the owner of the sheet and cleared on dispose, so a
 * request made while the sheet is gone is dropped rather than kept around.
 */
@Stable
class PlayerSheetPresenter {
    private var expandAction: (() -> Unit)? = null

    val isBound: Boolean
        get() = expandAction != null

    fun bind(action: () -> Unit) {
        expandAction = action
    }

    fun unbind() {
        expandAction = null
    }

    /** Expands the player sheet, if the scaffold is currently hosting it. */
    fun expandPlayer() {
        expandAction?.invoke()
    }
}

@Composable
fun rememberPlayerSheetPresenter(): PlayerSheetPresenter = remember { PlayerSheetPresenter() }
