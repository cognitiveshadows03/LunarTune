/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val playback = remember { DesktopPlayback() }
    Window(
        onCloseRequest = { playback.release(); exitApplication() },
        title = "LunarTune",
        state = rememberWindowState(),
    ) {
        MaterialTheme {
            DesktopShell()
        }
    }
}

@Composable
private fun DesktopShell() {
    var selected by remember { mutableIntStateOf(0) }
    val destinations = listOf("Home", "Search", "Library", "Settings")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.LibraryMusic, Icons.Rounded.Settings)
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight().padding(12.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                destinations.forEachIndexed { index, label ->
                    NavigationRailItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label) },
                        alwaysShowLabel = false,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(destinations[selected], style = MaterialTheme.typography.displaySmall)
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("LunarTune desktop foundation")
                }
            }
        }
    }
}
