package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.AccountDao
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetDao
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryDao
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionDao
import com.example.data.local.TransactionEntity
import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.RecurringFrequency
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import com.example.util.CsvUtils
import com.example.util.CurrencyUtils
import com.example.util.DatabaseBackupUtils
import com.example.util.DatabaseRestoreResult
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

class FinanceRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val database: AppDatabase? = null
) {

    // --- Accounts ---
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)?.toDomain()

    suspend fun saveAccount(account: Account): Long {
        return if (account.id == 0L) {
            accountDao.insertAccount(AccountEntity.fromDomain(account))
        } else {
            accountDao.updateAccount(AccountEntity.fromDomain(account))
            account.id
        }
    }

    suspend fun deleteAccount(account: Account) {
        val deletedLabel = "Deleted ${account.type.displayName}"
        transactionDao.markDeletedAccount(account.id, deletedLabel)
        transactionDao.markDeletedToAccount(account.id, deletedLabel)
        accountDao.deleteAccount(AccountEntity.fromDomain(account))
    }

    // --- Categories ---
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveCategory(category: Category): Long {
        return if (category.id == 0L) {
            categoryDao.insertCategory(CategoryEntity.fromDomain(category))
        } else {
            categoryDao.updateCategory(CategoryEntity.fromDomain(category))
            category.id
        }
    }

    suspend fun deleteCategory(category: Category, targetCategoryId: Long? = null): Int {
        var count = 0
        if (targetCategoryId != null) {
            count = transactionDao.getTransactionCountForCategory(category.id)
            transactionDao.reassignCategory(oldCategoryId = category.id, newCategoryId = targetCategoryId)
        }
        budgetDao.deleteBudgetForCategory(category.id)
        categoryDao.deleteCategory(CategoryEntity.fromDomain(category))
        return count
    }

    // --- Transactions with Details ---
    val allTransactionsWithDetails: Flow<List<TransactionWithDetails>> = combine(
        transactionDao.getAllTransactions(),
        accountDao.getAllAccounts(),
        categoryDao.getAllCategories()
    ) { txEntities, accountEntities, categoryEntities ->
        val accountsMap = accountEntities.associateBy { it.id }
        val categoriesMap = categoryEntities.associateBy { it.id }

        txEntities.map { entity ->
            val tx = entity.toDomain()
            val account = accountsMap[tx.accountId]
            val toAccount = tx.toAccountId?.let { accountsMap[it] }
            val category = tx.categoryId?.let { categoriesMap[it] }

            val accountName = entity.deletedAccountName ?: account?.name ?: "Deleted Account"
            val toAccountName = if (tx.toAccountId != null) {
                entity.deletedToAccountName ?: toAccount?.name ?: "Deleted Account"
            } else null

            TransactionWithDetails(
                transaction = tx,
                accountName = accountName,
                accountIcon = account?.iconName ?: "credit_card",
                toAccountName = toAccountName,
                categoryName = category?.name,
                categoryIcon = category?.iconName,
                categoryColor = category?.colorHex,
                accountCurrency = account?.currency ?: "PHP",
                toAccountCurrency = toAccount?.currency
            )
        }
    }

    fun getTransactionsInRangeWithDetails(startTime: Long, endTime: Long): Flow<List<TransactionWithDetails>> {
        return combine(
            transactionDao.getTransactionsInRange(startTime, endTime),
            accountDao.getAllAccounts(),
            categoryDao.getAllCategories()
        ) { txEntities, accountEntities, categoryEntities ->
            val accountsMap = accountEntities.associateBy { it.id }
            val categoriesMap = categoryEntities.associateBy { it.id }

            txEntities.map { entity ->
                val tx = entity.toDomain()
                val account = accountsMap[tx.accountId]
                val toAccount = tx.toAccountId?.let { accountsMap[it] }
                val category = tx.categoryId?.let { categoriesMap[it] }

                val accountName = entity.deletedAccountName ?: account?.name ?: "Deleted Account"
                val toAccountName = if (tx.toAccountId != null) {
                    entity.deletedToAccountName ?: toAccount?.name ?: "Deleted Account"
                } else null

                TransactionWithDetails(
                    transaction = tx,
                    accountName = accountName,
                    accountIcon = account?.iconName ?: "credit_card",
                    toAccountName = toAccountName,
                    categoryName = category?.name,
                    categoryIcon = category?.iconName,
                    categoryColor = category?.colorHex,
                    accountCurrency = account?.currency ?: "PHP",
                    toAccountCurrency = toAccount?.currency
                )
            }
        }
    }

    suspend fun addTransaction(transaction: Transaction): Long {
        // Calculate initial nextDueDate if recurring
        val nextDue = if (transaction.isRecurring && transaction.recurringFrequency != RecurringFrequency.NONE) {
            calculateNextDueDate(transaction.timestamp, transaction.recurringFrequency)
        } else null

        val txToSave = transaction.copy(nextDueDate = nextDue)
        val id = transactionDao.insertTransaction(TransactionEntity.fromDomain(txToSave))

        // Update balances according to account type rules
        updateAccountBalanceForTransaction(txToSave, isRevert = false)
        return id
    }

    suspend fun deleteTransaction(tx: Transaction) {
        // Revert balance effects
        updateAccountBalanceForTransaction(tx, isRevert = true)
        transactionDao.deleteTransaction(TransactionEntity.fromDomain(tx))
    }

    suspend fun editTransaction(oldTx: Transaction, newTx: Transaction) {
        // Revert old transaction effects
        updateAccountBalanceForTransaction(oldTx, isRevert = true)

        // Apply new transaction effects
        updateAccountBalanceForTransaction(newTx, isRevert = false)

        transactionDao.updateTransaction(TransactionEntity.fromDomain(newTx))
    }

    private suspend fun isCreditCard(accountId: Long): Boolean {
        val account = accountDao.getAccountById(accountId) ?: return false
        return account.type == AccountType.CREDIT_CARD.name
    }

    private suspend fun updateAccountBalanceForTransaction(
        tx: Transaction,
        isRevert: Boolean = false
    ) {
        val direction = if (isRevert) -1.0 else 1.0

        when (tx.type) {
            TransactionType.EXPENSE -> {
                // For normal accounts: Expense deducts from balance (-amount).
                // For credit cards: + means outstanding balance, so expense adds to outstanding debt (+amount).
                val isCc = isCreditCard(tx.accountId)
                val delta = if (isCc) tx.amount else -tx.amount
                val roundedDelta = CurrencyUtils.roundTo2Decimals(delta * direction)
                accountDao.updateBalance(tx.accountId, roundedDelta)
            }
            TransactionType.INCOME -> {
                // For normal accounts: Income adds to balance (+amount).
                // For credit cards: Income (refund/cashback) reduces outstanding debt or creates overpayment (-amount).
                val isCc = isCreditCard(tx.accountId)
                val delta = if (isCc) -tx.amount else tx.amount
                val roundedDelta = CurrencyUtils.roundTo2Decimals(delta * direction)
                accountDao.updateBalance(tx.accountId, roundedDelta)
            }
            TransactionType.TRANSFER -> {
                // Source account (outgoing transfer):
                // For normal accounts: Deducts (amount + fee).
                // For credit cards (cash advance): Increases outstanding balance by (amount + fee).
                val isSourceCc = isCreditCard(tx.accountId)
                val sourceDelta = if (isSourceCc) {
                    tx.amount + tx.transferFee
                } else {
                    -(tx.amount + tx.transferFee)
                }
                val roundedSourceDelta = CurrencyUtils.roundTo2Decimals(sourceDelta * direction)
                accountDao.updateBalance(tx.accountId, roundedSourceDelta)

                // Destination account (incoming transfer / payment):
                // For normal accounts: Adds amount (+amount).
                // For credit cards (paying off credit card): Subtracts from outstanding balance or creates overpayment (-amount).
                tx.toAccountId?.let { targetId ->
                    val isTargetCc = isCreditCard(targetId)
                    val targetDelta = if (isTargetCc) -tx.amount else tx.amount
                    val roundedTargetDelta = CurrencyUtils.roundTo2Decimals(targetDelta * direction)
                    accountDao.updateBalance(targetId, roundedTargetDelta)
                }
            }
        }
    }

    suspend fun recalculateAccountBalancesFromTransactions() {
        val accounts = accountDao.getAllAccountsList()
        val allTx = transactionDao.getAllTransactionsList().sortedBy { it.timestamp }

        // Start each account from its initialBalance (rounded to 2 decimals)
        val balanceMap = accounts.associate { it.id to CurrencyUtils.roundTo2Decimals(it.initialBalance) }.toMutableMap()
        val typeMap = accounts.associate { it.id to (it.type == AccountType.CREDIT_CARD.name) }

        for (entity in allTx) {
            val tx = entity.toDomain()
            when (tx.type) {
                TransactionType.EXPENSE -> {
                    if (balanceMap.containsKey(tx.accountId)) {
                        val isCc = typeMap[tx.accountId] ?: false
                        val delta = if (isCc) tx.amount else -tx.amount
                        balanceMap[tx.accountId] = CurrencyUtils.roundTo2Decimals((balanceMap[tx.accountId] ?: 0.0) + delta)
                    }
                }
                TransactionType.INCOME -> {
                    if (balanceMap.containsKey(tx.accountId)) {
                        val isCc = typeMap[tx.accountId] ?: false
                        val delta = if (isCc) -tx.amount else tx.amount
                        balanceMap[tx.accountId] = CurrencyUtils.roundTo2Decimals((balanceMap[tx.accountId] ?: 0.0) + delta)
                    }
                }
                TransactionType.TRANSFER -> {
                    if (balanceMap.containsKey(tx.accountId)) {
                        val isSourceCc = typeMap[tx.accountId] ?: false
                        val sourceDelta = if (isSourceCc) tx.amount + tx.transferFee else -(tx.amount + tx.transferFee)
                        balanceMap[tx.accountId] = CurrencyUtils.roundTo2Decimals((balanceMap[tx.accountId] ?: 0.0) + sourceDelta)
                    }

                    tx.toAccountId?.let { targetId ->
                        if (balanceMap.containsKey(targetId)) {
                            val isTargetCc = typeMap[targetId] ?: false
                            val targetDelta = if (isTargetCc) -tx.amount else tx.amount
                            balanceMap[targetId] = CurrencyUtils.roundTo2Decimals((balanceMap[targetId] ?: 0.0) + targetDelta)
                        }
                    }
                }
            }
        }

        for (account in accounts) {
            val correctBalance = CurrencyUtils.roundTo2Decimals(balanceMap[account.id] ?: account.initialBalance)
            val currentBalance = CurrencyUtils.roundTo2Decimals(account.balance)
            // Guard against microadjustments: only update if discrepancy is >= 0.009 (1 cent)
            if (kotlin.math.abs(currentBalance - correctBalance) >= 0.009) {
                accountDao.updateAccount(account.copy(balance = correctBalance))
            }
        }
    }

    suspend fun transferBetweenAccounts(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        fee: Double = 0.0,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val roundedAmount = CurrencyUtils.roundTo2Decimals(amount)
        val roundedFee = CurrencyUtils.roundTo2Decimals(fee)
        val transferTx = Transaction(
            type = TransactionType.TRANSFER,
            amount = roundedAmount,
            accountId = fromAccountId,
            toAccountId = toAccountId,
            transferFee = roundedFee,
            title = "Transfer to Account",
            note = note,
            timestamp = timestamp
        )
        return addTransaction(transferTx)
    }

    // --- Recurring Transactions ---
    suspend fun processDueRecurringTransactions(): Int {
        val recurringEntities = transactionDao.getActiveRecurringTransactions().first()
        val now = System.currentTimeMillis()
        var processedCount = 0

        for (entity in recurringEntities) {
            val tx = entity.toDomain()
            val nextDue = tx.nextDueDate ?: continue

            if (nextDue <= now) {
                // Time to create recurring occurrence
                val newOccurrence = Transaction(
                    type = tx.type,
                    amount = tx.amount,
                    accountId = tx.accountId,
                    toAccountId = tx.toAccountId,
                    transferFee = tx.transferFee,
                    categoryId = tx.categoryId,
                    title = "${tx.title} (Auto Recurring)",
                    note = tx.note,
                    timestamp = nextDue,
                    isRecurring = false
                )
                addTransaction(newOccurrence)

                // Advance the parent recurring rule
                val newExecutedCount = tx.recurringExecutedCount + 1
                val isFinished = tx.recurringTotalCount > 0 && newExecutedCount >= tx.recurringTotalCount
                val nextNextDue = if (!isFinished) calculateNextDueDate(nextDue, tx.recurringFrequency) else null

                val updatedParent = tx.copy(
                    recurringExecutedCount = newExecutedCount,
                    nextDueDate = nextNextDue
                )
                transactionDao.updateTransaction(TransactionEntity.fromDomain(updatedParent))
                processedCount++
            }
        }
        return processedCount
    }

    private fun calculateNextDueDate(baseTime: Long, frequency: RecurringFrequency): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = baseTime }
        when (frequency) {
            RecurringFrequency.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurringFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurringFrequency.BIWEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 14)
            RecurringFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RecurringFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
            RecurringFrequency.NONE -> {}
        }
        return cal.timeInMillis
    }

    // --- Budgets ---
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveBudget(budget: Budget): Long {
        return if (budget.id == 0L) {
            budgetDao.insertBudget(BudgetEntity.fromDomain(budget))
        } else {
            budgetDao.updateBudget(BudgetEntity.fromDomain(budget))
            budget.id
        }
    }

    suspend fun deleteBudget(budget: Budget) {
        budgetDao.deleteBudget(BudgetEntity.fromDomain(budget))
    }

    // --- CSV Import / Export ---
    suspend fun exportToCsv(startTime: Long? = null, endTime: Long? = null): String {
        val transactions = if (startTime != null && endTime != null) {
            getTransactionsInRangeWithDetails(startTime, endTime).first()
        } else {
            allTransactionsWithDetails.first()
        }
        return CsvUtils.exportTransactionsToCsv(transactions)
    }

    suspend fun importFromCsv(csvContent: String): Int {
        val rows = CsvUtils.parseTransactionsCsv(csvContent)
        if (rows.isEmpty()) return 0

        val currentAccounts = allAccounts.first()
        val currentCategories = allCategories.first()

        val accountsByName = currentAccounts.associateBy { it.name.lowercase().trim() }
        val categoriesByName = currentCategories.associateBy { it.name.lowercase().trim() }

        var importedCount = 0
        for (row in rows) {
            // Find or resolve account
            val account = accountsByName[row.accountName.lowercase().trim()] ?: currentAccounts.firstOrNull() ?: continue
            val toAccount = if (row.type == TransactionType.TRANSFER && row.toAccountName.isNotBlank()) {
                accountsByName[row.toAccountName.lowercase().trim()]
            } else null

            // Find or resolve category
            val category = categoriesByName[row.categoryName.lowercase().trim()]

            val tx = Transaction(
                type = row.type,
                amount = row.amount,
                accountId = account.id,
                toAccountId = toAccount?.id,
                transferFee = row.fee,
                categoryId = category?.id,
                title = row.title,
                note = row.note,
                timestamp = row.date,
                isRecurring = row.isRecurring,
                recurringFrequency = row.frequency
            )
            addTransaction(tx)
            importedCount++
        }
        return importedCount
    }

    suspend fun markLoanSettled(transactionId: Long, isSettled: Boolean) {
        val txEntity = transactionDao.getTransactionById(transactionId) ?: return
        val updated = txEntity.copy(isLoanSettled = isSettled)
        transactionDao.updateTransaction(updated)
    }

    // --- Full Database Backup & Restore ---

    suspend fun exportFullDatabaseBackup(): String {
        val accounts = accountDao.getAllAccountsList()
        val categories = categoryDao.getAllCategoriesList()
        val transactions = transactionDao.getAllTransactionsList()
        val budgets = budgetDao.getAllBudgetsList()
        return DatabaseBackupUtils.exportDatabaseToJson(
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            budgets = budgets
        )
    }

    suspend fun restoreFullDatabaseBackup(backup: com.example.util.FullDatabaseBackup): DatabaseRestoreResult {
        val performRestore: suspend () -> Unit = {
            transactionDao.deleteAllTransactions()
            budgetDao.deleteAllBudgets()
            accountDao.deleteAllAccounts()
            categoryDao.deleteAllCategories()

            if (backup.categories.isNotEmpty()) {
                categoryDao.insertCategories(backup.categories)
            }
            if (backup.accounts.isNotEmpty()) {
                accountDao.insertAccounts(backup.accounts)
            }
            if (backup.transactions.isNotEmpty()) {
                transactionDao.insertTransactions(backup.transactions)
            }
            if (backup.budgets.isNotEmpty()) {
                budgetDao.insertBudgets(backup.budgets)
            }
        }

        if (database != null) {
            database.withTransaction {
                performRestore()
            }
        } else {
            performRestore()
        }

        return DatabaseRestoreResult.Success(
            accountsCount = backup.accounts.size,
            categoriesCount = backup.categories.size,
            transactionsCount = backup.transactions.size,
            budgetsCount = backup.budgets.size
        )
    }

    /**
     * Resets database to a completely clean fresh state (0 accounts, 0 transactions, 0 budgets)
     * and repopulates the standard default categories within a single database transaction.
     */
    suspend fun resetDatabaseToFreshState() {
        val performReset: suspend () -> Unit = {
            transactionDao.deleteAllTransactions()
            budgetDao.deleteAllBudgets()
            accountDao.deleteAllAccounts()
            categoryDao.deleteAllCategories()
            if (database != null) {
                AppDatabase.populateInitialData(database)
            }
        }

        if (database != null) {
            database.withTransaction {
                performReset()
            }
        } else {
            performReset()
        }
    }

    suspend fun restoreFullDatabaseBackup(backupJson: String): DatabaseRestoreResult {
        val parseResult = DatabaseBackupUtils.parseBackupJson(backupJson)
        if (parseResult.isFailure) {
            return DatabaseRestoreResult.Error(
                parseResult.exceptionOrNull()?.message ?: "Invalid backup format"
            )
        }
        return restoreFullDatabaseBackup(parseResult.getOrThrow())
    }
}
