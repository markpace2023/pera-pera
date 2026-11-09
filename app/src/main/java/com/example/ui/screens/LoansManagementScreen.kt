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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import com.example.ui.components.IconUtils
import com.example.ui.components.ScreenHeader
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import com.example.util.ExchangeRateService
import com.example.util.LoanUtils
import com.example.util.UserPreferences
import kotlin.math.max

enum class LoanTabFilter {
    ALL,
    BORROWED,
    LENT,
    SETTLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansManagementScreen(
    accounts: List<Account>,
    categories: List<Category>,
    transactions: List<TransactionWithDetails>,
    onBack: () -> Unit,
    onOpenNewLoan: (type: TransactionType) -> Unit,
    onSavePaymentTransaction: (paymentTx: Transaction, shouldMarkSettled: Boolean, loanId: Long) -> Unit,
    onToggleLoanSettled: (Transaction) -> Unit,
    onDeleteTransaction: ((Transaction) -> Unit)? = null,
    primaryCurrency: String = "PHP",
    exchangeRates: Map<String, Double> = emptyMap(),
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(LoanTabFilter.ALL) }
    var activePaymentLoan by remember { mutableStateOf<Transaction?>(null) }
    var deletingTx by remember { mutableStateOf<Transaction?>(null) }
    val expandedHistoryLoanIds = remember { mutableStateOf(setOf<Long>()) }

    // All base loan transactions
    val loanTransactions = remember(transactions) {
        transactions.filter { it.transaction.isLoan }
    }

    // Map of loan id to its payments/collections recorded in ledger
    val paymentsByLoanId = remember(transactions) {
        transactions.filter { it.transaction.isLoanPayment && it.transaction.loanReferenceId != null }
            .groupBy { it.transaction.loanReferenceId!! }
    }

    // Totals calculations (incorporating interest in total payable/collectable and converting to primaryCurrency)
    val borrowedList = remember(loanTransactions) {
        loanTransactions.filter { it.transaction.type == TransactionType.INCOME }
    }
    val lentList = remember(loanTransactions) {
        loanTransactions.filter { it.transaction.type == TransactionType.EXPENSE }
    }

    var totalBorrowedConverted = 0.0
    var totalRepaidConverted = 0.0
    val activeBorrowedCurrencies = mutableSetOf<String>()
    borrowedList.forEach { item ->
        val loanCurr = item.accountCurrency
        val totalPayable = LoanUtils.calculateTotalPayable(item.transaction)
        val paid = paymentsByLoanId[item.transaction.id]?.sumOf { it.transaction.amount } ?: 0.0
        val rem = max(0.0, totalPayable - paid)
        if (rem > 0.01 && !item.transaction.isLoanSettled) {
            activeBorrowedCurrencies.add(loanCurr)
        }
        totalBorrowedConverted += ExchangeRateService.convert(totalPayable, loanCurr, primaryCurrency, exchangeRates)
        totalRepaidConverted += ExchangeRateService.convert(paid, loanCurr, primaryCurrency, exchangeRates)
    }
    val remainingToPay = max(0.0, totalBorrowedConverted - totalRepaidConverted)

    var totalLentConverted = 0.0
    var totalCollectedConverted = 0.0
    val activeLentCurrencies = mutableSetOf<String>()
    lentList.forEach { item ->
        val loanCurr = item.accountCurrency
        val totalCollectable = LoanUtils.calculateTotalPayable(item.transaction)
        val collected = paymentsByLoanId[item.transaction.id]?.sumOf { it.transaction.amount } ?: 0.0
        val rem = max(0.0, totalCollectable - collected)
        if (rem > 0.01 && !item.transaction.isLoanSettled) {
            activeLentCurrencies.add(loanCurr)
        }
        totalLentConverted += ExchangeRateService.convert(totalCollectable, loanCurr, primaryCurrency, exchangeRates)
        totalCollectedConverted += ExchangeRateService.convert(collected, loanCurr, primaryCurrency, exchangeRates)
    }
    val remainingToCollect = max(0.0, totalLentConverted - totalCollectedConverted)

    // Filtered list
    val displayedLoans = remember(loanTransactions, selectedFilter) {
        when (selectedFilter) {
            LoanTabFilter.ALL -> loanTransactions
            LoanTabFilter.BORROWED -> loanTransactions.filter { it.transaction.type == TransactionType.INCOME && !it.transaction.isLoanSettled }
            LoanTabFilter.LENT -> loanTransactions.filter { it.transaction.type == TransactionType.EXPENSE && !it.transaction.isLoanSettled }
            LoanTabFilter.SETTLED -> loanTransactions.filter { it.transaction.isLoanSettled }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("loans_management_screen")
    ) {
        // Screen Header with Back button and Add Loan button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("loans_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Debts & Loans Hub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Payments & collections ledger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { onOpenNewLoan(TransactionType.INCOME) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("new_debt_loan_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Loan", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Summary Statistics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("loans_summary_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Borrowed Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Borrowed (You Owe)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = DateUtils.formatCurrency(remainingToPay, primaryCurrency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingToPay > 0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Paid: ${DateUtils.formatCurrency(totalRepaidConverted, primaryCurrency)} of ${DateUtils.formatCurrency(totalBorrowedConverted, primaryCurrency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Lent Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lent (Owed to You)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = DateUtils.formatCurrency(remainingToCollect, primaryCurrency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingToCollect > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Collected: ${DateUtils.formatCurrency(totalCollectedConverted, primaryCurrency)} of ${DateUtils.formatCurrency(totalLentConverted, primaryCurrency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == LoanTabFilter.ALL,
                onClick = { selectedFilter = LoanTabFilter.ALL },
                label = { Text("All (${loanTransactions.size})") },
                modifier = Modifier.testTag("loan_tab_all")
            )
            FilterChip(
                selected = selectedFilter == LoanTabFilter.BORROWED,
                onClick = { selectedFilter = LoanTabFilter.BORROWED },
                label = { Text("Borrowed (${borrowedList.count { !it.transaction.isLoanSettled }})") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.2f)),
                modifier = Modifier.testTag("loan_tab_borrowed")
            )
            FilterChip(
                selected = selectedFilter == LoanTabFilter.LENT,
                onClick = { selectedFilter = LoanTabFilter.LENT },
                label = { Text("Lent (${lentList.count { !it.transaction.isLoanSettled }})") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f)),
                modifier = Modifier.testTag("loan_tab_lent")
            )
            FilterChip(
                selected = selectedFilter == LoanTabFilter.SETTLED,
                onClick = { selectedFilter = LoanTabFilter.SETTLED },
                label = { Text("Settled (${loanTransactions.count { it.transaction.isLoanSettled }})") },
                modifier = Modifier.testTag("loan_tab_settled")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Loans List
        if (displayedLoans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Handshake,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedFilter == LoanTabFilter.ALL) "No debts or loans recorded yet" else "No matching entries found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When you borrow or lend money, you can record payments and collections here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onOpenNewLoan(TransactionType.INCOME) },
                            modifier = Modifier.testTag("empty_borrow_btn")
                        ) {
                            Text("Record Borrowed")
                        }
                        OutlinedButton(
                            onClick = { onOpenNewLoan(TransactionType.EXPENSE) },
                            modifier = Modifier.testTag("empty_lend_btn")
                        ) {
                            Text("Record Lent")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("loans_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedLoans, key = { it.transaction.id }) { item ->
                    val loan = item.transaction
                    val isBorrowing = loan.type == TransactionType.INCOME
                    val loanPayments = paymentsByLoanId[loan.id].orEmpty()
                    val totalPaidForLoan = loanPayments.sumOf { it.transaction.amount }
                    val loanInterest = LoanUtils.calculateTotalInterest(loan)
                    val totalPayableForLoan = LoanUtils.calculateTotalPayable(loan)
                    val remainingBalance = max(0.0, totalPayableForLoan - totalPaidForLoan)
                    val isFullySettled = LoanUtils.isFullySettled(loan, loanPayments.map { it.transaction })
                    val isHistoryExpanded = expandedHistoryLoanIds.value.contains(loan.id)

                    val themeColor = if (isBorrowing) Color(0xFFF59E0B) else Color(0xFF10B981)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_card_${loan.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Person, Loan Type Pill, Settled Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(themeColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isBorrowing) Icons.Default.Handshake else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = themeColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = loan.loanPerson.ifBlank { "Personal Loan" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val loanCurr = item.accountCurrency
                                            val currInfo = ExchangeRateService.getCurrencyInfo(loanCurr)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${currInfo.flag} $loanCurr",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isBorrowing) "Borrowed Debt" else "Money Lent Out",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = themeColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFullySettled) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (isFullySettled) "Fully Settled" else "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFullySettled) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                if (onDeleteTransaction != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { deletingTx = loan },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("delete_loan_${loan.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Loan",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Balance and Progress Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val loanCurr = item.accountCurrency
                                Column {
                                    Text(
                                        text = if (isBorrowing) "Remaining to Pay" else "Remaining to Collect",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = DateUtils.formatCurrency(remainingBalance, loanCurr),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (remainingBalance > 0) themeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (loanCurr != primaryCurrency && remainingBalance > 0) {
                                        val remConverted = ExchangeRateService.convert(remainingBalance, loanCurr, primaryCurrency, exchangeRates)
                                        Text(
                                            text = "≈ ${DateUtils.formatCurrency(remConverted, primaryCurrency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val loanCurr = item.accountCurrency
                                    if (loanInterest > 0) {
                                        Text(
                                            text = "Total Due: ${DateUtils.formatCurrency(totalPayableForLoan, loanCurr)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Principal: ${DateUtils.formatCurrency(loan.amount, loanCurr)} • +${DateUtils.formatCurrency(loanInterest, loanCurr)} int",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "Original: ${DateUtils.formatCurrency(loan.amount, loanCurr)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${if (isBorrowing) "Paid" else "Collected"}: ${DateUtils.formatCurrency(totalPaidForLoan, loanCurr)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val progress = if (totalPayableForLoan > 0) (totalPaidForLoan / totalPayableForLoan).toFloat().coerceIn(0f, 1f) else 1f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (isFullySettled) Color(0xFF10B981) else themeColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Loan details: Due Date, Interest, Account used
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val dueStr = if (loan.loanDueDate != null) "Due: ${DateUtils.formatDate(loan.loanDueDate)}" else "No due date"
                                Text(
                                    text = "📅 $dueStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (loan.hasInterest && loan.interestRate != null) {
                                    val loanCurr = item.accountCurrency
                                    Text(
                                        text = "Interest: ${LoanUtils.formatInterestSummary(loan, loanCurr)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = themeColor
                                    )
                                }
                            }

                            // Payment History Accordion Header
                            if (loanPayments.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable {
                                            val current = expandedHistoryLoanIds.value
                                            expandedHistoryLoanIds.value = if (current.contains(loan.id)) {
                                                current - loan.id
                                            } else {
                                                current + loan.id
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("loan_history_toggle_${loan.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Ledger Records (${loanPayments.size} ${if (isBorrowing) "payments" else "collections"})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (isHistoryExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        loanPayments.forEach { pItem ->
                                            val pTx = pItem.transaction
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = pTx.title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "${DateUtils.formatDate(pTx.timestamp)} • ${pItem.accountName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = DateUtils.formatCurrency(pTx.amount, pItem.accountCurrency),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isBorrowing) Color(0xFFEF4444) else Color(0xFF10B981)
                                                    )
                                                    if (onDeleteTransaction != null) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        IconButton(
                                                            onClick = { deletingTx = pTx },
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .testTag("delete_payment_${pTx.id}")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Delete payment record",
                                                                modifier = Modifier.size(15.dp),
                                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Record Payment / Collection Primary Button
                                Button(
                                    onClick = { activePaymentLoan = loan },
                                    enabled = !isFullySettled || remainingBalance > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("record_payment_btn_${loan.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isBorrowing) Icons.Default.Payments else Icons.Default.Savings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBorrowing) "Make Payment" else "Record Collection",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                // Mark Settle / Unsettle Toggle
                                OutlinedButton(
                                    onClick = { onToggleLoanSettled(loan) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toggle_settle_btn_${loan.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isFullySettled) Icons.Default.History else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isFullySettled) "Reopen" else "Settle",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }

    // Modal Sheet / Dialog to Record Payment or Collection into Ledger
    if (activePaymentLoan != null) {
        val loan = activePaymentLoan!!
        val isBorrowing = loan.type == TransactionType.INCOME
        val loanPayments = paymentsByLoanId[loan.id].orEmpty()
        val totalPaidForLoan = loanPayments.sumOf { it.transaction.amount }
        val remainingBalance = LoanUtils.calculateRemainingBalance(loan, loanPayments.map { it.transaction })
        val loanWithDetails = loanTransactions.find { it.transaction.id == loan.id }
        val loanCurrency = loanWithDetails?.accountCurrency ?: accounts.find { it.id == loan.accountId }?.currency ?: "PHP"

        RecordLoanPaymentDialog(
            loan = loan,
            isBorrowing = isBorrowing,
            remainingBalance = remainingBalance,
            loanCurrency = loanCurrency,
            primaryCurrency = primaryCurrency,
            exchangeRates = exchangeRates,
            accounts = accounts,
            categories = categories,
            onDismiss = { activePaymentLoan = null },
            onConfirm = { paymentAmount, selectedAccountId, note, dateTimestamp, markSettled ->
                val resolvedType = if (isBorrowing) TransactionType.EXPENSE else TransactionType.INCOME
                val targetCatName = if (isBorrowing) "Debt / Loan Payment" else "Loan Collection"
                val matchedCategory = categories.find { it.name.equals(targetCatName, ignoreCase = true) }
                    ?: categories.find { it.type == resolvedType }

                val paymentTitle = if (isBorrowing) {
                    "Debt Payment to ${loan.loanPerson.ifBlank { "Lender" }}"
                } else {
                    "Loan Collection from ${loan.loanPerson.ifBlank { "Borrower" }}"
                }

                val finalTx = Transaction(
                    type = resolvedType,
                    amount = paymentAmount,
                    accountId = selectedAccountId,
                    categoryId = matchedCategory?.id,
                    title = paymentTitle,
                    note = note.ifBlank { "Linked to loan for ${loan.loanPerson}" },
                    timestamp = dateTimestamp,
                    isLoanPayment = true,
                    loanReferenceId = loan.id,
                    loanPerson = loan.loanPerson
                )

                // Save last used account & category for MRU order
                UserPreferences.recordUsedAccount(context, selectedAccountId)
                matchedCategory?.let { UserPreferences.recordUsedCategory(context, resolvedType, it.id) }

                onSavePaymentTransaction(finalTx, markSettled, loan.id)
                activePaymentLoan = null
            }
        )
    }

    if (deletingTx != null) {
        val target = deletingTx!!
        val isPayment = target.isLoanPayment
        AlertDialog(
            onDismissRequest = { deletingTx = null },
            title = {
                Text(
                    text = if (isPayment) "Delete Payment Record?" else "Delete Loan Entry?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isPayment)
                        "This will permanently delete this payment transaction from your ledger and automatically revert your account balance."
                    else
                        "This will permanently delete this debt/loan transaction from your ledger and hub."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction?.invoke(target)
                        deletingTx = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("loan_delete_confirm_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingTx = null },
                    modifier = Modifier.testTag("loan_delete_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordLoanPaymentDialog(
    loan: Transaction,
    isBorrowing: Boolean,
    remainingBalance: Double,
    loanCurrency: String,
    primaryCurrency: String = "PHP",
    exchangeRates: Map<String, Double> = emptyMap(),
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, accountId: Long, note: String, timestamp: Long, markSettled: Boolean) -> Unit
) {
    val context = LocalContext.current
    val recentAccountIds = remember { UserPreferences.getRecentAccountIds(context) }
    val sortedAccounts = remember(accounts, recentAccountIds) {
        UserPreferences.sortAccountsByRecency(accounts, recentAccountIds)
    }

    var selectedAccountId by remember {
        mutableLongStateOf(sortedAccounts.firstOrNull()?.id ?: accounts.firstOrNull()?.id ?: 0L)
    }
    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val paymentCurrency = currentAccount?.currency ?: loanCurrency
    val currInfo = ExchangeRateService.getCurrencyInfo(paymentCurrency)

    val fullInPaymentCurrency = remember(remainingBalance, loanCurrency, paymentCurrency) {
        if (remainingBalance <= 0) 0.0
        else if (paymentCurrency == loanCurrency) remainingBalance
        else ExchangeRateService.convert(remainingBalance, loanCurrency, paymentCurrency, exchangeRates)
    }

    var amountValue by remember {
        mutableStateOf(if (fullInPaymentCurrency > 0) CurrencyUtils.toTextFieldValue(fullInPaymentCurrency) else TextFieldValue(""))
    }
    var isAccountDropdownOpen by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var paymentDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var autoMarkSettled by remember { mutableStateOf(true) }

    val dialogTitle = if (isBorrowing) "Record Payment (Debt Repayment)" else "Record Collection (Money Returned)"
    val actionLabel = if (isBorrowing) "Record Payment" else "Record Collection"
    val accountPrompt = if (isBorrowing) "Pay From Account *" else "Deposit Into Account *"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    val interest = LoanUtils.calculateTotalInterest(loan)
                    val totalPayable = LoanUtils.calculateTotalPayable(loan)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isBorrowing) "Debt to ${loan.loanPerson}" else "Loan to ${loan.loanPerson}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Remaining: ${DateUtils.formatCurrency(remainingBalance, loanCurrency)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (loanCurrency != primaryCurrency && remainingBalance > 0) {
                                    val remConverted = ExchangeRateService.convert(remainingBalance, loanCurrency, primaryCurrency, exchangeRates)
                                    Text(
                                        text = "≈ ${DateUtils.formatCurrency(remConverted, primaryCurrency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Due: ${DateUtils.formatCurrency(totalPayable, loanCurrency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (interest > 0) {
                                    Text(
                                        text = "Principal: ${DateUtils.formatCurrency(loan.amount, loanCurrency)} (+${DateUtils.formatCurrency(interest, loanCurrency)} int)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "Original: ${DateUtils.formatCurrency(loan.amount, loanCurrency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = CurrencyUtils.formatCurrencyInput(it, amountValue.text) },
                    label = { Text("Payment Amount *") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("${currInfo.symbol} ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_payment_amount_input")
                )

                // Quick Amount Shortcuts
                if (fullInPaymentCurrency > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = CurrencyUtils.parseCurrencyInput(amountValue) == CurrencyUtils.roundTo2Decimals(fullInPaymentCurrency),
                            onClick = { amountValue = CurrencyUtils.toTextFieldValue(fullInPaymentCurrency) },
                            label = { Text("Full (${DateUtils.formatCurrency(fullInPaymentCurrency, paymentCurrency)})", style = MaterialTheme.typography.labelSmall) }
                        )
                        if (fullInPaymentCurrency >= 10) {
                            val half = fullInPaymentCurrency / 2.0
                            FilterChip(
                                selected = CurrencyUtils.parseCurrencyInput(amountValue) == CurrencyUtils.roundTo2Decimals(half),
                                onClick = { amountValue = CurrencyUtils.toTextFieldValue(half) },
                                label = { Text("Half (${DateUtils.formatCurrency(half, paymentCurrency)})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Account Selector (Last used is first!)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isAccountDropdownOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_payment_account_btn")
                    ) {
                        Icon(IconUtils.getIcon(currentAccount?.iconName), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                            Text(accountPrompt, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${currentAccount?.name ?: "Select"} (${DateUtils.formatCurrency(currentAccount?.balance ?: 0.0, currentAccount?.currency ?: "PHP")})",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = isAccountDropdownOpen,
                        onDismissRequest = { isAccountDropdownOpen = false }
                    ) {
                        sortedAccounts.forEach { acc ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(IconUtils.getIcon(acc.iconName), contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                text = {
                                    val isLastUsed = acc.id == recentAccountIds.firstOrNull()
                                    Text("${acc.name} (${DateUtils.formatCurrency(acc.balance, acc.currency)})${if (isLastUsed) " • Last Used" else ""}")
                                },
                                onClick = {
                                    selectedAccountId = acc.id
                                    isAccountDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                if (paymentCurrency != loanCurrency) {
                    val enteredAmt = CurrencyUtils.parseCurrencyInput(amountValue)
                    val eqInLoanCurr = ExchangeRateService.convert(enteredAmt, paymentCurrency, loanCurrency, exchangeRates)
                    Text(
                        text = "💱 Account currency ($paymentCurrency) differs from loan currency ($loanCurrency). ${DateUtils.formatCurrency(enteredAmt, paymentCurrency)} ≈ ${DateUtils.formatCurrency(eqInLoanCurr, loanCurrency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Date Picker Button
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_payment_date_btn")
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: ${DateUtils.formatDate(paymentDate)}", modifier = Modifier.weight(1f))
                }

                // Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Memo (Optional)") },
                    placeholder = { Text("e.g. Cash payment, GCash reference") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_payment_note_input")
                )

                // Ledger Sync Info text
                Text(
                    text = "✓ This payment will automatically create a transaction in your ledger and adjust your account balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = CurrencyUtils.parseCurrencyInput(amountValue)
                    if (amt > 0 && selectedAccountId != 0L) {
                        val amtInLoanCurrency = if (paymentCurrency == loanCurrency) amt else ExchangeRateService.convert(amt, paymentCurrency, loanCurrency, exchangeRates)
                        val willBeFullySettled = (remainingBalance - amtInLoanCurrency) <= 0.05
                        onConfirm(amt, selectedAccountId, noteText.trim(), paymentDate, willBeFullySettled)
                    }
                },
                enabled = CurrencyUtils.parseCurrencyInput(amountValue) > 0,
                modifier = Modifier.testTag("loan_payment_confirm_btn")
            ) {
                Text(actionLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("loan_payment_cancel_btn")
            ) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = paymentDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { paymentDate = it }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
