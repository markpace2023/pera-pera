package com.example.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.util.DatabaseBackupUtils
import com.example.util.DatabaseRestoreResult
import com.example.util.FullDatabaseBackup
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PendingStorageAction {
    BACKUP,
    RESTORE,
    SHARE
}

private fun isStoragePermissionGranted(context: Context): Boolean {
    val prefs = context.getSharedPreferences("spendflow_backup_prefs", Context.MODE_PRIVATE)
    val userConsent = prefs.getBoolean("storage_permission_consent_granted", false)
    return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        userConsent && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        userConsent
    }
}

private fun markStoragePermissionGranted(context: Context) {
    val prefs = context.getSharedPreferences("spendflow_backup_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("storage_permission_consent_granted", true).apply()
}

@Composable
fun DatabaseBackupDialog(
    initialTab: Int = 0, // 0 for Export, 1 for Restore
    onExportBackup: suspend () -> String,
    onRestoreBackup: suspend (String) -> DatabaseRestoreResult,
    onDismissRequest: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Storage Permission States
    var hasGrantedStoragePermission by remember {
        mutableStateOf(isStoragePermissionGranted(context))
    }
    var showStoragePermissionPrompt by remember { mutableStateOf(false) }
    var pendingStorageAction by remember { mutableStateOf<PendingStorageAction?>(null) }
    var permissionDeniedNotice by remember { mutableStateOf<String?>(null) }

    // Export State
    var exportedJson by remember { mutableStateOf("") }
    var exportPreview by remember { mutableStateOf<FullDatabaseBackup?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportSavedNotice by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }

    // Restore State
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableLongStateOf(0L) }
    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var isReadingFile by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<FullDatabaseBackup?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restoreSuccessResult by remember { mutableStateOf<DatabaseRestoreResult.Success?>(null) }
    var showConfirmRestoreAlert by remember { mutableStateOf(false) }

    // Saved device backups list
    var deviceBackupFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshDeviceBackups() {
        try {
            deviceBackupFiles = DatabaseBackupUtils.getDeviceBackupFiles(context)
        } catch (_: Exception) {
            deviceBackupFiles = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        refreshDeviceBackups()
    }

    // Document creation launcher for saving SQLite backup file
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = DatabaseBackupUtils.copyDatabaseToUri(context, uri)
            if (success) {
                val (name, _) = DatabaseBackupUtils.getFileInfoFromUri(context, uri)
                exportSavedNotice = "✓ SQLite database backup saved successfully: $name"
                exportErrorMessage = null
                refreshDeviceBackups()
            } else {
                val jsonSuccess = DatabaseBackupUtils.saveTextToUri(context, uri, exportedJson)
                if (jsonSuccess) {
                    val (name, _) = DatabaseBackupUtils.getFileInfoFromUri(context, uri)
                    exportSavedNotice = "✓ Backup saved successfully: $name"
                    exportErrorMessage = null
                    refreshDeviceBackups()
                } else {
                    exportErrorMessage = "Failed to write backup file to selected location."
                }
            }
        }
    }

    // Document open launcher for picking backup file
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingFile = true
            restoreError = null
            restoreSuccessResult = null
            val (name, size) = DatabaseBackupUtils.getFileInfoFromUri(context, uri)
            selectedFileName = name
            selectedFileSize = size

            coroutineScope.launch {
                val tempFile = DatabaseBackupUtils.copyUriToTempFile(context, uri, "probe_restore.db")
                if (tempFile != null && DatabaseBackupUtils.isSqliteFile(tempFile)) {
                    val parseResult = DatabaseBackupUtils.parseSqliteDatabase(tempFile)
                    isReadingFile = false
                    if (parseResult.isSuccess) {
                        val backup = parseResult.getOrThrow()
                        restorePreview = backup
                        selectedFileContent = DatabaseBackupUtils.exportDatabaseToJson(
                            backup.accounts, backup.categories, backup.transactions, backup.budgets
                        )
                        restoreError = null
                    } else {
                        restorePreview = null
                        restoreError = "Could not parse SQLite database: " +
                            (parseResult.exceptionOrNull()?.message ?: "Unknown SQLite error")
                    }
                } else {
                    val fileText = DatabaseBackupUtils.readTextFromUri(context, uri)
                    isReadingFile = false
                    if (fileText.isNullOrBlank()) {
                        restoreError = "The selected file is empty or could not be read."
                        restorePreview = null
                        selectedFileContent = null
                    } else {
                        selectedFileContent = fileText
                        val parseResult = DatabaseBackupUtils.parseBackupJson(fileText)
                        if (parseResult.isSuccess) {
                            restorePreview = parseResult.getOrNull()
                            restoreError = null
                        } else {
                            restorePreview = null
                            restoreError = "The selected file is not a valid SpendFlow SQLite or JSON backup."
                        }
                    }
                }
            }
        }
    }

    // Fallback content picker if OpenDocument is not available on emulator
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingFile = true
            restoreError = null
            restoreSuccessResult = null
            val (name, size) = DatabaseBackupUtils.getFileInfoFromUri(context, uri)
            selectedFileName = name
            selectedFileSize = size

            coroutineScope.launch {
                val tempFile = DatabaseBackupUtils.copyUriToTempFile(context, uri, "probe_restore.db")
                if (tempFile != null && DatabaseBackupUtils.isSqliteFile(tempFile)) {
                    val parseResult = DatabaseBackupUtils.parseSqliteDatabase(tempFile)
                    isReadingFile = false
                    if (parseResult.isSuccess) {
                        val backup = parseResult.getOrThrow()
                        restorePreview = backup
                        selectedFileContent = DatabaseBackupUtils.exportDatabaseToJson(
                            backup.accounts, backup.categories, backup.transactions, backup.budgets
                        )
                        restoreError = null
                    } else {
                        restorePreview = null
                        restoreError = "Could not parse SQLite database: " +
                            (parseResult.exceptionOrNull()?.message ?: "Unknown SQLite error")
                    }
                } else {
                    val fileText = DatabaseBackupUtils.readTextFromUri(context, uri)
                    isReadingFile = false
                    if (fileText.isNullOrBlank()) {
                        restoreError = "The selected file is empty or could not be read."
                        restorePreview = null
                        selectedFileContent = null
                    } else {
                        selectedFileContent = fileText
                        val parseResult = DatabaseBackupUtils.parseBackupJson(fileText)
                        if (parseResult.isSuccess) {
                            restorePreview = parseResult.getOrNull()
                            restoreError = null
                        } else {
                            restorePreview = null
                            restoreError = "The selected file is not a valid SpendFlow SQLite or JSON backup."
                        }
                    }
                }
            }
        }
    }

    // Safe execution of file operations that avoids unhandled ActivityNotFoundException crashes
    fun executeSafeBackup() {
        val defaultName = DatabaseBackupUtils.generateDefaultBackupFileName()
        // Always save a real SQLite database file directly to device storage
        val savedFile = DatabaseBackupUtils.saveSqliteDatabaseToDevice(context, defaultName)
        if (savedFile != null) {
            exportSavedNotice = "✓ SQLite backup file created: ${savedFile.name} in device Documents!"
            exportErrorMessage = null
            refreshDeviceBackups()
        }

        // Also attempt system document picker if available
        try {
            createDocumentLauncher.launch(defaultName)
        } catch (_: ActivityNotFoundException) {
            if (savedFile != null) {
                exportSavedNotice = "✓ SQLite backup saved to device storage: ${savedFile.name}"
            } else {
                exportErrorMessage = "Could not open system file picker. File saved in app storage."
            }
        } catch (e: Exception) {
            exportErrorMessage = "File picker error: ${e.message ?: "Unknown error"}"
        }
    }

    fun executeSafeRestorePicker() {
        try {
            openDocumentLauncher.launch(arrayOf("application/vnd.sqlite3", "application/x-sqlite3", "application/octet-stream", "application/json", "*/*"))
        } catch (_: ActivityNotFoundException) {
            try {
                getContentLauncher.launch("*/*")
            } catch (_: ActivityNotFoundException) {
                restoreError = "No system file manager found on this device. You can choose from the saved backups listed below."
            } catch (e: Exception) {
                restoreError = "Could not open file picker: ${e.message}"
            }
        } catch (e: Exception) {
            restoreError = "Could not open file picker: ${e.message}"
        }
    }

    fun executeSafeShare() {
        try {
            val defaultName = DatabaseBackupUtils.generateDefaultBackupFileName()
            DatabaseBackupUtils.shareSqliteDatabase(context, defaultName)
        } catch (e: Exception) {
            exportErrorMessage = "Could not share file: ${e.message}"
        }
    }

    // System runtime permission launcher for storage
    val systemStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (readGranted) {
            markStoragePermissionGranted(context)
            hasGrantedStoragePermission = true
            permissionDeniedNotice = null
            showStoragePermissionPrompt = false

            // Safely execute pending action
            when (pendingStorageAction) {
                PendingStorageAction.BACKUP -> executeSafeBackup()
                PendingStorageAction.RESTORE -> executeSafeRestorePicker()
                PendingStorageAction.SHARE -> executeSafeShare()
                null -> {}
            }
            pendingStorageAction = null
        } else {
            hasGrantedStoragePermission = false
            showStoragePermissionPrompt = false
            permissionDeniedNotice = "Storage permission was denied. Cannot backup or restore files without storage access."
        }
    }

    fun requestStorageAction(action: PendingStorageAction) {
        pendingStorageAction = action
        if (!hasGrantedStoragePermission) {
            showStoragePermissionPrompt = true
        } else {
            when (action) {
                PendingStorageAction.BACKUP -> executeSafeBackup()
                PendingStorageAction.RESTORE -> executeSafeRestorePicker()
                PendingStorageAction.SHARE -> executeSafeShare()
            }
            pendingStorageAction = null
        }
    }

    // Generate export JSON when export tab is displayed
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && exportedJson.isBlank()) {
            isExporting = true
            val json = onExportBackup()
            exportedJson = json
            exportPreview = DatabaseBackupUtils.parseBackupJson(json).getOrNull()
            isExporting = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(
                    text = "SQLite Database Backup & Restore",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Create and restore SQLite database backup files (.db / .sqlite)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Backup to File") },
                        icon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("backup_tab_export")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Restore from File") },
                        icon = { Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("backup_tab_restore")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Storage Permission Status Card
                if (!hasGrantedStoragePermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("storage_permission_status_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Storage Permission Required",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Permission is required before creating or restoring backup files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Denied Notice Card
                if (permissionDeniedNotice != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("storage_permission_denied_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Permission Notice",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = permissionDeniedNotice!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    // ================= BACKUP TAB (CREATE FILE) =================
                    if (isExporting) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Preparing database backup file...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // File summary card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "SpendFlow SQLite Database",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Format: SQLite 3 Database (.db)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    exportPreview?.let { preview ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "SQLite tables in database:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text("• accounts: ${preview.accounts.size} record(s)", style = MaterialTheme.typography.bodySmall)
                                            Text("• categories: ${preview.categories.size} record(s)", style = MaterialTheme.typography.bodySmall)
                                            Text("• transactions: ${preview.transactions.size} record(s)", style = MaterialTheme.typography.bodySmall)
                                            Text("• budgets: ${preview.budgets.size} record(s)", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }

                            if (exportSavedNotice != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = exportSavedNotice!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF047857)
                                        )
                                    }
                                }
                            }

                            if (exportErrorMessage != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = exportErrorMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            // Actions: Save to File & Share File with permission check
                            Button(
                                onClick = {
                                    requestStorageAction(PendingStorageAction.BACKUP)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_backup_file_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save SQLite DB File (.db)")
                            }

                            OutlinedButton(
                                onClick = {
                                    requestStorageAction(PendingStorageAction.SHARE)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("share_backup_file_btn")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share SQLite DB File")
                            }
                        }
                    }
                } else {
                    // ================= RESTORE TAB (REQUIRE FILE) =================
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "To restore your database, choose a SpendFlow SQLite database file (.db / .sqlite) or backup file from your device:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // File picker action button with permission check
                        Button(
                            onClick = {
                                requestStorageAction(PendingStorageAction.RESTORE)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("choose_backup_file_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (selectedFileName == null) "Select SQLite DB File (.db / .sqlite)" else "Browse Different File")
                        }

                        if (isReadingFile) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reading database backup...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // List of Existing Saved Backups on Device (Always accessible without SAF)
                        if (deviceBackupFiles.isNotEmpty()) {
                            Text(
                                text = "Saved SQLite & Backup Files on Device (${deviceBackupFiles.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                deviceBackupFiles.take(5).forEach { file ->
                                    val isSelected = selectedFileName == file.name
                                    val isDb = DatabaseBackupUtils.isSqliteFile(file) ||
                                        file.extension.lowercase(Locale.US) in setOf("db", "sqlite", "sqlite3")
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                isReadingFile = true
                                                restoreError = null
                                                restoreSuccessResult = null
                                                selectedFileName = file.name
                                                selectedFileSize = file.length()
                                                coroutineScope.launch {
                                                    if (isDb) {
                                                        val parseResult = DatabaseBackupUtils.parseSqliteDatabase(file)
                                                        isReadingFile = false
                                                        if (parseResult.isSuccess) {
                                                            val backup = parseResult.getOrThrow()
                                                            restorePreview = backup
                                                            selectedFileContent = DatabaseBackupUtils.exportDatabaseToJson(
                                                                backup.accounts, backup.categories, backup.transactions, backup.budgets
                                                            )
                                                            restoreError = null
                                                        } else {
                                                            restorePreview = null
                                                            restoreError = "Could not parse SQLite database from: ${file.name}"
                                                        }
                                                    } else {
                                                        val text = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { null }
                                                        isReadingFile = false
                                                        if (!text.isNullOrBlank()) {
                                                            selectedFileContent = text
                                                            val parseResult = DatabaseBackupUtils.parseBackupJson(text)
                                                            if (parseResult.isSuccess) {
                                                                restorePreview = parseResult.getOrNull()
                                                                restoreError = null
                                                            } else {
                                                                restorePreview = null
                                                                restoreError = "Invalid backup format in file: ${file.name}"
                                                            }
                                                        } else {
                                                            restoreError = "Could not read file: ${file.name}"
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = file.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                if (isDb) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                            )
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isDb) "SQLITE 3" else "JSON",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isDb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${DatabaseBackupUtils.formatFileSize(file.length())} • ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Selected File Information Card
                        if (selectedFileName != null && restorePreview != null) {
                            val preview = restorePreview!!
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = selectedFileName ?: "Selected Backup",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Size: ${DatabaseBackupUtils.formatFileSize(selectedFileSize)} • Valid SpendFlow v${preview.metadata.version}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Ready to restore:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text("• Accounts: ${preview.accounts.size}", style = MaterialTheme.typography.bodySmall)
                                        Text("• Categories: ${preview.categories.size}", style = MaterialTheme.typography.bodySmall)
                                        Text("• Transactions: ${preview.transactions.size}", style = MaterialTheme.typography.bodySmall)
                                        Text("• Budgets: ${preview.budgets.size}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Error message
                        restoreError?.let { err ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Restore Success Result Card
                        restoreSuccessResult?.let { res ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "✓ Database Restored Successfully!",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Restored ${res.accountsCount} accounts, ${res.transactionsCount} transactions, ${res.categoriesCount} categories, and ${res.budgetsCount} budgets from file.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF065F46)
                                    )
                                }
                            }
                        }

                        // Action button to trigger restore
                        Button(
                            onClick = { showConfirmRestoreAlert = true },
                            enabled = restorePreview != null && !isRestoring && selectedFileContent != null,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("execute_restore_db_btn")
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restoring from file...")
                            } else {
                                Text("Restore Database from File")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )

    // Storage Permission Prompt Alert Dialog
    if (showStoragePermissionPrompt) {
        AlertDialog(
            onDismissRequest = {
                showStoragePermissionPrompt = false
                permissionDeniedNotice = "Storage permission was not granted."
                pendingStorageAction = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.FolderShared,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Storage Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (pendingStorageAction) {
                            PendingStorageAction.BACKUP -> "SpendFlow needs permission to access your device storage to create and save the SQLite database backup file (.db)."
                            PendingStorageAction.RESTORE -> "SpendFlow needs permission to access your device storage to select and restore the SQLite database backup file (.db)."
                            PendingStorageAction.SHARE -> "SpendFlow needs storage permission to generate the SQLite database backup file for sharing."
                            else -> "SpendFlow requires storage permission to create or restore SQLite database backup files on your device."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Your financial database remains strictly offline and private on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                val perms = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                } else {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                systemStoragePermissionLauncher.launch(perms)
                            } else {
                                // Android 13+ (API 33+) handles document SAF with user in-app consent
                                markStoragePermissionGranted(context)
                                hasGrantedStoragePermission = true
                                permissionDeniedNotice = null
                                showStoragePermissionPrompt = false

                                // Execute pending action safely
                                when (pendingStorageAction) {
                                    PendingStorageAction.BACKUP -> executeSafeBackup()
                                    PendingStorageAction.RESTORE -> executeSafeRestorePicker()
                                    PendingStorageAction.SHARE -> executeSafeShare()
                                    null -> {}
                                }
                                pendingStorageAction = null
                            }
                        } catch (e: Exception) {
                            // Even if system permission launcher fails on emulator, mark consent and proceed safely
                            markStoragePermissionGranted(context)
                            hasGrantedStoragePermission = true
                            showStoragePermissionPrompt = false
                            when (pendingStorageAction) {
                                PendingStorageAction.BACKUP -> executeSafeBackup()
                                PendingStorageAction.RESTORE -> executeSafeRestorePicker()
                                PendingStorageAction.SHARE -> executeSafeShare()
                                null -> {}
                            }
                            pendingStorageAction = null
                        }
                    },
                    modifier = Modifier.testTag("grant_storage_permission_btn")
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStoragePermissionPrompt = false
                        permissionDeniedNotice = "Storage permission was denied. Cannot ${if (pendingStorageAction == PendingStorageAction.BACKUP) "create" else "restore"} backup file."
                        pendingStorageAction = null
                    },
                    modifier = Modifier.testTag("deny_storage_permission_btn")
                ) {
                    Text("Deny")
                }
            }
        )
    }

    // Confirmation Alert before wiping and restoring
    if (showConfirmRestoreAlert) {
        AlertDialog(
            onDismissRequest = { showConfirmRestoreAlert = false },
            title = { Text("Confirm Database Restore") },
            text = {
                Text(
                    text = "This will replace all existing accounts, categories, budgets, and transactions in your current local database with the contents of '${selectedFileName ?: "the backup file"}'.\n\nAre you sure you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRestoreAlert = false
                        val contentToRestore = selectedFileContent
                        if (!contentToRestore.isNullOrBlank()) {
                            coroutineScope.launch {
                                isRestoring = true
                                val result = onRestoreBackup(contentToRestore)
                                isRestoring = false
                                if (result is DatabaseRestoreResult.Success) {
                                    restoreSuccessResult = result
                                } else if (result is DatabaseRestoreResult.Error) {
                                    restoreError = result.message
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_restore_dialog_btn")
                ) {
                    Text("Yes, Restore File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestoreAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
