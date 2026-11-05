package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.SpendFlowApplication
import com.example.data.model.Account
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.ui.components.QuickAddTransactionSheet
import com.example.widget.QuickTransactionWidgetProvider
import kotlinx.coroutines.launch

enum class ScreenTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    ACCOUNTS("Accounts", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance),
    TRANSACTIONS("Ledger", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    CATEGORIES("Budgets", Icons.Filled.Category, Icons.Outlined.Category),
    REPORTS("Reports", Icons.Filled.Insights, Icons.Outlined.Insights),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainScreen(
    app: SpendFlowApplication,
    initialQuickActionType: String? = null
) {
    val repository = app.repository
    val biometricHelper = app.biometricHelper
    val notificationHelper = app.notificationHelper
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val accounts by repository.allAccounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by repository.allTransactionsWithDetails.collectAsStateWithLifecycle(initialValue = emptyList())
    val budgets by repository.allBudgets.collectAsStateWithLifecycle(initialValue = emptyList())

    var currentTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }
    var showTransferScreen by remember { mutableStateOf(false) }
    var showLoansManagementScreen by remember { mutableStateOf(false) }
    var transferInitialFromAccountId by remember { mutableStateOf<Long?>(null) }
    var transferInitialToAccountId by remember { mutableStateOf<Long?>(null) }

    // Quick add bottom sheet state
    var showQuickAddSheet by remember { mutableStateOf(false) }
    var quickAddInitialAccountId by remember { mutableStateOf<Long?>(null) }
    var quickAddInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var quickAddInitialAmount by remember { mutableStateOf("") }
    var quickAddInitialTitle by remember { mutableStateOf("") }
    var quickAddInitialCategoryId by remember { mutableStateOf<Long?>(null) }
    var quickAddInitialIsLoan by remember { mutableStateOf(false) }

    // Privacy Mode (masks balances & amounts throughout the app)
    var isPrivacyMode by remember { mutableStateOf(false) }

    // Primary currency and live exchange rates from internet
    var primaryCurrency by remember {
        mutableStateOf(com.example.util.UserPreferences.getPrimaryCurrency(app))
    }
    var exchangeRates by remember {
        mutableStateOf(com.example.util.ExchangeRateService.getRates(app))
    }

    LaunchedEffect(primaryCurrency) {
        com.example.util.ExchangeRateService.fetchRatesFromInternet(app).onSuccess { freshRates ->
            exchangeRates = freshRates
        }
    }

    // Biometric lock status (if enabled, starts locked until authenticated)
    var isAppUnlocked by remember {
        mutableStateOf(!biometricHelper.isBiometricEnabled)
    }

    // Handle incoming intent action (from AppWidget or shortcut)
    LaunchedEffect(initialQuickActionType) {
        if (!initialQuickActionType.isNullOrBlank()) {
            quickAddInitialType = if (initialQuickActionType.equals("INCOME", ignoreCase = true)) {
                TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            }
            quickAddInitialAmount = ""
            quickAddInitialTitle = ""
            quickAddInitialCategoryId = null
            quickAddInitialIsLoan = false
            showQuickAddSheet = true
        }
    }

    BackHandler(enabled = showLoansManagementScreen || showTransferScreen || currentTab != ScreenTab.DASHBOARD) {
        if (showLoansManagementScreen) {
            showLoansManagementScreen = false
        } else if (showTransferScreen) {
            showTransferScreen = false
        } else {
            currentTab = ScreenTab.DASHBOARD
        }
    }

    if (!isAppUnlocked && biometricHelper.isBiometricEnabled) {
        AuthLockScreen(
            biometricHelper = biometricHelper,
            onAuthenticated = {
                isAppUnlocked = true
            },
            onSkipLoginForQuickRecord = {
                isPrivacyMode = true
                isAppUnlocked = true
            }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // Tablet layout with NavigationRail on left and Content on right
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.testTag("tablet_navigation_rail")
                ) {
                    ScreenTab.values().forEach { tab ->
                        val selected = currentTab == tab && !showTransferScreen && !showLoansManagementScreen
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                showTransferScreen = false
                                showLoansManagementScreen = false
                                currentTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            modifier = Modifier.testTag("nav_rail_${tab.name}")
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    ScreenContentWithTransition(
                        currentTab = currentTab,
                        showTransferScreen = showTransferScreen,
                        showLoansManagementScreen = showLoansManagementScreen,
                        transferInitialFromAccountId = transferInitialFromAccountId,
                        transferInitialToAccountId = transferInitialToAccountId,
                        accounts = accounts,
                        categories = categories,
                        transactions = transactions,
                        budgets = budgets,
                        biometricHelper = biometricHelper,
                        notificationHelper = notificationHelper,
                        onSaveTransaction = { tx ->
                            coroutineScope.launch {
                                repository.addTransaction(tx)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transaction recorded")
                            }
                        },
                        onEditTransaction = { oldTx, newTx ->
                            coroutineScope.launch {
                                repository.editTransaction(oldTx, newTx)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transaction updated")
                            }
                        },
                        onDeleteTransaction = { tx ->
                            coroutineScope.launch {
                                repository.deleteTransaction(tx)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transaction deleted")
                            }
                        },
                        onSaveAccount = { acc ->
                            coroutineScope.launch {
                                repository.saveAccount(acc)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                            }
                        },
                        onDeleteAccount = { acc ->
                            coroutineScope.launch {
                                repository.deleteAccount(acc)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                            }
                        },
                        onSaveCategory = { cat ->
                            coroutineScope.launch { repository.saveCategory(cat) }
                        },
                        onDeleteCategory = { cat, targetCatId ->
                            coroutineScope.launch {
                                val movedCount = repository.deleteCategory(cat, targetCatId)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                if (targetCatId != null && movedCount > 0) {
                                    val targetName = categories.find { it.id == targetCatId }?.name ?: "another category"
                                    snackbarHostState.showSnackbar("Moved $movedCount transaction(s) to $targetName and deleted ${cat.name}")
                                } else {
                                    snackbarHostState.showSnackbar("Deleted category ${cat.name}")
                                }
                            }
                        },
                        onSaveBudget = { b ->
                            coroutineScope.launch { repository.saveBudget(b) }
                        },
                        onOpenAddTransactionSheet = { accId, t, amt, title, catId, isLoan ->
                            quickAddInitialAccountId = accId
                            quickAddInitialType = t
                            quickAddInitialAmount = amt
                            quickAddInitialTitle = title
                            quickAddInitialCategoryId = catId
                            quickAddInitialIsLoan = isLoan
                            showQuickAddSheet = true
                        },
                        isPrivacyMode = isPrivacyMode,
                        onTogglePrivacyMode = { isPrivacyMode = !isPrivacyMode },
                        onNavigateToAccounts = {
                            showTransferScreen = false
                            showLoansManagementScreen = false
                            currentTab = ScreenTab.ACCOUNTS
                        },
                        onNavigateToTransfer = { fromId, toId ->
                            transferInitialFromAccountId = fromId
                            transferInitialToAccountId = toId
                            showTransferScreen = true
                            showLoansManagementScreen = false
                        },
                        onBackFromTransfer = { showTransferScreen = false },
                        onNavigateToLoansManagement = {
                            showTransferScreen = false
                            showLoansManagementScreen = true
                        },
                        onBackFromLoansManagement = { showLoansManagementScreen = false },
                        onSavePaymentTransaction = { paymentTx, shouldMarkSettled, loanId ->
                            coroutineScope.launch {
                                repository.addTransaction(paymentTx)
                                if (shouldMarkSettled) {
                                    repository.markLoanSettled(loanId, true)
                                }
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                val actionWord = if (paymentTx.type == TransactionType.EXPENSE) "Payment" else "Collection"
                                snackbarHostState.showSnackbar("$actionWord recorded and ledger updated")
                            }
                        },
                        onExecuteTransfer = { fromId, toId, amt, fee, note ->
                            coroutineScope.launch {
                                repository.transferBetweenAccounts(fromId, toId, amt, fee, note)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transfer completed")
                            }
                        },
                        onLockAppNow = { isAppUnlocked = false },
                        onResetDatabase = {
                            coroutineScope.launch {
                                runCatching {
                                    repository.resetDatabaseToFreshState()
                                    com.example.util.UserPreferences.clearPreferences(app)
                                    QuickTransactionWidgetProvider.notifyUpdate(app)
                                    snackbarHostState.showSnackbar("Database reset to fresh state (0 accounts)")
                                }.onFailure { e ->
                                    snackbarHostState.showSnackbar("Reset failed: ${e.message}")
                                }
                            }
                        },
                        onExportCsv = { s, e -> repository.exportToCsv(s, e) },
                        onImportCsv = { csv ->
                            val count = repository.importFromCsv(csv)
                            QuickTransactionWidgetProvider.notifyUpdate(app)
                            count
                        },
                        onExportDatabaseBackup = { repository.exportFullDatabaseBackup() },
                        onRestoreDatabaseBackup = { json ->
                            val res = repository.restoreFullDatabaseBackup(json)
                            if (res is com.example.util.DatabaseRestoreResult.Success) {
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Restored ${res.accountsCount} accounts and ${res.transactionsCount} transactions")
                            }
                            res
                        },
                        onToggleLoanSettled = { tx ->
                            coroutineScope.launch {
                                repository.markLoanSettled(tx.id, !tx.isLoanSettled)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                val status = if (!tx.isLoanSettled) "marked as settled" else "marked as pending"
                                snackbarHostState.showSnackbar("Loan entry $status")
                            }
                        },
                        primaryCurrency = primaryCurrency,
                        exchangeRates = exchangeRates,
                        onPrimaryCurrencyChanged = { newCurr ->
                            primaryCurrency = newCurr
                            coroutineScope.launch {
                                com.example.util.ExchangeRateService.fetchRatesFromInternet(app, forceRefresh = true).onSuccess { exchangeRates = it }
                            }
                        },
                        onRefreshExchangeRates = {
                            com.example.util.ExchangeRateService.fetchRatesFromInternet(app, forceRefresh = true).onSuccess { exchangeRates = it }
                        }
                    )
                }
            }
        } else {
            // Phone layout with bottom NavigationBar
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!showTransferScreen && !showLoansManagementScreen) {
                        NavigationBar(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("bottom_nav_bar")
                        ) {
                            ScreenTab.values().forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        showTransferScreen = false
                                        showLoansManagementScreen = false
                                        currentTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = false,
                                    modifier = Modifier.testTag("nav_bar_${tab.name}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ScreenContentWithTransition(
                        currentTab = currentTab,
                        showTransferScreen = showTransferScreen,
                        showLoansManagementScreen = showLoansManagementScreen,
                        transferInitialFromAccountId = transferInitialFromAccountId,
                        transferInitialToAccountId = transferInitialToAccountId,
                        accounts = accounts,
                        categories = categories,
                        transactions = transactions,
                        budgets = budgets,
                        biometricHelper = biometricHelper,
                        notificationHelper = notificationHelper,
                        onSaveTransaction = { tx ->
                            coroutineScope.launch {
                                repository.addTransaction(tx)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transaction recorded")
                            }
                        },
                        onEditTransaction = { oldTx, newTx ->
                            coroutineScope.launch {
                                repository.editTransaction(oldTx, newTx)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transaction updated")
                            }
                        },
                        onDeleteTransaction = { tx ->
                            coroutineScope.launch {
                                repository.deleteTransaction(tx)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transaction deleted")
                            }
                        },
                        onSaveAccount = { acc ->
                            coroutineScope.launch {
                                repository.saveAccount(acc)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                            }
                        },
                        onDeleteAccount = { acc ->
                            coroutineScope.launch {
                                repository.deleteAccount(acc)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                            }
                        },
                        onSaveCategory = { cat ->
                            coroutineScope.launch { repository.saveCategory(cat) }
                        },
                        onDeleteCategory = { cat, targetCatId ->
                            coroutineScope.launch {
                                val movedCount = repository.deleteCategory(cat, targetCatId)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                if (targetCatId != null && movedCount > 0) {
                                    val targetName = categories.find { it.id == targetCatId }?.name ?: "another category"
                                    snackbarHostState.showSnackbar("Moved $movedCount transaction(s) to $targetName and deleted ${cat.name}")
                                } else {
                                    snackbarHostState.showSnackbar("Deleted category ${cat.name}")
                                }
                            }
                        },
                        onSaveBudget = { b ->
                            coroutineScope.launch { repository.saveBudget(b) }
                        },
                        onOpenAddTransactionSheet = { accId, t, amt, title, catId, isLoan ->
                            quickAddInitialAccountId = accId
                            quickAddInitialType = t
                            quickAddInitialAmount = amt
                            quickAddInitialTitle = title
                            quickAddInitialCategoryId = catId
                            quickAddInitialIsLoan = isLoan
                            showQuickAddSheet = true
                        },
                        isPrivacyMode = isPrivacyMode,
                        onTogglePrivacyMode = { isPrivacyMode = !isPrivacyMode },
                        onNavigateToAccounts = {
                            showTransferScreen = false
                            showLoansManagementScreen = false
                            currentTab = ScreenTab.ACCOUNTS
                        },
                        onNavigateToTransfer = { fromId, toId ->
                            transferInitialFromAccountId = fromId
                            transferInitialToAccountId = toId
                            showTransferScreen = true
                            showLoansManagementScreen = false
                        },
                        onBackFromTransfer = { showTransferScreen = false },
                        onNavigateToLoansManagement = {
                            showTransferScreen = false
                            showLoansManagementScreen = true
                        },
                        onBackFromLoansManagement = { showLoansManagementScreen = false },
                        onSavePaymentTransaction = { paymentTx, shouldMarkSettled, loanId ->
                            coroutineScope.launch {
                                repository.addTransaction(paymentTx)
                                if (shouldMarkSettled) {
                                    repository.markLoanSettled(loanId, true)
                                }
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                val actionWord = if (paymentTx.type == TransactionType.EXPENSE) "Payment" else "Collection"
                                snackbarHostState.showSnackbar("$actionWord recorded and ledger updated")
                            }
                        },
                        onExecuteTransfer = { fromId, toId, amt, fee, note ->
                            coroutineScope.launch {
                                repository.transferBetweenAccounts(fromId, toId, amt, fee, note)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Transfer completed")
                            }
                        },
                        onLockAppNow = { isAppUnlocked = false },
                        onResetDatabase = {
                            coroutineScope.launch {
                                runCatching {
                                    repository.resetDatabaseToFreshState()
                                    com.example.util.UserPreferences.clearPreferences(app)
                                    QuickTransactionWidgetProvider.notifyUpdate(app)
                                    snackbarHostState.showSnackbar("Database reset to fresh state (0 accounts)")
                                }.onFailure { e ->
                                    snackbarHostState.showSnackbar("Reset failed: ${e.message}")
                                }
                            }
                        },
                        onExportCsv = { s, e -> repository.exportToCsv(s, e) },
                        onImportCsv = { csv ->
                            val count = repository.importFromCsv(csv)
                            QuickTransactionWidgetProvider.notifyUpdate(app)
                            count
                        },
                        onExportDatabaseBackup = { repository.exportFullDatabaseBackup() },
                        onRestoreDatabaseBackup = { json ->
                            val res = repository.restoreFullDatabaseBackup(json)
                            if (res is com.example.util.DatabaseRestoreResult.Success) {
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                snackbarHostState.showSnackbar("Restored ${res.accountsCount} accounts and ${res.transactionsCount} transactions")
                            }
                            res
                        },
                        onToggleLoanSettled = { tx ->
                            coroutineScope.launch {
                                repository.markLoanSettled(tx.id, !tx.isLoanSettled)
                                QuickTransactionWidgetProvider.notifyUpdate(app)
                                val status = if (!tx.isLoanSettled) "marked as settled" else "marked as pending"
                                snackbarHostState.showSnackbar("Loan entry $status")
                            }
                        },
                        primaryCurrency = primaryCurrency,
                        exchangeRates = exchangeRates,
                        onPrimaryCurrencyChanged = { newCurr ->
                            primaryCurrency = newCurr
                            coroutineScope.launch {
                                com.example.util.ExchangeRateService.fetchRatesFromInternet(app, forceRefresh = true).onSuccess { exchangeRates = it }
                            }
                        },
                        onRefreshExchangeRates = {
                            com.example.util.ExchangeRateService.fetchRatesFromInternet(app, forceRefresh = true).onSuccess { exchangeRates = it }
                        }
                    )
                }
            }
        }
    }

    if (showQuickAddSheet) {
        QuickAddTransactionSheet(
            accounts = accounts,
            categories = categories,
            initialAccountId = quickAddInitialAccountId,
            initialType = quickAddInitialType,
            initialAmount = quickAddInitialAmount,
            initialTitle = quickAddInitialTitle,
            initialCategoryId = quickAddInitialCategoryId,
            initialIsLoan = quickAddInitialIsLoan,
            isPrivacyMode = isPrivacyMode,
            notificationHelper = notificationHelper,
            onSave = { tx ->
                quickAddInitialCategoryId = null
                coroutineScope.launch {
                    repository.addTransaction(tx)
                    QuickTransactionWidgetProvider.notifyUpdate(app)
                    snackbarHostState.showSnackbar("Transaction saved")
                }
            },
            onSaveCategory = { cat ->
                val newId = repository.saveCategory(cat)
                QuickTransactionWidgetProvider.notifyUpdate(app)
                snackbarHostState.showSnackbar("Category \"${cat.name}\" created")
                newId
            },
            onDismissRequest = {
                showQuickAddSheet = false
                quickAddInitialAmount = ""
                quickAddInitialTitle = ""
                quickAddInitialCategoryId = null
                quickAddInitialIsLoan = false
            }
        )
    }
}

@Composable
private fun ScreenContentWithTransition(
    currentTab: ScreenTab,
    showTransferScreen: Boolean,
    showLoansManagementScreen: Boolean,
    transferInitialFromAccountId: Long?,
    transferInitialToAccountId: Long?,
    accounts: List<Account>,
    categories: List<Category>,
    transactions: List<com.example.data.model.TransactionWithDetails>,
    budgets: List<Budget>,
    biometricHelper: com.example.util.BiometricHelper,
    notificationHelper: com.example.util.NotificationHelper,
    onSaveTransaction: (Transaction) -> Unit,
    onEditTransaction: (oldTx: Transaction, newTx: Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onSaveAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (Category, Long?) -> Unit,
    onSaveBudget: (Budget) -> Unit,
    onOpenAddTransactionSheet: (
        accountId: Long?,
        type: TransactionType,
        amount: String,
        title: String,
        categoryId: Long?,
        isLoan: Boolean
    ) -> Unit,
    isPrivacyMode: Boolean,
    onTogglePrivacyMode: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransfer: (fromAccountId: Long?, toAccountId: Long?) -> Unit,
    onBackFromTransfer: () -> Unit,
    onNavigateToLoansManagement: () -> Unit,
    onBackFromLoansManagement: () -> Unit,
    onSavePaymentTransaction: (paymentTx: Transaction, shouldMarkSettled: Boolean, loanId: Long) -> Unit,
    onExecuteTransfer: (fromId: Long, toId: Long, amount: Double, fee: Double, note: String) -> Unit,
    onLockAppNow: () -> Unit,
    onResetDatabase: () -> Unit,
    onExportCsv: suspend (startTime: Long?, endTime: Long?) -> String,
    onImportCsv: suspend (csvContent: String) -> Int,
    onExportDatabaseBackup: suspend () -> String,
    onRestoreDatabaseBackup: suspend (String) -> com.example.util.DatabaseRestoreResult,
    onToggleLoanSettled: (Transaction) -> Unit,
    primaryCurrency: String = "PHP",
    exchangeRates: Map<String, Double> = emptyMap(),
    onPrimaryCurrencyChanged: (String) -> Unit = {},
    onRefreshExchangeRates: suspend () -> Unit = {}
) {
    AnimatedContent(
        targetState = Triple(currentTab, showTransferScreen, showLoansManagementScreen),
        transitionSpec = {
            val (initialTab, initialTransfer, initialLoans) = initialState
            val (targetTab, targetTransfer, targetLoans) = targetState

            val isForward = if (initialLoans != targetLoans) {
                targetLoans
            } else if (initialTransfer != targetTransfer) {
                targetTransfer
            } else {
                targetTab.ordinal >= initialTab.ordinal
            }

            if (isForward) {
                (slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> fullWidth }
                ) + fadeIn(animationSpec = tween(280)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> -fullWidth / 3 }
                        ) + fadeOut(animationSpec = tween(200))
                    )
            } else {
                (slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> -fullWidth / 3 }
                ) + fadeIn(animationSpec = tween(280)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> fullWidth }
                        ) + fadeOut(animationSpec = tween(200))
                    )
            }
        },
        label = "ScreenTransition"
    ) { (tab, isTransfer, isLoansHub) ->
        ContentArea(
            currentTab = tab,
            showTransferScreen = isTransfer,
            showLoansManagementScreen = isLoansHub,
            transferInitialFromAccountId = transferInitialFromAccountId,
            transferInitialToAccountId = transferInitialToAccountId,
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            budgets = budgets,
            biometricHelper = biometricHelper,
            notificationHelper = notificationHelper,
            onSaveTransaction = onSaveTransaction,
            onEditTransaction = onEditTransaction,
            onDeleteTransaction = onDeleteTransaction,
            onSaveAccount = onSaveAccount,
            onDeleteAccount = onDeleteAccount,
            onSaveCategory = onSaveCategory,
            onDeleteCategory = onDeleteCategory,
            onSaveBudget = onSaveBudget,
            onOpenAddTransactionSheet = onOpenAddTransactionSheet,
            isPrivacyMode = isPrivacyMode,
            onTogglePrivacyMode = onTogglePrivacyMode,
            onNavigateToAccounts = onNavigateToAccounts,
            onNavigateToTransfer = onNavigateToTransfer,
            onBackFromTransfer = onBackFromTransfer,
            onNavigateToLoansManagement = onNavigateToLoansManagement,
            onBackFromLoansManagement = onBackFromLoansManagement,
            onSavePaymentTransaction = onSavePaymentTransaction,
            onExecuteTransfer = onExecuteTransfer,
            onLockAppNow = onLockAppNow,
            onResetDatabase = onResetDatabase,
            onExportCsv = onExportCsv,
            onImportCsv = onImportCsv,
            onExportDatabaseBackup = onExportDatabaseBackup,
            onRestoreDatabaseBackup = onRestoreDatabaseBackup,
            onToggleLoanSettled = onToggleLoanSettled,
            primaryCurrency = primaryCurrency,
            exchangeRates = exchangeRates,
            onPrimaryCurrencyChanged = onPrimaryCurrencyChanged,
            onRefreshExchangeRates = onRefreshExchangeRates
        )
    }
}

@Composable
private fun ContentArea(
    currentTab: ScreenTab,
    showTransferScreen: Boolean,
    showLoansManagementScreen: Boolean,
    transferInitialFromAccountId: Long?,
    transferInitialToAccountId: Long?,
    accounts: List<Account>,
    categories: List<Category>,
    transactions: List<com.example.data.model.TransactionWithDetails>,
    budgets: List<Budget>,
    biometricHelper: com.example.util.BiometricHelper,
    notificationHelper: com.example.util.NotificationHelper,
    onSaveTransaction: (Transaction) -> Unit,
    onEditTransaction: (oldTx: Transaction, newTx: Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onSaveAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (Category, Long?) -> Unit,
    onSaveBudget: (Budget) -> Unit,
    onOpenAddTransactionSheet: (
        accountId: Long?,
        type: TransactionType,
        amount: String,
        title: String,
        categoryId: Long?,
        isLoan: Boolean
    ) -> Unit,
    isPrivacyMode: Boolean,
    onTogglePrivacyMode: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransfer: (fromAccountId: Long?, toAccountId: Long?) -> Unit,
    onBackFromTransfer: () -> Unit,
    onNavigateToLoansManagement: () -> Unit,
    onBackFromLoansManagement: () -> Unit,
    onSavePaymentTransaction: (paymentTx: Transaction, shouldMarkSettled: Boolean, loanId: Long) -> Unit,
    onExecuteTransfer: (fromId: Long, toId: Long, amount: Double, fee: Double, note: String) -> Unit,
    onLockAppNow: () -> Unit,
    onResetDatabase: () -> Unit,
    onExportCsv: suspend (startTime: Long?, endTime: Long?) -> String,
    onImportCsv: suspend (csvContent: String) -> Int,
    onExportDatabaseBackup: suspend () -> String,
    onRestoreDatabaseBackup: suspend (String) -> com.example.util.DatabaseRestoreResult,
    onToggleLoanSettled: (Transaction) -> Unit,
    primaryCurrency: String = "PHP",
    exchangeRates: Map<String, Double> = emptyMap(),
    onPrimaryCurrencyChanged: (String) -> Unit = {},
    onRefreshExchangeRates: suspend () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    if (showLoansManagementScreen) {
        LoansManagementScreen(
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            primaryCurrency = primaryCurrency,
            exchangeRates = exchangeRates,
            onBack = onBackFromLoansManagement,
            onOpenNewLoan = { loanType ->
                onOpenAddTransactionSheet(
                    null,
                    loanType,
                    "",
                    if (loanType == TransactionType.INCOME) "Borrowed Debt" else "Money Lent",
                    null,
                    true
                )
            },
            onSavePaymentTransaction = onSavePaymentTransaction,
            onToggleLoanSettled = onToggleLoanSettled,
            onDeleteTransaction = onDeleteTransaction
        )
        return
    }

    if (showTransferScreen) {
        TransferScreen(
            accounts = accounts,
            initialFromAccountId = transferInitialFromAccountId,
            initialToAccountId = transferInitialToAccountId,
            onExecuteTransfer = onExecuteTransfer,
            onBack = onBackFromTransfer
        )
        return
    }

    when (currentTab) {
        ScreenTab.DASHBOARD -> DashboardScreen(
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            onSaveTransaction = onSaveTransaction,
            onDeleteTransaction = onDeleteTransaction,
            onOpenAddTransactionSheet = { accId, t, amt, title, catId ->
                onOpenAddTransactionSheet(accId, t, amt, title, catId, false)
            },
            onNavigateToAccounts = onNavigateToAccounts,
            onNavigateToTransfer = { onNavigateToTransfer(null, null) },
            onNavigateToLoansManagement = onNavigateToLoansManagement,
            onTestNotification = { cardName ->
                notificationHelper.sendCreditCardCutoffNotification(
                    cardName = cardName,
                    daysLeft = 2,
                    cutoffDateFormatted = "Upcoming Cutoff",
                    currentBalanceFormatted = "₱6,800.00"
                )
            },
            onToggleLoanSettled = onToggleLoanSettled,
            isPrivacyMode = isPrivacyMode,
            onTogglePrivacyMode = onTogglePrivacyMode,
            primaryCurrency = primaryCurrency,
            exchangeRates = exchangeRates,
            onRefreshExchangeRates = {
                coroutineScope.launch {
                    onRefreshExchangeRates()
                }
            }
        )
        ScreenTab.ACCOUNTS -> AccountsScreen(
            accounts = accounts,
            onSaveAccount = onSaveAccount,
            onDeleteAccount = onDeleteAccount,
            onNavigateToTransfer = onNavigateToTransfer,
            onTestNotification = { cardName ->
                notificationHelper.sendCreditCardCutoffNotification(
                    cardName = cardName,
                    daysLeft = 2,
                    cutoffDateFormatted = "Upcoming Cutoff",
                    currentBalanceFormatted = "₱6,800.00"
                )
            },
            isPrivacyMode = isPrivacyMode
        )
        ScreenTab.TRANSACTIONS -> TransactionsScreen(
            transactions = transactions,
            accounts = accounts,
            categories = categories,
            onDeleteTransaction = onDeleteTransaction,
            onEditTransaction = onEditTransaction,
            onAddNew = { onOpenAddTransactionSheet(null, TransactionType.EXPENSE, "", "", null, false) },
            onToggleLoanSettled = onToggleLoanSettled,
            onNavigateToLoansManagement = onNavigateToLoansManagement,
            isPrivacyMode = isPrivacyMode,
            primaryCurrency = primaryCurrency,
            exchangeRates = exchangeRates
        )
        ScreenTab.CATEGORIES -> CategoriesScreen(
            categories = categories,
            budgets = budgets,
            transactions = transactions,
            onSaveCategory = onSaveCategory,
            onDeleteCategory = onDeleteCategory,
            onSaveBudget = onSaveBudget,
            onTestBudgetNotification = { name, pct, sp, lim ->
                notificationHelper.sendBudgetWarningNotification(name, pct, sp, lim)
            }
        )
        ScreenTab.REPORTS -> ReportsScreen(
            transactions = transactions,
            onExportCsv = onExportCsv,
            onImportCsv = onImportCsv,
            onExportDatabaseBackup = onExportDatabaseBackup,
            onRestoreDatabaseBackup = onRestoreDatabaseBackup
        )
        ScreenTab.SETTINGS -> SettingsScreen(
            biometricHelper = biometricHelper,
            notificationHelper = notificationHelper,
            onLockAppNow = onLockAppNow,
            onResetDatabase = onResetDatabase,
            onExportDatabaseBackup = onExportDatabaseBackup,
            onRestoreDatabaseBackup = onRestoreDatabaseBackup,
            primaryCurrency = primaryCurrency,
            onPrimaryCurrencyChanged = onPrimaryCurrencyChanged,
            exchangeRates = exchangeRates,
            onRefreshExchangeRates = onRefreshExchangeRates
        )
    }
}
