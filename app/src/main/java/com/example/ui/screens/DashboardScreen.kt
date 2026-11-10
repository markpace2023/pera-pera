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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import com.example.ui.components.CustomDateRangePickerDialog
import com.example.ui.components.IconUtils
import com.example.ui.components.QuickTransactionWidget
import com.example.ui.components.ScreenHeader
import com.example.ui.components.StatementDateInfoCard
import com.example.util.DateUtils
import com.example.util.LoanUtils
import java.util.Calendar

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake

import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import com.example.ui.components.TransactionDetailDialog
import com.example.util.Budget503020Service
import com.example.util.ExchangeRateService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    accounts: List<Account>,
    categories: List<Category>,
    transactions: List<TransactionWithDetails>,
    onSaveTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onOpenAddTransactionSheet: (
        accountId: Long?,
        type: TransactionType,
        amount: String,
        title: String,
        categoryId: Long?
    ) -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransfer: () -> Unit,
    onNavigateToLoansManagement: () -> Unit = {},
    onTestNotification: (String) -> Unit,
    onToggleLoanSettled: ((Transaction) -> Unit)? = null,
    isPrivacyMode: Boolean = false,
    onTogglePrivacyMode: (() -> Unit)? = null,
    primaryCurrency: String = "PHP",
    exchangeRates: Map<String, Double> = emptyMap(),
    onRefreshExchangeRates: () -> Unit = {},
    onSaveCategory: (suspend (Category) -> Long)? = null
) {
    var selectedTimeframe by remember { mutableStateOf(DateUtils.Timeframe.MONTHLY) }
    var customStartDate by remember { mutableStateOf(System.currentTimeMillis() - 30L * 24 * 3600 * 1000) }
    var customEndDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var isNetWorthExtended by remember { mutableStateOf(false) }
    var viewingTransaction by remember { mutableStateOf<TransactionWithDetails?>(null) }

    val (startTime, endTime) = remember(selectedTimeframe, customStartDate, customEndDate) {
        DateUtils.getTimeframeRange(
            timeframe = selectedTimeframe,
            customStart = customStartDate,
            customEnd = customEndDate
        )
    }

    val filteredTransactions = remember(transactions, startTime, endTime) {
        transactions.filter { it.transaction.timestamp in startTime..endTime }
    }

    val totalIncome = remember(filteredTransactions) {
        filteredTransactions.filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amount }
    }

    val totalExpense = remember(filteredTransactions) {
        filteredTransactions.filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amount }
    }

    val netCashflow = totalIncome - totalExpense

    val distinctAccountCurrencies = remember(accounts) {
        accounts.map { it.currency.uppercase().trim().ifBlank { primaryCurrency } }.distinct()
    }

    val hasMultipleCurrencies = remember(distinctAccountCurrencies, primaryCurrency) {
        distinctAccountCurrencies.size > 1 || (distinctAccountCurrencies.isNotEmpty() && distinctAccountCurrencies.first() != primaryCurrency.uppercase())
    }

    val totalAssets = remember(accounts, primaryCurrency, exchangeRates) {
        accounts.filter { it.type != AccountType.CREDIT_CARD }.sumOf { acc ->
            ExchangeRateService.convert(acc.balance, acc.currency, primaryCurrency, exchangeRates)
        }
    }

    val totalCreditDebt = remember(accounts, primaryCurrency, exchangeRates) {
        accounts.filter { it.type == AccountType.CREDIT_CARD }.sumOf { acc ->
            ExchangeRateService.convert(acc.balance, acc.currency, primaryCurrency, exchangeRates)
        }
    }

    val netWorth = totalAssets - totalCreditDebt

    // Find urgent credit cards (statement cutoff within 5 days)
    val creditCardAccounts = remember(accounts) {
        accounts.filter { it.type == AccountType.CREDIT_CARD }
    }

    val allLoanTransactions = remember(transactions) {
        transactions.filter { it.transaction.isLoan }
    }

    val loanPaymentsByLoanId = remember(transactions) {
        transactions.filter { it.transaction.isLoanPayment && it.transaction.loanReferenceId != null }
            .groupBy { it.transaction.loanReferenceId!! }
    }

    val pendingBorrowedTotal = remember(allLoanTransactions, loanPaymentsByLoanId, primaryCurrency, exchangeRates) {
        allLoanTransactions.filter { it.transaction.type == TransactionType.INCOME && !it.transaction.isLoanSettled }
            .sumOf { item ->
                val payments = loanPaymentsByLoanId[item.transaction.id]?.map { it.transaction }.orEmpty()
                val remNative = LoanUtils.calculateRemainingBalance(item.transaction, payments)
                ExchangeRateService.convert(remNative, item.accountCurrency, primaryCurrency, exchangeRates)
            }
    }

    val pendingLentTotal = remember(allLoanTransactions, loanPaymentsByLoanId, primaryCurrency, exchangeRates) {
        allLoanTransactions.filter { it.transaction.type == TransactionType.EXPENSE && !it.transaction.isLoanSettled }
            .sumOf { item ->
                val payments = loanPaymentsByLoanId[item.transaction.id]?.map { it.transaction }.orEmpty()
                val remNative = LoanUtils.calculateRemainingBalance(item.transaction, payments)
                ExchangeRateService.convert(remNative, item.accountCurrency, primaryCurrency, exchangeRates)
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            ScreenHeader(
                title = "SpendFlow",
                subtitle = "Smart Income & Expense Tracker",
                titleColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
                action = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onTogglePrivacyMode != null) {
                            IconButton(
                                onClick = onTogglePrivacyMode,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("top_privacy_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPrivacyMode) "Show Balances" else "Hide Balances",
                                    tint = if (isPrivacyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = onNavigateToTransfer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("top_transfer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Transfer",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            )
        }

        // Net Worth Hero Card (Multi-Currency Support)
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B),
                                Color(0xFF0F766E)
                            )
                        )
                    )
                    .animateContentSize()
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TOTAL NET WORTH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            if (hasMultipleCurrencies) {
                                Text(
                                    text = "Converted to $primaryCurrency (Live Rates)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2DD4BF)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasMultipleCurrencies) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF06B6D4).copy(alpha = 0.25f),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "${distinctAccountCurrencies.size} Currencies",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${accounts.size} Accounts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(netWorth, primaryCurrency),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Assets & Cash",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(totalAssets, primaryCurrency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = if (totalCreditDebt < 0) "Credit Overpaid" else "Credit Used",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(totalCreditDebt, primaryCurrency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (totalCreditDebt < 0) Color(0xFF10B981) else Color(0xFFF87171),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Collapsed vs Opened (Extended) Multi-Currency View
                    if (!isNetWorthExtended) {
                        // Collapsed view button to extend card and show other currency values
                        if (accounts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            FilledTonalButton(
                                onClick = { isNetWorthExtended = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("extend_net_worth_card_btn"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (hasMultipleCurrencies) "View Breakdown by Currency (${distinctAccountCurrencies.size})" else "View Currency Details",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        // Opened View: Show breakdown separately by currency
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CURRENCY BREAKDOWN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "Live Internet Rates",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2DD4BF)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val accountsByCurrency = remember(accounts) {
                            accounts.groupBy { it.currency.uppercase().trim().ifBlank { primaryCurrency } }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            accountsByCurrency.forEach { (curr, currAccounts) ->
                                val cInfo = ExchangeRateService.getCurrencyInfo(curr)
                                val origAssets = currAccounts.filter { it.type != AccountType.CREDIT_CARD }.sumOf { it.balance }
                                val origDebt = currAccounts.filter { it.type == AccountType.CREDIT_CARD }.sumOf { it.balance }
                                val origNet = origAssets - origDebt
                                val isOtherCurrency = !curr.equals(primaryCurrency, ignoreCase = true)
                                val convertedSubNet = if (isOtherCurrency) {
                                    ExchangeRateService.convert(origNet, curr, primaryCurrency, exchangeRates)
                                } else origNet

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(cInfo.flag, style = MaterialTheme.typography.titleMedium)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = "$curr • ${currAccounts.size} account${if (currAccounts.size > 1) "s" else ""}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = cInfo.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(origNet, curr),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (origNet >= 0) Color(0xFF10B981) else Color(0xFFF87171)
                                                )
                                                if (isOtherCurrency && !isPrivacyMode) {
                                                    Text(
                                                        text = "≈ ${DateUtils.formatCurrency(convertedSubNet, primaryCurrency)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFCBD5E1),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }

                                        if (isOtherCurrency) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = ExchangeRateService.formatExchangeRate(curr, primaryCurrency, exchangeRates),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF2DD4BF)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Assets: ${if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(origAssets, curr)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF94A3B8)
                                            )
                                            if (origDebt > 0) {
                                                Text(
                                                    text = "Debt: ${if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(origDebt, curr)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFF87171)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Collapse Button to return to collapsed view
                        FilledTonalButton(
                            onClick = { isNetWorthExtended = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collapse_net_worth_card_btn"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Collapse Currency Breakdown",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // In-App Quick Transaction Widget or Fresh State Empty Accounts Card
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (accounts.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAccounts() }
                            .testTag("dashboard_empty_accounts_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No Accounts Added",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Start fresh by creating an account (Cash, Savings, Bank, Card) to track your funds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    QuickTransactionWidget(
                        accounts = accounts,
                        categories = categories,
                        recentTransactions = transactions,
                        onSaveQuickTransaction = onSaveTransaction,
                        onOpenFullSheet = { accId, t, amt, title, catId ->
                            onOpenAddTransactionSheet(accId, t, amt, title, catId)
                        },
                        isPrivacyMode = isPrivacyMode,
                        onSaveCategory = onSaveCategory
                    )
                }
            }
        }

        // Timeframe Selector & Period Breakdown
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cash Flow Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (selectedTimeframe == DateUtils.Timeframe.CUSTOM) {
                        Surface(
                            onClick = { showCustomDateDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.testTag("date_range_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    DateUtils.formatDate(startTime) + " – " + DateUtils.formatDate(endTime),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timeframe Chips: Daily, Weekly, Monthly, Yearly, Custom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateUtils.Timeframe.values().forEach { tf ->
                        val isSelected = selectedTimeframe == tf
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTimeframe = tf
                                if (tf == DateUtils.Timeframe.CUSTOM) {
                                    showCustomDateDialog = true
                                }
                            },
                            label = { Text(tf.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("timeframe_chip_${tf.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Income / Expense / Net metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Income
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_income_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(totalIncome),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Expense
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_expense_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Expenses",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(totalExpense),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Net Flow
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_netflow_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        val netColor = if (netCashflow >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(netColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Savings,
                                        contentDescription = null,
                                        tint = netColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Savings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(netCashflow),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = netColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 50/30/20 Auto-Adjusting Budget Snapshot Card
        item {
            val monthly503020 = remember(accounts, categories, transactions, primaryCurrency, exchangeRates) {
                Budget503020Service.calculateMonthlyBudget(
                    accounts = accounts,
                    categories = categories,
                    transactions = transactions,
                    monthCalendar = Calendar.getInstance(),
                    primaryCurrency = primaryCurrency,
                    exchangeRates = exchangeRates
                )
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_503020_budget_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "50/30/20 Monthly Budget",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Auto-adjusts as monthly income arrives",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (isPrivacyMode) "••••" else DateUtils.formatCurrency(monthly503020.totalMonthlyIncome),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        DashboardBudgetBarRow(
                            label = "Needs (50%)",
                            allocated = monthly503020.needsStatus.allocatedLimit,
                            used = monthly503020.needsStatus.usedAmount,
                            remaining = monthly503020.needsStatus.remainingAmount,
                            fraction = monthly503020.needsStatus.progressFraction,
                            percent = monthly503020.needsStatus.usagePercentage.toInt(),
                            isOver = monthly503020.needsStatus.isOverBudget,
                            barColor = Color(0xFF3B82F6),
                            isPrivacyMode = isPrivacyMode
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        DashboardBudgetBarRow(
                            label = "Wants (30%)",
                            allocated = monthly503020.wantsStatus.allocatedLimit,
                            used = monthly503020.wantsStatus.usedAmount,
                            remaining = monthly503020.wantsStatus.remainingAmount,
                            fraction = monthly503020.wantsStatus.progressFraction,
                            percent = monthly503020.wantsStatus.usagePercentage.toInt(),
                            isOver = monthly503020.wantsStatus.isOverBudget,
                            barColor = Color(0xFF8B5CF6),
                            isPrivacyMode = isPrivacyMode
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        DashboardBudgetBarRow(
                            label = "Savings & Debt (20%)",
                            allocated = monthly503020.savingsDebtStatus.allocatedLimit,
                            used = monthly503020.savingsDebtStatus.usedAmount,
                            remaining = monthly503020.savingsDebtStatus.remainingAmount,
                            fraction = monthly503020.savingsDebtStatus.progressFraction,
                            percent = monthly503020.savingsDebtStatus.usagePercentage.toInt(),
                            isOver = monthly503020.savingsDebtStatus.isOverBudget,
                            barColor = Color(0xFF10B981),
                            isPrivacyMode = isPrivacyMode
                        )
                    }
                }
            }
        }

        // Credit Card Cutoff Reminders (if any credit cards configured)
        if (creditCardAccounts.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Credit Card Statement Cycles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onNavigateToAccounts() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    creditCardAccounts.forEach { card ->
                        StatementDateInfoCard(
                            account = card,
                            onTestCutoffAlert = {
                                onTestNotification(card.name)
                            },
                            isPrivacyMode = isPrivacyMode,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }

        // Debts & Loans Section (Borrowing & Lending)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Handshake,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Debts & Loans",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onNavigateToLoansManagement,
                        modifier = Modifier.testTag("dashboard_manage_loans_btn")
                    ) {
                        Text("Manage Hub ›", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (allLoanTransactions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_loans_summary_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "You Owe (Borrowed)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(pendingBorrowedTotal, primaryCurrency),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pendingBorrowedTotal > 0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Owed to You (Lent)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(pendingLentTotal, primaryCurrency),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pendingLentTotal > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            val pendingLoans = allLoanTransactions.filter { !it.transaction.isLoanSettled }
                            if (pendingLoans.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Pending Repayments (${pendingLoans.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                pendingLoans.take(3).forEach { loanItem ->
                                    val ltx = loanItem.transaction
                                    val loanCurrency = loanItem.accountCurrency
                                    val payments = loanPaymentsByLoanId[ltx.id]?.map { it.transaction }.orEmpty()
                                    val remainingBal = LoanUtils.calculateRemainingBalance(ltx, payments)
                                    val totalInterest = LoanUtils.calculateTotalInterest(ltx)
                                    val nativeFormatted = DateUtils.formatCurrency(remainingBal, loanCurrency)
                                    val amountDesc = if (isPrivacyMode) {
                                        "••••••"
                                    } else if (loanCurrency != primaryCurrency) {
                                        val converted = ExchangeRateService.convert(remainingBal, loanCurrency, primaryCurrency, exchangeRates)
                                        "$nativeFormatted (≈ ${DateUtils.formatCurrency(converted, primaryCurrency)})"
                                    } else if (totalInterest > 0) {
                                        "$nativeFormatted (incl. +${DateUtils.formatCurrency(totalInterest, loanCurrency)} int)"
                                    } else {
                                        nativeFormatted
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (ltx.type == TransactionType.INCOME) "Borrowed from ${ltx.loanPerson.ifBlank { "Someone" }}" else "Lent to ${ltx.loanPerson.ifBlank { "Someone" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val dueStr = if (ltx.loanDueDate != null) "Due: ${DateUtils.formatDate(ltx.loanDueDate)}" else "No due date"
                                            Text(
                                                text = "$dueStr • $amountDesc",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        TextButton(
                                            onClick = { onToggleLoanSettled?.invoke(ltx) },
                                            modifier = Modifier.testTag("quick_settle_btn_${ltx.id}")
                                        ) {
                                            Text("Settle ✓", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNavigateToLoansManagement,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dashboard_open_loans_hub_btn")
                            ) {
                                Text("Payments & Collections Ledger", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToLoansManagement() }
                            .testTag("dashboard_empty_loans_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Track Borrowing & Lending", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Record debts, due dates, payments, and collections linked to your ledger.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Recent Transactions Header & List
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions (${filteredTransactions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                /* TextButton(
                    onClick = { onOpenAddTransactionSheet(null, TransactionType.EXPENSE, "", "", null) },
                    modifier = Modifier.testTag("dashboard_add_tx_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Entry")
                } */
            }
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transactions in this period",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use the quick transaction widget above to add one!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            items(filteredTransactions.take(15), key = { it.transaction.id }) { item ->
                val tx = item.transaction
                var showDeleteConfirm by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { viewingTransaction = item }
                        .testTag("tx_item_${tx.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // Category / Type icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when (tx.type) {
                                            TransactionType.EXPENSE -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                            TransactionType.INCOME -> Color(0xFF10B981).copy(alpha = 0.15f)
                                            TransactionType.TRANSFER -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                IconUtils.AccountIconView(
                                    iconName = item.categoryIcon ?: item.accountIcon,
                                    contentDescription = null,
                                    tint = when (tx.type) {
                                        TransactionType.EXPENSE -> Color(0xFFEF4444)
                                        TransactionType.INCOME -> Color(0xFF10B981)
                                        TransactionType.TRANSFER -> Color(0xFF3B82F6)
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tx.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    if (tx.isRecurring) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Repeat,
                                            contentDescription = "Recurring",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
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

                                Text(
                                    text = DateUtils.formatDate(tx.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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

                                if (tx.isRecurring && tx.recurringTotalCount > 0) {
                                    Text(
                                        text = "${tx.recurringExecutedCount}/${tx.recurringTotalCount} done",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.size(32.dp)
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
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete Transaction?") },
                        text = { Text("This will revert the balance on ${item.accountName} and cannot be undone.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onDeleteTransaction(tx)
                                    showDeleteConfirm = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
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
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Custom Date Range Dialog
    if (showCustomDateDialog) {
        CustomDateRangePickerDialog(
            initialStartDate = customStartDate,
            initialEndDate = customEndDate,
            onDateRangeSelected = { start, end ->
                customStartDate = start
                customEndDate = end
                showCustomDateDialog = false
            },
            onDismissRequest = { showCustomDateDialog = false }
        )
    }

    // Transaction Details Dialog
    viewingTransaction?.let { txDetails ->
        TransactionDetailDialog(
            item = txDetails,
            primaryCurrency = primaryCurrency,
            exchangeRates = exchangeRates,
            isPrivacyMode = isPrivacyMode,
            onDismiss = { viewingTransaction = null }
        )
    }
}

@Composable
fun DashboardBudgetBarRow(
    label: String,
    allocated: Double,
    used: Double,
    remaining: Double,
    fraction: Float,
    percent: Int,
    isOver: Boolean,
    barColor: Color,
    isPrivacyMode: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = barColor
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isPrivacyMode) "•••• / ••••" else "${DateUtils.formatCurrency(used)} / ${DateUtils.formatCurrency(allocated)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isOver) MaterialTheme.colorScheme.errorContainer else barColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOver) MaterialTheme.colorScheme.error else barColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isOver) MaterialTheme.colorScheme.error else barColor,
            trackColor = barColor.copy(alpha = 0.15f)
        )
    }
}

