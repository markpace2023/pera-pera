package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SwapHoriz
import com.example.ui.components.BankIconSearchDialog
import com.example.util.BankIconSearchService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.example.util.ExchangeRateService
import com.example.util.UserPreferences
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.ui.components.ScreenHeader
import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.StatementDateType
import com.example.ui.components.IconPickerDialog
import com.example.ui.components.IconUtils
import com.example.ui.components.StatementDateInfoCard
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@Composable
fun AccountsScreen(
    accounts: List<Account>,
    onSaveAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onNavigateToTransfer: (fromAccountId: Long?, toAccountId: Long?) -> Unit,
    onTestNotification: (String) -> Unit,
    isPrivacyMode: Boolean = false
) {
    var showAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("accounts_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenHeader(
                    title = "Your Accounts",
                    subtitle = "Manage cards, savings, cash, and digital wallets"
                )
            }

            // Empty State when no accounts exist (Fresh state)
            if (accounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("accounts_empty_state_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Accounts Yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Your database is fresh with no accounts. Add your first cash wallet, savings, bank, or credit card to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    accountToEdit = null
                                    showAccountDialog = true
                                },
                                modifier = Modifier.testTag("create_first_account_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Your First Account")
                            }
                        }
                    }
                }
            }

            // Group Credit Cards
            val creditCards = accounts.filter { it.type == AccountType.CREDIT_CARD }
            val otherAccounts = accounts.filter { it.type != AccountType.CREDIT_CARD }

            if (creditCards.isNotEmpty()) {
                item {
                    Text(
                        text = "Credit Cards (${creditCards.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(creditCards, key = { it.id }) { card ->
                    AccountCardItem(
                        account = card,
                        onEdit = {
                            accountToEdit = card
                            showAccountDialog = true
                        },
                        onDelete = { onDeleteAccount(card) },
                        onTransfer = { onNavigateToTransfer(null, card.id) },
                        isPrivacyMode = isPrivacyMode
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatementDateInfoCard(
                        account = card,
                        onTestCutoffAlert = { onTestNotification(card.name) },
                        isPrivacyMode = isPrivacyMode
                    )
                }
            }

            if (otherAccounts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cash, Debit & Savings (${otherAccounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(otherAccounts, key = { it.id }) { acc ->
                    AccountCardItem(
                        account = acc,
                        onEdit = {
                            accountToEdit = acc
                            showAccountDialog = true
                        },
                        onDelete = { onDeleteAccount(acc) },
                        onTransfer = { onNavigateToTransfer(acc.id, null) },
                        isPrivacyMode = isPrivacyMode
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }

        FloatingActionButton(
            onClick = {
                accountToEdit = null
                showAccountDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp)
                .testTag("add_account_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Account")
        }
    }

    if (showAccountDialog) {
        AccountEditDialog(
            account = accountToEdit,
            onSave = { acc ->
                onSaveAccount(acc)
                showAccountDialog = false
            },
            onDismiss = { showAccountDialog = false }
        )
    }
}

@Composable
fun AccountCardItem(
    account: Account,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTransfer: () -> Unit,
    isPrivacyMode: Boolean = false
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isCredit = account.type == AccountType.CREDIT_CARD
    val cardColor = runCatching { Color(android.graphics.Color.parseColor(account.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_card_${account.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isLocal = BankIconSearchService.isLocalIcon(account.iconName)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isLocal) Color.White else cardColor.copy(alpha = 0.18f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isLocal) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconUtils.AccountIconView(
                            iconName = account.iconName,
                            tint = cardColor,
                            modifier = Modifier.size(if (isLocal) 34.dp else 26.dp),
                            contentDescription = account.name
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (account.accountNumberMask.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = account.accountNumberMask,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = account.type.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (isCredit) {
                            if (account.balance < 0) "Overpayment / Credit" else "Current Outstanding Balance"
                        } else {
                            "Available Balance"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(account.balance, account.currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCredit) {
                            if (account.balance < 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        } else {
                            if (account.balance >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        }
                    )
                }

                if (isCredit) {
                    FilledTonalButton(
                        onClick = onTransfer,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("pay_card_${account.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pay",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onTransfer,
                        modifier = Modifier.testTag("transfer_from_card_${account.id}")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Transfer")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${account.name}?") },
            text = { Text("Deleting this account will also remove all its associated transactions. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AccountEditDialog(
    account: Account?,
    onSave: (Account) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = account != null

    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: AccountType.SAVINGS) }
    var balanceValue by remember {
        mutableStateOf(account?.balance?.let { CurrencyUtils.toTextFieldValue(it) } ?: TextFieldValue(""))
    }
    val context = LocalContext.current
    val initialCurrency = remember(account) {
        account?.currency ?: UserPreferences.getPrimaryCurrency(context)
    }
    var currency by remember { mutableStateOf(initialCurrency) }
    var colorHex by remember { mutableStateOf(account?.colorHex ?: "#10B981") }
    var iconKey by remember { mutableStateOf(account?.iconName ?: type.defaultIcon) }
    var maskText by remember { mutableStateOf(account?.accountNumberMask ?: "") }

    // Credit card fields
    var creditLimitValue by remember {
        mutableStateOf(account?.creditLimit?.let { CurrencyUtils.toTextFieldValue(it) } ?: CurrencyUtils.toTextFieldValue(50000.0))
    }
    var statementDateType by remember { mutableStateOf(account?.statementDateType ?: StatementDateType.VARIABLE_MONTH_LENGTH) }
    var statementDayText by remember { mutableStateOf(account?.statementDay?.toString() ?: "18") }
    var statementOffsetDaysText by remember { mutableStateOf(account?.statementOffsetDays?.toString() ?: "13") }
    var paymentGraceDaysText by remember { mutableStateOf(account?.paymentGraceDays?.toString() ?: "21") }

    var isTypeDropdownOpen by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var isStatementTypeDropdownOpen by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showBankIconSearchDialog by remember { mutableStateOf(false) }

    val presetColors = listOf(
        "#10B981", "#3B82F6", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4", "#EC4899", "#1E293B"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Account" else "Add New Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Account Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            // Smart auto-suggest icon if not manually changed to local
                            if (!BankIconSearchService.isLocalIcon(iconKey)) {
                                iconKey = IconUtils.suggestIconForAccount(it, type.name)
                            }
                        },
                        label = { Text("Account Name *") },
                        placeholder = { Text("e.g. BDO, BPI, Chase, GCash") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_name_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Internet Real Bank Icon Query Button
                    FilledTonalButton(
                        onClick = { showBankIconSearchDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_bank_icon_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (BankIconSearchService.isLocalIcon(iconKey)) "Change Real Bank Icon (Online)" else "Find Real Bank Icon Online",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                item {
                    // Type selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isTypeDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Type: ${type.displayName}", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isTypeDropdownOpen,
                            onDismissRequest = { isTypeDropdownOpen = false }
                        ) {
                            AccountType.values().forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.displayName) },
                                    onClick = {
                                        type = t
                                        iconKey = IconUtils.suggestIconForAccount(name, t.name)
                                        isTypeDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    // Icon & Color row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Icon preview and choose button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isLocal = BankIconSearchService.isLocalIcon(iconKey)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isLocal) Color.White else runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary).copy(alpha = 0.2f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isLocal) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { showBankIconSearchDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                IconUtils.AccountIconView(
                                    iconName = iconKey,
                                    tint = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.size(if (isLocal) 32.dp else 24.dp),
                                    contentDescription = null
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(
                                    onClick = { showBankIconSearchDialog = true },
                                    modifier = Modifier.testTag("choose_online_bank_logo_btn")
                                ) {
                                    Text("Online")
                                }
                                TextButton(
                                    onClick = { showIconPicker = true },
                                    modifier = Modifier.testTag("choose_symbol_icon_btn")
                                ) {
                                    Text("Symbol")
                                }
                            }
                        }

                        // Currency selector
                        val currInfo = ExchangeRateService.getCurrencyInfo(currency)
                        OutlinedButton(
                            onClick = { showCurrencyPicker = true },
                            modifier = Modifier.testTag("account_currency_selector_btn")
                        ) {
                            Text("${currInfo.flag} $currency (${currInfo.symbol})", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                }

                item {
                    // Color swatches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetColors.forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            val isSelected = colorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .then(
                                        if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                    )
                                    .clickable { colorHex = hex }
                            )
                        }
                    }
                }

                item {
                    // Balance input
                    OutlinedTextField(
                        value = balanceValue,
                        onValueChange = { balanceValue = CurrencyUtils.formatCurrencyInput(it, balanceValue.text) },
                        label = { Text(if (type == AccountType.CREDIT_CARD) "Current Outstanding Balance" else "Initial Balance") },
                        placeholder = { Text("0.00") },
                        prefix = { Text(if (currency == "PHP") "₱" else "$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_balance_input")
                    )
                }

                item {
                    // Mask / Last 4 digits
                    OutlinedTextField(
                        value = maskText,
                        onValueChange = { maskText = it },
                        label = { Text("Card/Account Mask (Optional)") },
                        placeholder = { Text("e.g. *4821") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Credit Card Specific Settings
                if (type == AccountType.CREDIT_CARD) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Credit Card Statement Settings",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedTextField(
                                    value = creditLimitValue,
                                    onValueChange = { creditLimitValue = CurrencyUtils.formatCurrencyInput(it, creditLimitValue.text) },
                                    label = { Text("Total Credit Limit") },
                                    placeholder = { Text("0.00") },
                                    prefix = { Text(if (currency == "PHP") "₱" else "$") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Statement date type dropdown
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { isStatementTypeDropdownOpen = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(statementDateType.displayName, modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = isStatementTypeDropdownOpen,
                                        onDismissRequest = { isStatementTypeDropdownOpen = false }
                                    ) {
                                        StatementDateType.values().forEach { sType ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(sType.displayName, fontWeight = FontWeight.Bold)
                                                        Text(sType.description, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                },
                                                onClick = {
                                                    statementDateType = sType
                                                    isStatementTypeDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (statementDateType == StatementDateType.FIXED) {
                                    OutlinedTextField(
                                        value = statementDayText,
                                        onValueChange = { statementDayText = it },
                                        label = { Text("Fixed Cutoff Day of Month (1 - 31)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else if (statementDateType == StatementDateType.VARIABLE_MONTH_LENGTH) {
                                    OutlinedTextField(
                                        value = statementDayText,
                                        onValueChange = { statementDayText = it },
                                        label = { Text("Base Cutoff Day (for 31-day month, e.g. 18)") },
                                        supportingText = { Text("Automatically adjusts: 31d: 18th, 30d: 17th, Feb: 15th") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = statementOffsetDaysText,
                                        onValueChange = { statementOffsetDaysText = it },
                                        label = { Text("Days Before Month End (e.g. 13)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                OutlinedTextField(
                                    value = paymentGraceDaysText,
                                    onValueChange = { paymentGraceDaysText = it },
                                    label = { Text("Payment Due Days after Cutoff (e.g. 21 days)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val balance = CurrencyUtils.parseCurrencyInput(balanceValue)
                        val creditLimit = CurrencyUtils.parseCurrencyInput(creditLimitValue)
                        val statementDay = statementDayText.toIntOrNull() ?: 18
                        val statementOffset = statementOffsetDaysText.toIntOrNull() ?: 13
                        val paymentGrace = paymentGraceDaysText.toIntOrNull() ?: 21

                        val acc = Account(
                            id = account?.id ?: 0L,
                            name = name.trim(),
                            type = type,
                            balance = balance,
                            initialBalance = if (isEditing) (account?.initialBalance ?: balance) else balance,
                            currency = currency,
                            colorHex = colorHex,
                            iconName = iconKey,
                            accountNumberMask = maskText.trim(),
                            creditLimit = creditLimit,
                            statementDateType = statementDateType,
                            statementDay = statementDay,
                            statementOffsetDays = statementOffset,
                            paymentGraceDays = paymentGrace
                        )
                        onSave(acc)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text(if (isEditing) "Save Changes" else "Add Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showIconPicker) {
        IconPickerDialog(
            selectedIconKey = iconKey,
            onIconSelected = { key ->
                iconKey = key
                showIconPicker = false
            },
            onDismissRequest = { showIconPicker = false },
            onOpenOnlineSearch = {
                showBankIconSearchDialog = true
            }
        )
    }

    if (showBankIconSearchDialog) {
        BankIconSearchDialog(
            initialQuery = name,
            onIconSavedAndSelected = { localKey ->
                iconKey = localKey
                showBankIconSearchDialog = false
            },
            onDismissRequest = { showBankIconSearchDialog = false }
        )
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currentCurrency = currency,
            onSelectCurrency = { selected ->
                currency = selected
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

@Composable
fun CurrencyPickerDialog(
    currentCurrency: String,
    onSelectCurrency: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) ExchangeRateService.SUPPORTED_CURRENCIES
        else ExchangeRateService.SUPPORTED_CURRENCIES.filter {
            it.code.lowercase().contains(q) ||
            it.name.lowercase().contains(q) ||
            it.symbol.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Account Currency",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search currency or code...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredList) { item ->
                        val isSelected = item.code.equals(currentCurrency, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCurrency(item.code)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(item.flag, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = item.code,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = item.symbol,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

