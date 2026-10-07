/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.backup.ScheduledBackupFrequency
import dev.citali.lunartune.constants.ImportSourcePriorityKey
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.ui.component.DefaultDialog
import dev.citali.lunartune.ui.component.EnumListPreference
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.ListPreference
import dev.citali.lunartune.ui.component.PreferenceEntry
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.menu.AddToPlaylistDialogOnline
import dev.citali.lunartune.ui.menu.LoadingScreen
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.BackupCategory
import dev.citali.lunartune.viewmodels.BackupRestoreViewModel
import dev.citali.lunartune.viewmodels.BackupSource
import dev.citali.lunartune.viewmodels.ScheduledBackupScreenState
import dev.citali.lunartune.viewmodels.ScheduledBackupUiData
import dev.citali.lunartune.viewmodels.GoogleDriveSyncScreenState
import dev.citali.lunartune.viewmodels.GoogleDriveSyncUiData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CSV_MIME_TYPES =
    arrayOf(
        "text/csv",
        "text/x-csv",
        "text/comma-separated-values",
        "text/x-comma-separated-values",
        "application/csv",
        "application/x-csv",
        "application/vnd.ms-excel",
        "text/plain",
        "text/*",
        "application/octet-stream",
    )

@Composable
fun BackupAndRestore(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by rememberSaveable { mutableStateOf(false) }
    var isProgressStarted by rememberSaveable { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var progressPercentage by rememberSaveable { mutableIntStateOf(0) }
    var showBackupOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreValidationError by rememberSaveable { mutableStateOf(false) }
    var restoreValidationErrorMessage by remember { mutableStateOf("") }
    var pendingBackupCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsStateWithLifecycle()
    val scheduledBackupState by viewModel.scheduledBackupState.collectAsStateWithLifecycle()
    val googleDriveSyncState by viewModel.googleDriveSyncState.collectAsStateWithLifecycle()
    val (importLocalFirst, onImportLocalFirstChange) = rememberPreference(ImportSourcePriorityKey, false)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.googleDriveSyncEvent.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scheduledBackupEvent.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri, pendingBackupCategories)
            }
        }
    val backupDirectoryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(viewModel::onScheduledBackupDirectorySelected)
        }
    val googleDriveFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.onGoogleDriveSyncRemoteFolderSelected(
                uri.toString(),
                resolveFolderDisplayName(context, uri).ifBlank {
                    context.getString(R.string.google_drive_sync_remote_folder_default_name)
                },
            )
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val result = viewModel.validateBackup(context, uri)
                    if (result.isValid) {
                        if (result.source == BackupSource.ARCHIVETUNE) {
                            Toast
                                .makeText(context, R.string.restore_archivetune_backup_detected, Toast.LENGTH_LONG)
                                .show()
                        }
                        pendingRestoreCategories = result.availableCategories
                        pendingRestoreUri = uri
                        showRestoreOptionsDialog = true
                    } else {
                        restoreValidationErrorMessage = result.errorMessage ?: context.getString(R.string.restore_corrupted)
                        showRestoreValidationError = true
                    }
                }
            }
        }
    val importPlaylistFromCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val result = viewModel.importPlaylistFromCsv(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)
                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            }
        }
    val importM3uLauncherOnline =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val result = viewModel.loadM3UOnline(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)
                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
                        .padding(16.dp),
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            val scheduledBackupData =
                when (val state = scheduledBackupState) {
                    is ScheduledBackupScreenState.Success -> {
                        state.data
                    }

                    ScheduledBackupScreenState.Loading,
                    ScheduledBackupScreenState.Empty,
                    is ScheduledBackupScreenState.Error,
                    -> {
                        ScheduledBackupUiData(
                            enabled = false,
                            frequency = ScheduledBackupFrequency.WEEKLY,
                            customDateEpochDay = null,
                            customDateLabel = null,
                            directoryName = null,
                            overwriteExisting = false,
                            showCustomDatePicker = false,
                        )
                    }
                }

            ScheduledBackupSection(
                data = scheduledBackupData,
                enabled = scheduledBackupState !is ScheduledBackupScreenState.Loading,
                onEnabledChanged = viewModel::onScheduledBackupEnabledChanged,
                onFrequencySelected = viewModel::onScheduledBackupFrequencySelected,
                onDirectoryClick = { backupDirectoryLauncher.launch(null) },
                onOverwriteChanged = viewModel::onScheduledBackupOverwriteChanged,
            )

            val googleDriveData = (googleDriveSyncState as? GoogleDriveSyncScreenState.Success)?.data
                ?: GoogleDriveSyncUiData(
                    enabled = false,
                    frequency = ScheduledBackupFrequency.WEEKLY,
                    customDateEpochDay = null,
                    customDateLabel = null,
                    remoteFolderName = null,
                    remoteFolderUri = null,
                    overwriteExisting = true,
                    showCustomDatePicker = false,
                    lastSyncLabel = null,
                    lastSyncFailed = false,
                    isSyncing = false,
                )
            GoogleDriveSyncSection(
                data = googleDriveData,
                enabled = googleDriveSyncState !is GoogleDriveSyncScreenState.Loading,
                onEnabledChanged = viewModel::onGoogleDriveSyncEnabledChanged,
                onFrequencySelected = viewModel::onGoogleDriveSyncFrequencySelected,
                onFolderClick = { googleDriveFolderLauncher.launch(null) },
                onClearFolderClick = viewModel::onGoogleDriveSyncRemoteFolderCleared,
                onOverwriteChanged = viewModel::onGoogleDriveSyncOverwriteChanged,
                onSyncNowClick = viewModel::onGoogleDriveSyncRunNow,
            )
            if (googleDriveData.showCustomDatePicker) {
                ScheduledBackupDatePickerDialog(
                    selectedEpochDay = googleDriveData.customDateEpochDay,
                    onDateSelected = viewModel::onGoogleDriveSyncCustomDateSelected,
                    onDismiss = viewModel::onGoogleDriveSyncCustomDateDismissed,
                )
            }

            PreferenceGroup(title = stringResource(R.string.internal_service)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.action_backup)) },
                        description = stringResource(R.string.backup_create_backup_desc),
                        icon = { Icon(painterResource(R.drawable.backup), null) },
                        onClick = { showBackupOptionsDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.action_restore)) },
                        description = stringResource(R.string.restore_select_backup),
                        icon = { Icon(painterResource(R.drawable.restore), null) },
                        onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip")) },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.import_priority_setting_title)) },
                        description = stringResource(R.string.import_priority_setting_desc),
                        icon = { Icon(painterResource(R.drawable.playlist_import), null) },
                        selectedValue = importLocalFirst,
                        values = listOf(true, false),
                        valueText = { localFirst ->
                            stringResource(
                                if (localFirst) {
                                    R.string.import_priority_local_first
                                } else {
                                    R.string.import_priority_youtube_only
                                },
                            )
                        },
                        valueDescription = { localFirst ->
                            stringResource(
                                if (localFirst) {
                                    R.string.import_priority_local_first_desc
                                } else {
                                    R.string.import_priority_youtube_only_desc
                                },
                            )
                        },
                        onValueSelected = onImportLocalFirstChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.music_transfer_title)) },
                        description = stringResource(R.string.music_transfer_entry_description),
                        icon = { Icon(painterResource(R.drawable.playlist_import), null) },
                        onClick = { navController.navigate("settings/music_transfer") },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.import_online)) },
                        description = stringResource(R.string.import_m3u_format),
                        icon = { Icon(painterResource(R.drawable.playlist_import), null) },
                        onClick = { importM3uLauncherOnline.launch(arrayOf("audio/*")) },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.import_csv)) },
                        description = stringResource(R.string.import_csv_format),
                        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                        onClick = { importPlaylistFromCsv.launch(CSV_MIME_TYPES) },
                    )
                }
            }
        }
    }

    val scheduledBackupData = (scheduledBackupState as? ScheduledBackupScreenState.Success)?.data
    if (scheduledBackupData?.showCustomDatePicker == true) {
        ScheduledBackupDatePickerDialog(
            selectedEpochDay = scheduledBackupData.customDateEpochDay,
            onDateSelected = viewModel::onScheduledBackupCustomDateSelected,
            onDismiss = viewModel::onScheduledBackupCustomDateDismissed,
        )
    }

    if (showBackupOptionsDialog) {
        BackupOptionsDialog(
            title = stringResource(R.string.backup_options_title),
            confirmLabel = stringResource(R.string.action_backup),
            onConfirm = { categories ->
                pendingBackupCategories = categories
                showBackupOptionsDialog = false
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                backupLauncher.launch(
                    "${context.getString(R.string.app_name)}_${LocalDateTime.now().format(formatter)}.backup",
                )
            },
            onDismiss = { showBackupOptionsDialog = false },
        )
    }

    if (showRestoreOptionsDialog) {
        val uri = pendingRestoreUri
        if (uri != null) {
            BackupOptionsDialog(
                title = stringResource(R.string.restore_options_title),
                confirmLabel = stringResource(R.string.action_restore),
                onConfirm = { categories ->
                    pendingRestoreCategories = categories
                    showRestoreOptionsDialog = false
                    pendingRestoreUri = null
                    viewModel.restore(context, uri, categories)
                },
                onDismiss = {
                    showRestoreOptionsDialog = false
                    pendingRestoreUri = null
                },
            )
        }
    }

    if (showRestoreValidationError) {
        DefaultDialog(
            onDismiss = { showRestoreValidationError = false },
            title = { Text(stringResource(R.string.restore_failed)) },
            buttons = {
                TextButton(
                    onClick = { showRestoreValidationError = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(
                text = restoreValidationErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { isProgressStarted = it },
        onPercentageChange = { progressPercentage = it },
        onStatusChange = { progressStatus = it },
    )

    LaunchedEffect(progressPercentage, isProgressStarted) {
        if (isProgressStarted && progressPercentage == 99) {
            delay(10_000)
            if (progressPercentage == 99) {
                isProgressStarted = false
                progressPercentage = 0
            }
        }
    }

    LoadingScreen(
        isVisible = backupRestoreProgress != null || isProgressStarted,
        value = backupRestoreProgress?.percent ?: progressPercentage,
        title = backupRestoreProgress?.title,
        stepText = backupRestoreProgress?.step ?: progressStatus,
        indeterminate = backupRestoreProgress?.indeterminate ?: false,
    )
}

@Composable
private fun ScheduledBackupSection(
    data: ScheduledBackupUiData,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onFrequencySelected: (ScheduledBackupFrequency) -> Unit,
    onDirectoryClick: () -> Unit,
    onOverwriteChanged: (Boolean) -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.scheduled_backup)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.scheduled_backup_enabled)) },
                description =
                    stringResource(
                        if (data.enabled) {
                            R.string.scheduled_backup_enabled_description
                        } else {
                            R.string.scheduled_backup_disabled_description
                        },
                    ),
                icon = { Icon(painterResource(R.drawable.repeat_on), contentDescription = null) },
                checked = data.enabled,
                onCheckedChange = onEnabledChanged,
                isEnabled = enabled,
            )
        }

        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.scheduled_backup_frequency)) },
                description =
                    if (data.frequency == ScheduledBackupFrequency.CUSTOM && data.customDateLabel != null) {
                        stringResource(R.string.scheduled_backup_custom_date, data.customDateLabel)
                    } else {
                        stringResource(R.string.scheduled_backup_frequency_description)
                    },
                icon = { Icon(painterResource(R.drawable.calendar_today), contentDescription = null) },
                selectedValue = data.frequency,
                valueText = { frequency -> stringResource(frequency.labelRes) },
                onValueSelected = onFrequencySelected,
                isEnabled = enabled,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.scheduled_backup_directory)) },
                description =
                    data.directoryName
                        ?: stringResource(R.string.scheduled_backup_directory_description),
                icon = { Icon(painterResource(R.drawable.snippet_folder), contentDescription = null) },
                onClick = onDirectoryClick,
                isEnabled = enabled,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.scheduled_backup_overwrite)) },
                description = stringResource(R.string.scheduled_backup_overwrite_description),
                icon = { Icon(painterResource(R.drawable.backup), contentDescription = null) },
                checked = data.overwriteExisting,
                onCheckedChange = onOverwriteChanged,
                isEnabled = enabled && data.directoryName != null,
            )
        }
    }
}

@Composable
private fun GoogleDriveSyncSection(
    data: GoogleDriveSyncUiData,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onFrequencySelected: (ScheduledBackupFrequency) -> Unit,
    onFolderClick: () -> Unit,
    onClearFolderClick: () -> Unit,
    onOverwriteChanged: (Boolean) -> Unit,
    onSyncNowClick: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.google_drive_sync)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.google_drive_sync_enabled)) },
                description = stringResource(if (data.enabled) R.string.google_drive_sync_enabled_description_on else R.string.google_drive_sync_enabled_description_off),
                icon = { Icon(painterResource(R.drawable.backup), null) },
                checked = data.enabled,
                onCheckedChange = onEnabledChanged,
                isEnabled = enabled && data.remoteFolderUri != null,
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.google_drive_sync_remote_folder)) },
                description = data.remoteFolderName ?: stringResource(R.string.google_drive_sync_remote_folder_not_set),
                icon = { Icon(painterResource(R.drawable.snippet_folder), null) },
                onClick = onFolderClick,
                isEnabled = enabled,
            )
        }
        if (data.remoteFolderUri != null) item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.google_drive_sync_clear_folder)) },
                description = stringResource(R.string.google_drive_sync_clear_folder_description),
                icon = { Icon(painterResource(R.drawable.delete), null) },
                onClick = onClearFolderClick,
                isEnabled = enabled && !data.isSyncing,
            )
        }
        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.scheduled_backup_frequency)) },
                description = data.customDateLabel ?: stringResource(R.string.scheduled_backup_frequency_description),
                icon = { Icon(painterResource(R.drawable.calendar_today), null) },
                selectedValue = data.frequency,
                valueText = { stringResource(it.labelRes) },
                onValueSelected = onFrequencySelected,
                isEnabled = enabled && data.enabled,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.google_drive_sync_overwrite)) },
                description = stringResource(R.string.google_drive_sync_overwrite_description),
                icon = { Icon(painterResource(R.drawable.backup), null) },
                checked = data.overwriteExisting,
                onCheckedChange = onOverwriteChanged,
                isEnabled = enabled && data.remoteFolderUri != null,
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(if (data.isSyncing) R.string.google_drive_sync_running else R.string.google_drive_sync_run_now)) },
                description = when {
                    data.lastSyncFailed -> stringResource(R.string.google_drive_sync_last_sync_failed)
                    data.lastSyncLabel != null -> stringResource(R.string.google_drive_sync_last_synced, data.lastSyncLabel)
                    else -> stringResource(R.string.google_drive_sync_never_synced)
                },
                icon = { Icon(painterResource(R.drawable.sync), null) },
                onClick = onSyncNowClick,
                isEnabled = enabled && data.remoteFolderUri != null && !data.isSyncing,
            )
        }
        item { Text(stringResource(R.string.google_drive_sync_cloud_app_disclaimer), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
    }
}

private fun resolveFolderDisplayName(context: android.content.Context, treeUri: Uri): String = try {
    val id = DocumentsContract.getTreeDocumentId(treeUri)
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
    context.contentResolver.query(documentUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }.orEmpty()
} catch (_: Exception) { "" }

@Composable
private fun ScheduledBackupDatePickerDialog(
    selectedEpochDay: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val initialEpochDay = selectedEpochDay?.coerceAtLeast(todayEpochDay) ?: todayEpochDay + 1
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                LocalDate
                    .ofEpochDay(initialEpochDay)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
            selectableDates =
                remember(todayEpochDay) {
                    object : androidx.compose.material3.SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            Instant
                                .ofEpochMilli(utcTimeMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay() >= todayEpochDay
                    }
                },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val epochDay =
                        Instant
                            .ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay()
                    onDateSelected(epochDay)
                },
                enabled = datePickerState.selectedDateMillis != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            title = { Text(stringResource(R.string.scheduled_backup_custom_title)) },
            showModeToggle = false,
        )
    }
}

private val ScheduledBackupFrequency.labelRes: Int
    get() =
        when (this) {
            ScheduledBackupFrequency.DAILY -> R.string.scheduled_backup_daily
            ScheduledBackupFrequency.WEEKLY -> R.string.scheduled_backup_weekly
            ScheduledBackupFrequency.MONTHLY -> R.string.scheduled_backup_monthly
            ScheduledBackupFrequency.CUSTOM -> R.string.scheduled_backup_custom
        }

@Composable
private fun IconBubble(
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.large)
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun BackupOptionsDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (Set<BackupCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(title) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = { onConfirm(selected) },
                shapes = ButtonDefaults.shapes(),
                enabled = selected.isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        BackupCategory.entries.forEach { category ->
            val isChecked = category in selected
            val labelRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library
                    BackupCategory.ACCOUNT -> R.string.backup_category_account
                    BackupCategory.SETTINGS -> R.string.backup_category_settings
                    BackupCategory.DOWNLOADS -> R.string.backup_category_downloads
                }
            val descRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                    BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                    BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                    BackupCategory.DOWNLOADS -> R.string.backup_category_downloads_desc
                }
            val iconRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.drawable.library_music
                    BackupCategory.ACCOUNT -> R.drawable.account
                    BackupCategory.SETTINGS -> R.drawable.settings
                    BackupCategory.DOWNLOADS -> R.drawable.download
                }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                onClick = {
                    selected = if (isChecked) selected - category else selected + category
                },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBubble(
                        icon = painterResource(iconRes),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 40.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + category else selected - category
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
