package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.ui.components.ScreenHeader
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.RecurringFrequency
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import com.example.ui.components.IconUtils
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import com.example.util.LoanUtils
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

import com.example.ui.components.TransactionDetailDialog
import com.example.util.ExchangeRateService

@Composable
fun TransactionsScreen(
    transactions: List<TransactionWithDetails>,
    accounts: List<Account>,
    categories: List<Category>,
    onDeleteTransaction: (Transaction) -> Unit,
    onEditTransaction: (oldTx: Transaction, newTx: Transaction) -> Unit,
    onAddNew: () -> Unit,
    onToggleLoanSettled: ((Transaction) -> Unit)? = null,
    onNavigateToLoansManagement: (() -> Unit)? = null,
    isPrivacyMode: Boolean = false,
    primaryCurrency: String = "PHP",
    exchangeRates: Map<String, Double> = emptyMap()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var selectedAccountIdFilter by remember { mutableStateOf<Long?>(null) }
    var showOnlyRecurring by remember { mutableStateOf(false) }
    var showOnlyLoans by remember { mutableStateOf(false) }

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var viewingTransaction by remember { mutableStateOf<TransactionWithDetails?>(null) }

    val filteredList = remember(
        transactions,
        searchQuery,
        selectedTypeFilter,
        selectedAccountIdFilter,
        showOnlyRecurring,
        showOnlyLoans
    ) {
        transactions.filter { item ->
            val tx = item.transaction
            val matchesSearch = searchQuery.isBlank() ||
                tx.title.contains(searchQuery, ignoreCase = true) ||
                tx.note.contains(searchQuery, ignoreCase = true) ||
                (tx.loanPerson?.contains(searchQuery, ignoreCase = true) == true) ||
                item.accountName.contains(searchQuery, ignoreCase = true) ||
                (item.categoryName?.contains(searchQuery, ignoreCase = true) == true)

            val matchesType = selectedTypeFilter == null || tx.type == selectedTypeFilter
            val matchesAccount = selectedAccountIdFilter == null || tx.accountId == selectedAccountIdFilter || tx.toAccountId == selectedAccountIdFilter
            val matchesRecurring = !showOnlyRecurring || tx.isRecurring
            val matchesLoan = !showOnlyLoans || tx.isLoan

            matchesSearch && matchesType && matchesAccount && matchesRecurring && matchesLoan
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("transactions_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ScreenHeader(
                title = "Transactions Ledger",
                subtitle = "Filter, search, view, and edit transaction history"
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by title, note, category...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Type Filter Chips: All, Expenses, Income, Transfers, Recurring
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == null && !showOnlyRecurring,
                    onClick = {
                        selectedTypeFilter = null
                        showOnlyRecurring = false
                    },
                    label = { Text("All (${transactions.size})") },
                    modifier = Modifier.testTag("tx_filter_all")
                )

                FilterChip(
                    selected = selectedTypeFilter == TransactionType.EXPENSE,
                    onClick = {
                        selectedTypeFilter = if (selectedTypeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE
                        showOnlyRecurring = false
                    },
                    label = { Text("Expenses") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.testTag("tx_filter_expense")
                )

                FilterChip(
                    selected = selectedTypeFilter == TransactionType.INCOME,
                    onClick = {
                        selectedTypeFilter = if (selectedTypeFilter == TransactionType.INCOME) null else TransactionType.INCOME
                        showOnlyRecurring = false
                    },
                    label = { Text("Income") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("tx_filter_income")
                )

                FilterChip(
                    selected = selectedTypeFilter == TransactionType.TRANSFER,
                    onClick = {
                        selectedTypeFilter = if (selectedTypeFilter == TransactionType.TRANSFER) null else TransactionType.TRANSFER
                        showOnlyRecurring = false
                    },
                    label = { Text("Transfers") },
                    modifier = Modifier.testTag("tx_filter_transfer")
                )

                FilterChip(
                    selected = showOnlyRecurring,
                    onClick = {
                        showOnlyRecurring = !showOnlyRecurring
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Recurring") },
                    modifier = Modifier.testTag("tx_filter_recurring")
                )

                FilterChip(
                    selected = showOnlyLoans,
                    onClick = {
                        showOnlyLoans = !showOnlyLoans
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Loans (Borrow/Lend)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.testTag("tx_filter_loans")
                )
            }

            if (showOnlyLoans && onNavigateToLoansManagement != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToLoansManagement() }
                        .testTag("tx_loans_hub_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Handshake,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Debts & Loans Hub",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Manage borrowed/lent money and update repayments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        TextButton(
                            onClick = onNavigateToLoansManagement,
                            modifier = Modifier.testTag("tx_open_loans_hub_btn")
                        ) {
                            Text("Open Hub ›", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transactions List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No matching transactions found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.transaction.id }) { item ->
                        val tx = item.transaction
                        var showDeleteDialog by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingTransaction = item }
                                .testTag("ledger_item_${tx.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when (tx.type) {
                                                        TransactionType.EXPENSE -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                                        TransactionType.INCOME -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                        TransactionType.TRANSFER -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconUtils.getIcon(item.categoryIcon ?: item.accountIcon),
                                                contentDescription = null,
                                                tint = when (tx.type) {
                                                    TransactionType.EXPENSE -> Color(0xFFEF4444)
                                                    TransactionType.INCOME -> Color(0xFF10B981)
                                                    TransactionType.TRANSFER -> Color(0xFF3B82F6)
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = tx.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (tx.isRecurring) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = tx.recurringFrequency.label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = if (tx.type == TransactionType.TRANSFER) {
                                                    "${item.accountName} ➔ ${item.toAccountName ?: "Target"}"
                                                } else {
                                                    "${item.accountName} • ${item.categoryName ?: "General"}"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            if (tx.note.isNotBlank()) {
                                                Text(
                                                    text = tx.note,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = DateUtils.formatDate(tx.timestamp) + " " + DateUtils.formatTime(tx.timestamp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            val prefix = when (tx.type) {
                                                TransactionType.EXPENSE -> "-"
                                                TransactionType.INCOME -> "+"
                                                TransactionType.TRANSFER -> "⇋"
                                            }
                                            val color = when (tx.type) {
                                                TransactionType.EXPENSE -> Color(0xFFEF4444)
                                                TransactionType.INCOME -> Color(0xFF10B981)
                                                TransactionType.TRANSFER -> Color(0xFF3B82F6)
                                            }
                                            val txCurrency = item.accountCurrency.uppercase().trim().ifBlank { primaryCurrency }
                                            val isDiffCurrency = !txCurrency.equals(primaryCurrency, ignoreCase = true)

                                            Text(
                                                text = if (isPrivacyMode) "••••••" else "$prefix${DateUtils.formatCurrency(tx.amount, txCurrency)}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = color
                                            )
                                            if (!isPrivacyMode && isDiffCurrency) {
                                                val converted = ExchangeRateService.convert(tx.amount, txCurrency, primaryCurrency, exchangeRates)
                                                Text(
                                                    text = "≈ $prefix${DateUtils.formatCurrency(converted, primaryCurrency)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            if (tx.transferFee > 0) {
                                                Text(
                                                    text = "Fee: ${DateUtils.formatCurrency(tx.transferFee, txCurrency)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Edit Button
                                        IconButton(
                                            onClick = { editingTransaction = tx },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("edit_tx_btn_${tx.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Delete Button
                                        IconButton(
                                            onClick = { showDeleteDialog = true },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("delete_tx_btn_${tx.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (tx.isLoan) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp)
                                            .padding(bottom = 12.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (tx.isLoanSettled) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFF59E0B).copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Handshake,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (tx.isLoanSettled) Color(0xFF10B981) else Color(0xFFD97706)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (tx.type == TransactionType.INCOME) "Borrowed from: ${tx.loanPerson ?: "Someone"}" else "Lent to: ${tx.loanPerson ?: "Someone"}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (tx.isLoanSettled) Color(0xFF047857) else Color(0xFFB45309)
                                                    )
                                                }

                                                val details = buildList {
                                                    if (tx.loanDueDate != null) add("Due: ${DateUtils.formatDate(tx.loanDueDate)}")
                                                    if (tx.hasInterest && tx.interestRate != null) {
                                                        val loanCurrency = item.accountCurrency
                                                        val totalPayable = LoanUtils.calculateTotalPayable(tx)
                                                        add("Interest: ${LoanUtils.formatInterestSummary(tx, loanCurrency)}")
                                                        val dueStr = DateUtils.formatCurrency(totalPayable, loanCurrency)
                                                        if (loanCurrency != primaryCurrency) {
                                                            val convertedTotal = ExchangeRateService.convert(totalPayable, loanCurrency, primaryCurrency, exchangeRates)
                                                            add("Total Due: $dueStr (≈ ${DateUtils.formatCurrency(convertedTotal, primaryCurrency)})")
                                                        } else {
                                                            add("Total Due: $dueStr")
                                                        }
                                                    }
                                                    if (tx.hasTerm) add("Term: ${tx.termLength} ${tx.termUnit}")
                                                }.joinToString(" • ")

                                                if (details.isNotBlank()) {
                                                    Text(
                                                        text = details,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (tx.isLoanSettled) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.25f),
                                                modifier = Modifier.clickable(enabled = onToggleLoanSettled != null) {
                                                    onToggleLoanSettled?.invoke(tx)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (tx.isLoanSettled) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = if (tx.isLoanSettled) Color(0xFF059669) else Color(0xFFD97706)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (tx.isLoanSettled) "Settled ✓" else "Pending (Tap)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (tx.isLoanSettled) Color(0xFF059669) else Color(0xFFD97706)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete Transaction?") },
                                text = { Text("This will rollback the balance changes on ${item.accountName}.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onDeleteTransaction(tx)
                                            showDeleteDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddNew,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp)
                .testTag("transactions_add_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Transaction")
        }
    }

    // Edit Transaction Dialog
    editingTransaction?.let { txToEdit ->
        EditTransactionDialog(
            transaction = txToEdit,
            accounts = accounts,
            categories = categories,
            isPrivacyMode = isPrivacyMode,
            onSave = { updatedTx ->
                onEditTransaction(txToEdit, updatedTx)
                editingTransaction = null
            },
            onDismiss = { editingTransaction = null }
        )
    }

    // View Transaction Details Dialog
    viewingTransaction?.let { txDetails ->
        TransactionDetailDialog(
            item = txDetails,
            primaryCurrency = primaryCurrency,
            exchangeRates = exchangeRates,
            isPrivacyMode = isPrivacyMode,
            onEdit = { tx ->
                viewingTransaction = null
                editingTransaction = tx
            },
            onDismiss = { viewingTransaction = null }
        )
    }
}

@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    accounts: List<Account>,
    categories: List<Category>,
    isPrivacyMode: Boolean = false,
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(transaction.type) }
    var selectedAccountId by remember { mutableLongStateOf(transaction.accountId) }
    var targetAccountId by remember { mutableLongStateOf(transaction.toAccountId ?: accounts.firstOrNull { it.id != selectedAccountId }?.id ?: 0L) }
    var amountValue by remember { mutableStateOf(CurrencyUtils.toTextFieldValue(transaction.amount)) }
    var feeValue by remember { mutableStateOf(CurrencyUtils.toTextFieldValue(transaction.transferFee, showZeroAsEmpty = true)) }
    var titleText by remember { mutableStateOf(transaction.title) }
    var noteText by remember { mutableStateOf(transaction.note) }
    var selectedCategoryId by remember(type, categories) {
        mutableStateOf(transaction.categoryId ?: categories.firstOrNull { it.type == type }?.id)
    }

    var isRecurring by remember { mutableStateOf(transaction.isRecurring) }
    var frequency by remember { mutableStateOf(if (transaction.recurringFrequency != RecurringFrequency.NONE) transaction.recurringFrequency else RecurringFrequency.MONTHLY) }
    var recurringOccurrencesText by remember { mutableStateOf(if (transaction.recurringTotalCount > 0) transaction.recurringTotalCount.toString() else "12") }
    var isUnlimitedRecurring by remember { mutableStateOf(transaction.recurringTotalCount == 0) }

    var isAccountDropdownOpen by remember { mutableStateOf(false) }
    var isTargetAccountDropdownOpen by remember { mutableStateOf(false) }
    var isFrequencyDropdownOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var selectedTimestamp by remember { mutableLongStateOf(transaction.timestamp) }

    fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val updatedCal = Calendar.getInstance().apply {
                    timeInMillis = selectedTimestamp
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, selectedDay)
                }
                selectedTimestamp = updatedCal.timeInMillis
            },
            year,
            month,
            day
        ).show()
    }

    fun showTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                val updatedCal = Calendar.getInstance().apply {
                    timeInMillis = selectedTimestamp
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                }
                selectedTimestamp = updatedCal.timeInMillis
            },
            hour,
            minute,
            false
        ).show()
    }

    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val targetAccount = accounts.find { it.id == targetAccountId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Type selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    TransactionType.values().forEach { t ->
                        val isSelected = type == t
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                when (t) {
                                    TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                                    TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                                    TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiary
                                }
                            } else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    type = t
                                    val filtered = categories.filter { it.type == t }
                                    selectedCategoryId = filtered.firstOrNull()?.id
                                }
                        ) {
                            Text(
                                text = t.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = CurrencyUtils.formatCurrencyInput(it, amountValue.text) },
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    prefix = { Text(if (currentAccount?.currency == "PHP") "₱" else "$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_amount_input")
                )

                // Title
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Title / Description") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_title_input")
                )

                // Date & Time Picker Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Date & Time",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { selectedTimestamp = System.currentTimeMillis() },
                            modifier = Modifier.testTag("edit_tx_set_now_btn")
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Now", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_tx_date_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = DateUtils.formatDate(selectedTimestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showTimePicker() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_tx_time_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = DateUtils.formatTime(selectedTimestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Account Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isAccountDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Account: ${currentAccount?.name ?: "Select"}", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = isAccountDropdownOpen,
                        onDismissRequest = { isAccountDropdownOpen = false }
                    ) {
                        accounts.forEach { acc ->
                            val balanceText = if (isPrivacyMode) "" else " (${DateUtils.formatCurrency(acc.balance, acc.currency)})"
                            DropdownMenuItem(
                                text = { Text("${acc.name}$balanceText") },
                                onClick = {
                                    selectedAccountId = acc.id
                                    isAccountDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // Target Account if Transfer
                if (type == TransactionType.TRANSFER) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isTargetAccountDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("To Account: ${targetAccount?.name ?: "Select"}", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isTargetAccountDropdownOpen,
                            onDismissRequest = { isTargetAccountDropdownOpen = false }
                        ) {
                            accounts.filter { it.id != selectedAccountId }.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        targetAccountId = acc.id
                                        isTargetAccountDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = feeValue,
                        onValueChange = { feeValue = CurrencyUtils.formatCurrencyInput(it, feeValue.text) },
                        label = { Text("Transfer Fee") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Category Selection Chips
                    val availableCats = categories.filter { it.type == type }
                    if (availableCats.isNotEmpty()) {
                        Text("Category", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableCats.forEach { cat ->
                                val isSelected = cat.id == selectedCategoryId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategoryId = cat.id },
                                    label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                // Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note (Optional)") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Recurring Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recurring Transaction", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
                }

                if (isRecurring) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isFrequencyDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Frequency: ${frequency.label}", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isFrequencyDropdownOpen,
                            onDismissRequest = { isFrequencyDropdownOpen = false }
                        ) {
                            RecurringFrequency.values().filter { it != RecurringFrequency.NONE }.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq.label) },
                                    onClick = {
                                        frequency = freq
                                        isFrequencyDropdownOpen = false
                                    }
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
                    val amount = CurrencyUtils.parseCurrencyInput(amountValue).takeIf { it > 0 } ?: transaction.amount
                    val fee = CurrencyUtils.parseCurrencyInput(feeValue)
                    val totalCount = if (isRecurring) {
                        if (isUnlimitedRecurring) 0 else (recurringOccurrencesText.toIntOrNull() ?: 12)
                    } else 0

                    val updatedTx = transaction.copy(
                        type = type,
                        amount = amount,
                        accountId = selectedAccountId,
                        toAccountId = if (type == TransactionType.TRANSFER) targetAccountId else null,
                        transferFee = fee,
                        categoryId = if (type != TransactionType.TRANSFER) selectedCategoryId else null,
                        title = titleText.ifBlank { transaction.title },
                        note = noteText,
                        timestamp = selectedTimestamp,
                        isRecurring = isRecurring,
                        recurringFrequency = if (isRecurring) frequency else RecurringFrequency.NONE,
                        recurringTotalCount = totalCount
                    )
                    onSave(updatedTx)
                },
                modifier = Modifier.testTag("save_edit_tx_btn")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
