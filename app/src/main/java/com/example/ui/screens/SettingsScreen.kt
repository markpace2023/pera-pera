package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.components.DatabaseBackupDialog
import com.example.util.BiometricHelper
import com.example.util.DatabaseRestoreResult
import com.example.util.NotificationHelper
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload

import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.example.util.ExchangeRateService
import com.example.util.UserPreferences
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    biometricHelper: BiometricHelper,
    notificationHelper: NotificationHelper,
    onLockAppNow: () -> Unit,
    onResetDatabase: () -> Unit,
    onExportDatabaseBackup: suspend () -> String,
    onRestoreDatabaseBackup: suspend (String) -> DatabaseRestoreResult,
    primaryCurrency: String = "PHP",
    onPrimaryCurrencyChanged: (String) -> Unit = {},
    exchangeRates: Map<String, Double> = emptyMap(),
    onRefreshExchangeRates: suspend () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isBiometricEnabled by remember { mutableStateOf(biometricHelper.isBiometricEnabled) }
    var isRecurringNotifEnabled by remember { mutableStateOf(notificationHelper.isRecurringNotificationEnabled) }
    var isBudgetNotifEnabled by remember { mutableStateOf(notificationHelper.isBudgetNotificationEnabled) }
    var isCcNotifEnabled by remember { mutableStateOf(notificationHelper.isCreditCardNotificationEnabled) }
    var isLoanNotifEnabled by remember { mutableStateOf(notificationHelper.isLoanNotificationEnabled) }

    var showPinDialog by remember { mutableStateOf(false) }
    var newPinText by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupDialogTab by remember { mutableIntStateOf(0) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var isRefreshingRates by remember { mutableStateOf(false) }

    val currentCurrencyInfo = remember(primaryCurrency) {
        ExchangeRateService.getCurrencyInfo(primaryCurrency)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Settings & Preferences",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Primary currency, biometric security lock, notifications, and backup",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Primary Currency Section (User requirement: "settings should have a primary currency dropdown")
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("primary_currency_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Paid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Primary Currency",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Used for Total Net Worth, multi-currency conversions, and reports",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Primary Currency Dropdown Selector Button
                    OutlinedButton(
                        onClick = { showCurrencyPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_primary_currency_dropdown"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(currentCurrencyInfo.flag, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${currentCurrencyInfo.code} - ${currentCurrencyInfo.name} (${currentCurrencyInfo.symbol})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Primary Currency")
                        }
                    }

                    // Internet Exchange Rates Live Status & Refresh
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Live Internet Exchange Rates",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = ExchangeRateService.getLastUpdatedFormatted(context),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isRefreshingRates = true
                                            onRefreshExchangeRates()
                                            isRefreshingRates = false
                                        }
                                    },
                                    enabled = !isRefreshingRates,
                                    modifier = Modifier.testTag("refresh_exchange_rates_btn")
                                ) {
                                    if (isRefreshingRates) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Refresh", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            // Quick sample rates preview relative to primary currency
                            val sampleCurrencies = listOf("USD", "EUR", "GBP", "JPY", "SGD", "CAD", "AUD", "PHP")
                                .filter { !it.equals(primaryCurrency, ignoreCase = true) }
                                .take(4)

                            if (sampleCurrencies.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    sampleCurrencies.forEach { sampleCurr ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "1 $sampleCurr",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                val converted = ExchangeRateService.convert(1.0, sampleCurr, primaryCurrency, exchangeRates)
                                                Text(
                                                    text = "${currentCurrencyInfo.symbol}${com.example.util.DateUtils.formatNumber(converted)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Biometrics & Security Section (User requirement: "able to add biometrics login")
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Biometric App Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Biometric Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Require Fingerprint / PIN to open",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Protects your financial accounts and transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { checked ->
                                isBiometricEnabled = checked
                                biometricHelper.isBiometricEnabled = checked
                            },
                            modifier = Modifier.testTag("biometric_toggle_switch")
                        )
                    }

                    if (isBiometricEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    newPinText = biometricHelper.getStoredPin()
                                    showPinDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (biometricHelper.hasPin()) "Change PIN" else "Set 4-Digit PIN")
                            }

                            Button(
                                onClick = onLockAppNow,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.weight(1f).testTag("lock_app_now_btn")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock Now")
                            }
                        }
                    }
                }
            }
        }

        // Notification Toggles Section (User requirement: "notification toggle for recurring expense/near budget depletion/credit card statement date")
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 1. Recurring Expense Toggle & Test
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Recurring Expense Due Alerts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Remind me before recurring bills/expenses are charged",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isRecurringNotifEnabled,
                                onCheckedChange = { checked ->
                                    isRecurringNotifEnabled = checked
                                    notificationHelper.isRecurringNotificationEnabled = checked
                                },
                                modifier = Modifier.testTag("recurring_notif_switch")
                            )
                        }
                        if (isRecurringNotifEnabled) {
                            TextButton(
                                onClick = {
                                    notificationHelper.sendRecurringDueNotification(
                                        title = "Netflix Subscription",
                                        amountFormatted = "₱549.00",
                                        accountName = "UnionBank Visa CC"
                                    )
                                },
                                modifier = Modifier.testTag("test_recurring_notif_btn")
                            ) {
                                Text("Test Recurring Alert")
                            }
                        }
                    }

                    // 2. Near Budget Depletion Toggle & Test
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Near Budget Depletion Alerts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Alert when a category reaches 80% or exceeds budget",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isBudgetNotifEnabled,
                                onCheckedChange = { checked ->
                                    isBudgetNotifEnabled = checked
                                    notificationHelper.isBudgetNotificationEnabled = checked
                                },
                                modifier = Modifier.testTag("budget_notif_switch")
                            )
                        }
                        if (isBudgetNotifEnabled) {
                            TextButton(
                                onClick = {
                                    notificationHelper.sendBudgetWarningNotification(
                                        categoryName = "Food & Dining",
                                        percentSpent = 85,
                                        currentSpendFormatted = "₱21,250.00",
                                        limitFormatted = "₱25,000.00"
                                    )
                                },
                                modifier = Modifier.testTag("test_budget_notif_btn")
                            ) {
                                Text("Test Budget Alert")
                            }
                        }
                    }

                    // 3. Credit Card Statement Cutoff Date Toggle & Test
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Credit Card Statement Cutoff Alerts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Remind me 2 days before fixed and variable statement cutoffs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isCcNotifEnabled,
                                onCheckedChange = { checked ->
                                    isCcNotifEnabled = checked
                                    notificationHelper.isCreditCardNotificationEnabled = checked
                                },
                                modifier = Modifier.testTag("cc_statement_notif_switch")
                            )
                        }
                        if (isCcNotifEnabled) {
                            TextButton(
                                onClick = {
                                    notificationHelper.sendCreditCardCutoffNotification(
                                        cardName = "UnionBank Visa CC",
                                        daysLeft = 2,
                                        cutoffDateFormatted = "Nov 17, 2026",
                                        currentBalanceFormatted = "₱6,800.00"
                                    )
                                },
                                modifier = Modifier.testTag("test_cc_notif_btn")
                            ) {
                                Text("Test Statement Cutoff Alert")
                            }
                        }
                    }

                    // 4. Borrowing & Lending Due Alerts Toggle & Test
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Borrowing & Lending Alerts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Remind me before repayments are due for money borrowed or lent",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isLoanNotifEnabled,
                                onCheckedChange = { checked ->
                                    isLoanNotifEnabled = checked
                                    notificationHelper.isLoanNotificationEnabled = checked
                                },
                                modifier = Modifier.testTag("loan_notif_switch")
                            )
                        }
                        if (isLoanNotifEnabled) {
                            TextButton(
                                onClick = {
                                    notificationHelper.sendLoanDueNotification(
                                        person = "Alex Rivera",
                                        isBorrowing = true,
                                        amountFormatted = "₱5,000.00",
                                        dueDateFormatted = "Nov 15, 2026"
                                    )
                                },
                                modifier = Modifier.testTag("test_loan_notif_btn")
                            ) {
                                Text("Test Borrowing Alert")
                            }
                        }
                    }
                }
            }
        }

        // Full Database Backup & Restore Section (User requirement: "make the backup to backup the whole database")
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SQLite Database Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Create a complete backup file of your entire SQLite database (.db) — including all accounts, balances, categories, budgets, and transactions. Restore anytime from an SQLite or JSON backup file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                backupDialogTab = 0
                                showBackupDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_backup_db_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SQLite Backup")
                        }

                        OutlinedButton(
                            onClick = {
                                backupDialogTab = 1
                                showBackupDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_restore_db_btn")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore SQLite")
                        }
                    }
                }
            }
        }

        // Local Database & Data Storage Section (User requirement: "local database only")
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Storage & Privacy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "SpendFlow operates strictly offline with an on-device encrypted Room SQLite database. Your financial data never leaves your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().testTag("reset_db_btn")
                    ) {
                        Text(
                            text = "Reset Database to Fresh State (0 Accounts)",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    // Set PIN dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set 4-Digit Security PIN") },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit numeric code to unlock SpendFlow when biometric authentication is bypassed or unavailable:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                newPinText = it
                            }
                        },
                        label = { Text("4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_setup_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinText.length == 4) {
                            biometricHelper.setPin(newPinText)
                            showPinDialog = false
                        }
                    },
                    enabled = newPinText.length == 4
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Confirm Dialog (Fresh state: 0 accounts)
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset to Fresh State?") },
            text = { Text("This will clear all transactions, budgets, and accounts, leaving a clean fresh state with 0 accounts and standard categories.") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDatabase()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_db_btn")
                ) {
                    Text("Reset to Fresh State")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBackupDialog) {
        DatabaseBackupDialog(
            initialTab = backupDialogTab,
            onExportBackup = onExportDatabaseBackup,
            onRestoreBackup = onRestoreDatabaseBackup,
            onDismissRequest = { showBackupDialog = false }
        )
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currentCurrency = primaryCurrency,
            onSelectCurrency = { selected ->
                UserPreferences.setPrimaryCurrency(context, selected)
                onPrimaryCurrencyChanged(selected)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

