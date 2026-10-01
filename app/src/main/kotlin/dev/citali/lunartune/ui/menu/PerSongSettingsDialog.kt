/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.menu

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.NeverRecommendSongIdsKey
import dev.citali.lunartune.constants.PerSongAlbumArtOverridesKey
import dev.citali.lunartune.constants.PerSongTagsKey
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.constants.PlayerDesignStyleKey
import dev.citali.lunartune.constants.PlayerDesignStyleOverridesKey
import dev.citali.lunartune.ui.component.ListDialog
import dev.citali.lunartune.utils.copySongArtworkFromGallery
import dev.citali.lunartune.utils.formatSongTags
import dev.citali.lunartune.utils.parseIdSet
import dev.citali.lunartune.utils.parsePlayerDesignStyleOverrides
import dev.citali.lunartune.utils.parseSongTags
import dev.citali.lunartune.utils.parseTabMap
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.utils.setPlayerDesignStyleOverride
import dev.citali.lunartune.utils.setTabMapValue
import dev.citali.lunartune.utils.toggleIdSet

/**
 * Per-song overrides, laid out like the lyrics search sheet: a coloured header
 * that names the song, then one card per option whose trailing control shows
 * the current state at a glance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PerSongSettingsDialog(
    songId: String,
    onDismiss: () -> Unit,
    songTitle: String? = null,
    songArtist: String? = null,
    thumbnailUrl: String? = null,
) {
    val context = LocalContext.current
    val settingsPlayerDesignStyle by rememberEnumPreference(PlayerDesignStyleKey, defaultValue = PlayerDesignStyle.V4)
    val (playerStyleOverrides, onPlayerStyleOverridesChange) =
        rememberPreference(PlayerDesignStyleOverridesKey, "")
    val (albumArtOverrides, onAlbumArtOverridesChange) =
        rememberPreference(PerSongAlbumArtOverridesKey, "")
    val (tagsRaw, onTagsRawChange) = rememberPreference(PerSongTagsKey, "")
    val (neverRecommendRaw, onNeverRecommendChange) =
        rememberPreference(NeverRecommendSongIdsKey, "")

    val songStyle =
        remember(playerStyleOverrides, songId) {
            parsePlayerDesignStyleOverrides(playerStyleOverrides)[songId]
        }
    val customArtPath =
        remember(albumArtOverrides, songId) {
            parseTabMap(albumArtOverrides)[songId]?.takeIf { it.isNotBlank() }
        }
    val hasCustomArt = customArtPath != null
    val currentTags =
        remember(tagsRaw, songId) {
            parseSongTags(parseTabMap(tagsRaw)[songId].orEmpty())
        }
    val neverRecommend =
        remember(neverRecommendRaw, songId) {
            songId in parseIdSet(neverRecommendRaw)
        }
    val overrideCount =
        listOf(songStyle != null, hasCustomArt, neverRecommend, currentTags.isNotEmpty()).count { it }

    var showPlayerStyleDialog by rememberSaveable { mutableStateOf(false) }
    var showTagsDialog by rememberSaveable { mutableStateOf(false) }
    var tagsDraft by rememberSaveable { mutableStateOf("") }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val stored = copySongArtworkFromGallery(context, songId, uri)
            if (stored == null) {
                Toast.makeText(context, R.string.per_song_album_art_failed, Toast.LENGTH_SHORT).show()
            } else {
                onAlbumArtOverridesChange(setTabMapValue(albumArtOverrides, songId, stored))
                Toast.makeText(context, R.string.per_song_album_art_saved, Toast.LENGTH_SHORT).show()
            }
        }

    if (showPlayerStyleDialog) {
        ListDialog(onDismiss = { showPlayerStyleDialog = false }) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.player_style_use_default)) },
                    supportingContent = {
                        Text(
                            text = playerDesignStyleLabel(settingsPlayerDesignStyle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (songStyle == null) R.drawable.done else R.drawable.settings),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlayerStyleOverridesChange(
                                    setPlayerDesignStyleOverride(playerStyleOverrides, songId, null),
                                )
                                showPlayerStyleDialog = false
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            items(PlayerDesignStyle.entries) { style ->
                val selected = songStyle == style
                ListItem(
                    headlineContent = { Text(text = playerDesignStyleLabel(style)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (selected) R.drawable.done else R.drawable.style),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlayerStyleOverridesChange(
                                    setPlayerDesignStyleOverride(playerStyleOverrides, songId, style),
                                )
                                showPlayerStyleDialog = false
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    if (showTagsDialog) {
        AlertDialog(
            onDismissRequest = { showTagsDialog = false },
            title = { Text(stringResource(R.string.per_song_custom_tags)) },
            text = {
                OutlinedTextField(
                    value = tagsDraft,
                    onValueChange = { tagsDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.per_song_custom_tags_hint)) },
                    singleLine = false,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formatted = formatSongTags(parseSongTags(tagsDraft))
                        onTagsRawChange(setTabMapValue(tagsRaw, songId, formatted.ifBlank { null }))
                        showTagsDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTagsDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PerSongHeader(
                        title = songTitle,
                        artist = songArtist,
                        thumbnailUrl = customArtPath ?: thumbnailUrl,
                        overrideCount = overrideCount,
                        onDismiss = onDismiss,
                    )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PerSongOptionCard(
                            icon = R.drawable.style,
                            title = stringResource(R.string.player_style_for_song),
                            state =
                                songStyle?.let { playerDesignStyleLabel(it) }
                                    ?: stringResource(R.string.player_style_use_default),
                            isSet = songStyle != null,
                            onClick = { showPlayerStyleDialog = true },
                            trailing = {
                                PerSongValueChip(
                                    text =
                                        songStyle?.let { playerDesignStyleLabel(it) }
                                            ?: playerDesignStyleLabel(settingsPlayerDesignStyle),
                                    highlighted = songStyle != null,
                                )
                            },
                        )

                        PerSongOptionCard(
                            icon = R.drawable.image,
                            title = stringResource(R.string.per_song_album_art),
                            state =
                                stringResource(
                                    if (hasCustomArt) R.string.per_song_album_art_custom else R.string.per_song_album_art_default,
                                ),
                            isSet = hasCustomArt,
                            onClick = { galleryLauncher.launch("image/*") },
                            trailing = {
                                if (hasCustomArt) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        AsyncImage(
                                            model = customArtPath,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier =
                                                Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                        )
                                        IconButton(
                                            onClick = {
                                                onAlbumArtOverridesChange(setTabMapValue(albumArtOverrides, songId, null))
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.close),
                                                contentDescription = stringResource(R.string.reset),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_forward),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            },
                        )

                        PerSongOptionCard(
                            icon = R.drawable.block,
                            title = stringResource(R.string.per_song_never_recommend),
                            state =
                                stringResource(
                                    if (neverRecommend) R.string.per_song_never_recommend_on else R.string.per_song_never_recommend_off,
                                ),
                            isSet = neverRecommend,
                            onClick = {
                                onNeverRecommendChange(toggleIdSet(neverRecommendRaw, songId, !neverRecommend))
                            },
                            trailing = {
                                Switch(
                                    checked = neverRecommend,
                                    onCheckedChange = {
                                        onNeverRecommendChange(toggleIdSet(neverRecommendRaw, songId, it))
                                    },
                                )
                            },
                        )

                        PerSongOptionCard(
                            icon = R.drawable.edit,
                            title = stringResource(R.string.per_song_custom_tags),
                            state =
                                if (currentTags.isEmpty()) {
                                    stringResource(R.string.per_song_custom_tags_none)
                                } else {
                                    stringResource(R.string.per_song_tags_count, currentTags.size)
                                },
                            isSet = currentTags.isNotEmpty(),
                            onClick = {
                                tagsDraft = formatSongTags(currentTags)
                                showTagsDialog = true
                            },
                            trailing = {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_forward),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            extra =
                                if (currentTags.isEmpty()) {
                                    null
                                } else {
                                    {
                                        PerSongTagRow(tags = currentTags, highlighted = true)
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerSongHeader(
    title: String?,
    artist: String?,
    thumbnailUrl: String?,
    overrideCount: Int,
    onDismiss: () -> Unit,
) {
    val subtitle =
        when {
            !title.isNullOrBlank() && !artist.isNullOrBlank() -> "$title • $artist"
            !title.isNullOrBlank() -> title
            overrideCount == 0 -> stringResource(R.string.per_song_no_overrides)
            else -> stringResource(R.string.per_song_overrides_count, overrideCount)
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                if (thumbnailUrl.isNullOrBlank()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.tune),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                } else {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.per_song_settings),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (overrideCount > 0 && !title.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = overrideCount.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * One option. A set override is drawn in the secondary container with a primary
 * outline, the way an expanded lyrics result is, so the eye lands on what has
 * actually been changed for this song.
 */
@Composable
private fun PerSongOptionCard(
    icon: Int,
    title: String,
    state: String,
    isSet: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val containerColor =
        if (isSet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor =
        if (isSet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val outlineColor =
        if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, outlineColor),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color =
                        if (isSet) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint =
                                if (isSet) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (isSet) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailing()
            }
            extra?.invoke()
        }
    }
}

@Composable
private fun PerSongValueChip(
    text: String,
    highlighted: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color =
            if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (highlighted) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .widthIn(max = 120.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PerSongTagRow(
    tags: List<String>,
    highlighted: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The row is not scrollable on purpose: the card is a summary, the
        // editor is one tap away. Show what fits and count the rest.
        val shown = tags.take(4)
        shown.forEach { tag ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color =
                    if (highlighted) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .widthIn(max = 110.dp)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (tags.size > shown.size) {
            Text(
                text = "+${tags.size - shown.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 5.dp),
            )
        }
    }
}

@Composable
fun playerDesignStyleLabel(style: PlayerDesignStyle): String =
    when (style) {
        PlayerDesignStyle.V1 -> stringResource(R.string.player_design_v1)
        PlayerDesignStyle.V2 -> stringResource(R.string.player_design_v2)
        PlayerDesignStyle.V3 -> stringResource(R.string.player_design_v3)
        PlayerDesignStyle.V4 -> stringResource(R.string.player_design_v4)
        PlayerDesignStyle.V5 -> stringResource(R.string.player_design_v5)
        PlayerDesignStyle.V6 -> stringResource(R.string.player_design_v6)
        PlayerDesignStyle.V7 -> stringResource(R.string.player_design_v7)
        PlayerDesignStyle.V7_LEGACY -> stringResource(R.string.player_design_v7_legacy)
        PlayerDesignStyle.V8 -> stringResource(R.string.player_design_v8)
        PlayerDesignStyle.V9 -> stringResource(R.string.player_design_v9)
        PlayerDesignStyle.V10 -> stringResource(R.string.player_design_v10)
    }
