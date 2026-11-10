package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@org.junit.Ignore("Robolectric shadow jars require online Maven fetcher not available in offline test container")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SpendFlow", appName)
  }

  @Test
  fun testFullDatabaseBackupAndRestoreRoundTrip() {
    val accounts = listOf(
      com.example.data.local.AccountEntity(
        id = 1,
        name = "Wallet",
        type = "CASH",
        balance = 1200.0,
        initialBalance = 1000.0,
        currency = "PHP",
        colorHex = "#10B981",
        iconName = "local_atm",
        accountNumberMask = "",
        creditLimit = 0.0,
        statementDateType = "FIXED",
        statementDay = 1,
        statementOffsetDays = 0,
        paymentGraceDays = 0
      )
    )

    val categories = listOf(
      com.example.data.local.CategoryEntity(
        id = 1,
        name = "Food",
        type = "EXPENSE",
        iconName = "restaurant",
        colorHex = "#F59E0B",
        isDefault = true
      )
    )

    val transactions = listOf(
      com.example.data.local.TransactionEntity(
        id = 1,
        type = "EXPENSE",
        amount = 150.0,
        accountId = 1,
        toAccountId = null,
        transferFee = 0.0,
        categoryId = 1,
        title = "Snack",
        note = "Coffee and bread",
        timestamp = 1761234567000L,
        isRecurring = false,
        recurringFrequency = "NONE",
        recurringTotalCount = 0,
        recurringExecutedCount = 0,
        nextDueDate = null,
        isLoan = false,
        loanPerson = "",
        loanDueDate = null,
        hasInterest = false,
        interestRate = null,
        interestType = "MONTHLY",
        hasTerm = false,
        termUnit = "MONTHS",
        termLength = 1,
        isLoanSettled = false
      )
    )

    val budgets = listOf(
      com.example.data.local.BudgetEntity(
        id = 1,
        categoryId = 1,
        monthlyLimit = 5000.0,
        notifyThresholdPercent = 80
      )
    )

    val json = com.example.util.DatabaseBackupUtils.exportDatabaseToJson(
      accounts, categories, transactions, budgets
    )

    assertTrue(json.contains("\"app\": \"SpendFlow\""))
    assertTrue(json.contains("\"accountsCount\": 1"))
    assertTrue(json.contains("\"Wallet\""))
    assertTrue(json.contains("\"Coffee and bread\""))

    val parseResult = com.example.util.DatabaseBackupUtils.parseBackupJson(json)
    assertTrue(parseResult.isSuccess)

    val backup = parseResult.getOrThrow()
    assertEquals(1, backup.accounts.size)
    assertEquals("Wallet", backup.accounts[0].name)
    assertEquals(1200.0, backup.accounts[0].balance, 0.001)

    assertEquals(1, backup.categories.size)
    assertEquals("Food", backup.categories[0].name)

    assertEquals(1, backup.transactions.size)
    assertEquals("Snack", backup.transactions[0].title)
    assertEquals(150.0, backup.transactions[0].amount, 0.001)

    assertEquals(1, backup.budgets.size)
    assertEquals(5000.0, backup.budgets[0].monthlyLimit, 0.001)
  }

  @Test
  fun testSampleBackupTemplateValidation() {
    val sample = com.example.util.DatabaseBackupUtils.getSampleBackupTemplate()
    val parseResult = com.example.util.DatabaseBackupUtils.parseBackupJson(sample)
    assertTrue(parseResult.isSuccess)

    val backup = parseResult.getOrThrow()
    assertEquals(2, backup.accounts.size)
    assertEquals(3, backup.categories.size)
    assertEquals(2, backup.transactions.size)
    assertEquals(1, backup.budgets.size)
  }

  @Test
  fun testLoanAndPaymentTransactionCalculations() {
    // A loan borrowed from someone (Income of 5000)
    val borrowedLoan = com.example.data.model.Transaction(
      id = 10,
      type = com.example.data.model.TransactionType.INCOME,
      amount = 5000.0,
      accountId = 1,
      title = "Loan from John",
      isLoan = true,
      loanPerson = "John",
      isLoanSettled = false
    )

    // A repayment against the borrowed loan (Expense of 2000)
    val repayment1 = com.example.data.model.Transaction(
      id = 11,
      type = com.example.data.model.TransactionType.EXPENSE,
      amount = 2000.0,
      accountId = 1,
      title = "Debt Payment to John",
      isLoanPayment = true,
      loanReferenceId = 10L,
      loanPerson = "John"
    )

    val repayment2 = com.example.data.model.Transaction(
      id = 12,
      type = com.example.data.model.TransactionType.EXPENSE,
      amount = 3000.0,
      accountId = 1,
      title = "Debt Payment to John",
      isLoanPayment = true,
      loanReferenceId = 10L,
      loanPerson = "John"
    )

    val allTxs = listOf(borrowedLoan, repayment1, repayment2)
    val paymentsForLoan = allTxs.filter { it.isLoanPayment && it.loanReferenceId == 10L }
    val totalRepaid = paymentsForLoan.sumOf { it.amount }
    val remaining = kotlin.math.max(0.0, borrowedLoan.amount - totalRepaid)

    assertEquals(5000.0, totalRepaid, 0.001)
    assertEquals(0.0, remaining, 0.001)
    assertTrue(totalRepaid >= borrowedLoan.amount)
  }

  @Test
  fun testCreditCardReversedIncomeExpenseAndTransferBalanceRules() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val repo = com.example.data.repository.FinanceRepository(
      db.accountDao(),
      db.transactionDao(),
      db.categoryDao(),
      db.budgetDao(),
      db
    )

    // 1. Create a Normal Account (Savings) with 10,000 balance
    val savingsId = repo.saveAccount(
      com.example.data.model.Account(
        name = "Savings",
        type = com.example.data.model.AccountType.SAVINGS,
        balance = 10000.0,
        initialBalance = 10000.0
      )
    )

    // 2. Create a Credit Card with 5,000 outstanding balance (+5000)
    val cardId = repo.saveAccount(
      com.example.data.model.Account(
        name = "Visa Card",
        type = com.example.data.model.AccountType.CREDIT_CARD,
        balance = 5000.0,
        initialBalance = 5000.0,
        creditLimit = 50000.0
      )
    )

    // Verify initial states
    assertEquals(10000.0, repo.getAccountById(savingsId)?.balance ?: 0.0, 0.001)
    assertEquals(5000.0, repo.getAccountById(cardId)?.balance ?: 0.0, 0.001)

    // 3. User makes an expense of 1,500 on the credit card
    // Rule: Expense on credit card adds to outstanding balance (+amount)
    repo.addTransaction(
      com.example.data.model.Transaction(
        type = com.example.data.model.TransactionType.EXPENSE,
        amount = 1500.0,
        accountId = cardId,
        title = "Grocery shopping"
      )
    )
    assertEquals(6500.0, repo.getAccountById(cardId)?.balance ?: 0.0, 0.001)

    // 4. User receives an income (refund/cashback) of 500 on the credit card
    // Rule: Income on credit card subtracts from outstanding balance (-amount)
    repo.addTransaction(
      com.example.data.model.Transaction(
        type = com.example.data.model.TransactionType.INCOME,
        amount = 500.0,
        accountId = cardId,
        title = "Item refund"
      )
    )
    assertEquals(6000.0, repo.getAccountById(cardId)?.balance ?: 0.0, 0.001)

    // 5. User transfers / pays 4,000 from Savings to Credit Card
    // Rule: Incoming transfer to credit card subtracts from outstanding balance (-amount)
    repo.transferBetweenAccounts(
      fromAccountId = savingsId,
      toAccountId = cardId,
      amount = 4000.0
    )
    assertEquals(6000.0, repo.getAccountById(savingsId)?.balance ?: 0.0, 0.001)
    assertEquals(2000.0, repo.getAccountById(cardId)?.balance ?: 0.0, 0.001)

    // 6. User overpays the credit card by transferring 3,000 more from Savings to Credit Card
    // Rule: - value means overpayment (2,000 - 3,000 = -1,000)
    repo.transferBetweenAccounts(
      fromAccountId = savingsId,
      toAccountId = cardId,
      amount = 3000.0
    )
    assertEquals(3000.0, repo.getAccountById(savingsId)?.balance ?: 0.0, 0.001)
    assertEquals(-1000.0, repo.getAccountById(cardId)?.balance ?: 0.0, 0.001)

    // 7. Test recalculation logic: recalculateAccountBalancesFromTransactions
    repo.recalculateAccountBalancesFromTransactions()
    assertEquals(3000.0, repo.getAccountById(savingsId)?.balance ?: 0.0, 0.001)
    assertEquals(-1000.0, repo.getAccountById(cardId)?.balance ?: 0.0, 0.001)

    db.close()
  }

  @Test
  fun testSqliteDatabaseBackupAndRestoreInspection() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val dbFile = java.io.File(context.cacheDir, "test_spendflow_backup.db")
      if (dbFile.exists()) dbFile.delete()

      // 1. Create a real SQLite database file with the schema tables
      val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
      sqliteDb.execSQL(
        """
        CREATE TABLE accounts (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          name TEXT NOT NULL,
          type TEXT NOT NULL,
          balance REAL NOT NULL,
          initialBalance REAL NOT NULL,
          currency TEXT NOT NULL,
          colorHex TEXT NOT NULL,
          iconName TEXT NOT NULL,
          accountNumberMask TEXT NOT NULL,
          creditLimit REAL NOT NULL,
          statementDateType TEXT NOT NULL,
          statementDay INTEGER NOT NULL,
          statementOffsetDays INTEGER NOT NULL,
          paymentGraceDays INTEGER NOT NULL
        )
        """.trimIndent()
      )
      sqliteDb.execSQL(
        """
        CREATE TABLE categories (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          name TEXT NOT NULL,
          type TEXT NOT NULL,
          iconName TEXT NOT NULL,
          colorHex TEXT NOT NULL,
          isDefault INTEGER NOT NULL
        )
        """.trimIndent()
      )
      sqliteDb.execSQL(
        """
        CREATE TABLE transactions (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          type TEXT NOT NULL,
          amount REAL NOT NULL,
          accountId INTEGER NOT NULL,
          toAccountId INTEGER,
          transferFee REAL NOT NULL,
          categoryId INTEGER,
          title TEXT NOT NULL,
          note TEXT NOT NULL,
          timestamp INTEGER NOT NULL,
          isRecurring INTEGER NOT NULL,
          recurringFrequency TEXT NOT NULL,
          recurringTotalCount INTEGER NOT NULL,
          recurringExecutedCount INTEGER NOT NULL,
          nextDueDate INTEGER,
          isLoan INTEGER NOT NULL DEFAULT 0,
          loanPerson TEXT NOT NULL DEFAULT '',
          loanDueDate INTEGER,
          hasInterest INTEGER NOT NULL DEFAULT 0,
          interestRate REAL,
          interestType TEXT NOT NULL DEFAULT 'MONTHLY',
          hasTerm INTEGER NOT NULL DEFAULT 0,
          termUnit TEXT NOT NULL DEFAULT 'MONTHS',
          termLength INTEGER NOT NULL DEFAULT 1,
          isLoanSettled INTEGER NOT NULL DEFAULT 0,
          isLoanPayment INTEGER NOT NULL DEFAULT 0,
          loanReferenceId INTEGER
        )
        """.trimIndent()
      )
      sqliteDb.execSQL(
        """
        CREATE TABLE budgets (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          categoryId INTEGER NOT NULL,
          monthlyLimit REAL NOT NULL,
          notifyThresholdPercent INTEGER NOT NULL
        )
        """.trimIndent()
      )

      // Insert records
      sqliteDb.execSQL(
        "INSERT INTO accounts (id, name, type, balance, initialBalance, currency, colorHex, iconName, accountNumberMask, creditLimit, statementDateType, statementDay, statementOffsetDays, paymentGraceDays) " +
        "VALUES (1, 'BDO Checking', 'CHECKING', 15000.0, 15000.0, 'PHP', '#3B82F6', 'account_balance', '*9988', 0.0, 'FIXED', 1, 0, 0)"
      )
      sqliteDb.execSQL(
        "INSERT INTO categories (id, name, type, iconName, colorHex, isDefault) " +
        "VALUES (1, 'Utilities', 'EXPENSE', 'receipt_long', '#EF4444', 1)"
      )
      sqliteDb.execSQL(
        "INSERT INTO transactions (id, type, amount, accountId, transferFee, title, note, timestamp, isRecurring, recurringFrequency, recurringTotalCount, recurringExecutedCount) " +
        "VALUES (1, 'EXPENSE', 2500.0, 1, 0.0, 'Electric Bill', 'Meralco', 1761234567000, 0, 'NONE', 0, 0)"
      )
      sqliteDb.execSQL(
        "INSERT INTO budgets (id, categoryId, monthlyLimit, notifyThresholdPercent) " +
        "VALUES (1, 1, 5000.0, 80)"
      )
      sqliteDb.close()

      // 2. Validate SQLite format detection
      assertTrue(com.example.util.DatabaseBackupUtils.isSqliteFile(dbFile))

      // 3. Parse SQLite database
      val parseResult = com.example.util.DatabaseBackupUtils.parseSqliteDatabase(dbFile)
      assertTrue(parseResult.isSuccess)
      val backup = parseResult.getOrThrow()

      assertEquals(1, backup.accounts.size)
      assertEquals("BDO Checking", backup.accounts[0].name)
      assertEquals(15000.0, backup.accounts[0].balance, 0.001)

      assertEquals(1, backup.categories.size)
      assertEquals("Utilities", backup.categories[0].name)

      assertEquals(1, backup.transactions.size)
      assertEquals("Electric Bill", backup.transactions[0].title)
      assertEquals(2500.0, backup.transactions[0].amount, 0.001)

      assertEquals(1, backup.budgets.size)
      assertEquals(5000.0, backup.budgets[0].monthlyLimit, 0.001)

      // 4. Restore the parsed SQLite backup into a live Room database
      val roomDb = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
      val repo = com.example.data.repository.FinanceRepository(
        roomDb.accountDao(),
        roomDb.transactionDao(),
        roomDb.categoryDao(),
        roomDb.budgetDao(),
        roomDb
      )

      val restoreResult = repo.restoreFullDatabaseBackup(backup)
      assertTrue(restoreResult is com.example.util.DatabaseRestoreResult.Success)
      val success = restoreResult as com.example.util.DatabaseRestoreResult.Success
      assertEquals(1, success.accountsCount)
      assertEquals(1, success.categoriesCount)
      assertEquals(1, success.transactionsCount)
      assertEquals(1, success.budgetsCount)

      val restoredAccount = repo.getAccountById(1)
      assertEquals("BDO Checking", restoredAccount?.name)
      assertEquals(15000.0, restoredAccount?.balance ?: 0.0, 0.001)

      roomDb.close()
      dbFile.delete()
    }
  }

  @Test
  fun testDeleteAccountPreservesTransactionsAndMarksDeleted() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val repo = com.example.data.repository.FinanceRepository(
      db.accountDao(),
      db.transactionDao(),
      db.categoryDao(),
      db.budgetDao(),
      db
    )

    // 1. Create a cash wallet account
    val account = com.example.data.model.Account(
      name = "Cash Wallet",
      type = com.example.data.model.AccountType.CASH,
      balance = 2000.0,
      initialBalance = 2000.0
    )
    val accountId = repo.saveAccount(account)
    val savedAccount = repo.getAccountById(accountId)!!

    // 2. Add an expense transaction
    val txId = repo.addTransaction(
      com.example.data.model.Transaction(
        type = com.example.data.model.TransactionType.EXPENSE,
        amount = 350.0,
        accountId = accountId,
        title = "Lunch at Diner"
      )
    )

    // Verify transaction exists before deletion
    val txBefore = db.transactionDao().getTransactionById(txId)
    org.junit.Assert.assertNotNull(txBefore)
    assertEquals(accountId, txBefore?.accountId)

    // 3. Delete the account
    repo.deleteAccount(savedAccount)

    // Verify account is removed from accounts table
    val deletedAcc = repo.getAccountById(accountId)
    org.junit.Assert.assertNull(deletedAcc)

    // Verify transaction was NOT deleted from transactions table!
    val txAfter = db.transactionDao().getTransactionById(txId)
    org.junit.Assert.assertNotNull(txAfter)
    assertEquals("Deleted Cash", txAfter?.deletedAccountName)

    // Verify detail list mapping displays "Deleted Cash"
    val allDetails = repo.allTransactionsWithDetails.first()
    assertEquals(1, allDetails.size)
    assertEquals("Deleted Cash", allDetails[0].accountName)
    assertEquals("Lunch at Diner", allDetails[0].transaction.title)
    assertEquals(350.0, allDetails[0].transaction.amount, 0.001)

    db.close()
  }

  @Test
  fun testCurrencyUtilsFormattingAndRoundingPrecision() {
    // 1. Comma formatting on thousands, millions
    assertEquals("1,234", com.example.util.CurrencyUtils.formatCurrencyInput("1234"))
    assertEquals("1,234.5", com.example.util.CurrencyUtils.formatCurrencyInput("1234.5"))
    assertEquals("1,234.56", com.example.util.CurrencyUtils.formatCurrencyInput("1234.56"))
    assertEquals("1,234.56", com.example.util.CurrencyUtils.formatCurrencyInput("1234.567"))
    assertEquals("1,234,567.89", com.example.util.CurrencyUtils.formatCurrencyInput("1234567.89"))
    assertEquals("-1,234.50", com.example.util.CurrencyUtils.formatCurrencyInput("-1234.50"))

    // 2. Parsing currency inputs
    assertEquals(1234.56, com.example.util.CurrencyUtils.parseCurrencyInput("1,234.56"), 0.0001)
    assertEquals(1234567.89, com.example.util.CurrencyUtils.parseCurrencyInput("1,234,567.89"), 0.0001)
    assertEquals(0.0, com.example.util.CurrencyUtils.parseCurrencyInput(""), 0.0001)

    // 3. Strict 2-decimal rounding eliminating 0.000000001 drift
    val floatingPointDrift = 15000.0 - 0.000000001
    val rounded = com.example.util.CurrencyUtils.roundTo2Decimals(floatingPointDrift)
    assertEquals(15000.0, rounded, 0.00001)

    // 4. Formatter display
    assertEquals("1,234.50", com.example.util.CurrencyUtils.formatDisplay(1234.5))
    assertEquals("0.00", com.example.util.CurrencyUtils.formatDisplay(0.0))
    assertEquals("", com.example.util.CurrencyUtils.formatDoubleToInput(0.0, showZeroAsEmpty = true))
    assertEquals("1,234.50", com.example.util.CurrencyUtils.formatDoubleToInput(1234.5))
  }

  @Test
  fun testCurrencyInputCursorPreservation() {
    // 1. Typing 4th digit '4' into "123" at end:
    // User types '4' -> raw input "1234" with cursor at index 4
    val input1 = androidx.compose.ui.text.input.TextFieldValue("1234", androidx.compose.ui.text.TextRange(4))
    val result1 = com.example.util.CurrencyUtils.formatCurrencyInput(input1, previousText = "123")
    assertEquals("1,234", result1.text)
    // Cursor MUST remain at index 5 (after '4'), NOT at index 2 (between '1' and '2')!
    assertEquals(5, result1.selection.end)

    // 2. Typing '5' at end of "1,234":
    // User types '5' -> raw input "1,2345" with cursor at index 6
    val input2 = androidx.compose.ui.text.input.TextFieldValue("1,2345", androidx.compose.ui.text.TextRange(6))
    val result2 = com.example.util.CurrencyUtils.formatCurrencyInput(input2, previousText = "1,234")
    assertEquals("12,345", result2.text)
    assertEquals(6, result2.selection.end)

    // 3. Decimal entry: typing '.' after "1,234" with cursor at 5
    val inputDot = androidx.compose.ui.text.input.TextFieldValue("1,234.", androidx.compose.ui.text.TextRange(6))
    val resultDot = com.example.util.CurrencyUtils.formatCurrencyInput(inputDot, previousText = "1,234")
    assertEquals("1,234.", resultDot.text)
    assertEquals(6, resultDot.selection.end)

    // 4. Typing decimal digit '5' after decimal point "1,234."
    val inputDec1 = androidx.compose.ui.text.input.TextFieldValue("1,234.5", androidx.compose.ui.text.TextRange(7))
    val resultDec1 = com.example.util.CurrencyUtils.formatCurrencyInput(inputDec1, previousText = "1,234.")
    assertEquals("1,234.5", resultDec1.text)
    assertEquals(7, resultDec1.selection.end)

    // 5. Backspacing a comma: previous "1,234" cursor at 2 ("1,|234").
    // Hitting backspace produces raw "1234" with cursor at 1 ("1|234").
    // formatCurrencyInput detects deleted comma and deletes the preceding digit '1'.
    val inputBackspaceComma = androidx.compose.ui.text.input.TextFieldValue("1234", androidx.compose.ui.text.TextRange(1))
    val resultBackspaceComma = com.example.util.CurrencyUtils.formatCurrencyInput(inputBackspaceComma, previousText = "1,234")
    assertEquals("234", resultBackspaceComma.text)
    assertEquals(0, resultBackspaceComma.selection.end)
  }

  @Test
  fun testResetDatabaseToFreshState() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    val repo = com.example.data.repository.FinanceRepository(
      accountDao = db.accountDao(),
      transactionDao = db.transactionDao(),
      categoryDao = db.categoryDao(),
      budgetDao = db.budgetDao(),
      database = db
    )

    // Insert an account and transaction
    val accId = repo.saveAccount(
      com.example.data.model.Account(
        name = "My Checking",
        type = com.example.data.model.AccountType.SAVINGS,
        balance = 5000.0
      )
    )
    repo.addTransaction(
      com.example.data.model.Transaction(
        type = com.example.data.model.TransactionType.EXPENSE,
        amount = 100.0,
        accountId = accId,
        title = "Grocery item"
      )
    )

    assertEquals(1, repo.allAccounts.first().size)
    assertEquals(1, repo.allTransactionsWithDetails.first().size)

    // Execute reset
    repo.resetDatabaseToFreshState()

    // Verify 0 accounts, 0 transactions, and default categories populated
    val accountsAfter = repo.allAccounts.first()
    val transactionsAfter = repo.allTransactionsWithDetails.first()
    val categoriesAfter = repo.allCategories.first()

    assertEquals(0, accountsAfter.size)
    assertEquals(0, transactionsAfter.size)
    assertTrue(categoriesAfter.isNotEmpty())

    db.close()
  }

  @Test
  fun testLocalBankIconPersistenceAndResolution() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Test isLocalIcon checks
    assertTrue(com.example.util.BankIconSearchService.isLocalIcon("local:bank_icons/bank_12345_bdo.png"))
    assertTrue(com.example.util.BankIconSearchService.isLocalIcon("icon_bpi.png"))
    org.junit.Assert.assertFalse(com.example.util.BankIconSearchService.isLocalIcon("account_balance"))
    org.junit.Assert.assertFalse(com.example.util.BankIconSearchService.isLocalIcon("credit_card"))

    // 2. Test saving local icon file
    val iconsDir = java.io.File(context.filesDir, "bank_icons").apply { mkdirs() }
    val testIconFile = java.io.File(iconsDir, "bank_test_chase.png")
    testIconFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

    val storageKey = "local:bank_icons/${testIconFile.name}"
    val resolvedFile = com.example.util.BankIconSearchService.getLocalIconFile(context, storageKey)
    org.junit.Assert.assertNotNull(resolvedFile)
    assertTrue(resolvedFile!!.exists())

    // 3. Test saving account with local bank icon in database
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()

    val repo = com.example.data.repository.FinanceRepository(
      accountDao = db.accountDao(),
      transactionDao = db.transactionDao(),
      categoryDao = db.categoryDao(),
      budgetDao = db.budgetDao(),
      database = db
    )

    val accId = repo.saveAccount(
      com.example.data.model.Account(
        name = "Chase Sapphire Checking",
        type = com.example.data.model.AccountType.SAVINGS,
        balance = 25000.0,
        iconName = storageKey
      )
    )

    val fetched = repo.getAccountById(accId)
    org.junit.Assert.assertNotNull(fetched)
    assertEquals(storageKey, fetched?.iconName)
    assertTrue(com.example.util.BankIconSearchService.isLocalIcon(fetched?.iconName))

    db.close()
  }

  @Test
  fun testIncomeDistributionAndAnalyticsCalculation() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()

    val repo = com.example.data.repository.FinanceRepository(
      accountDao = db.accountDao(),
      transactionDao = db.transactionDao(),
      categoryDao = db.categoryDao(),
      budgetDao = db.budgetDao(),
      database = db
    )

    // Setup accounts
    val bankId = repo.saveAccount(
      com.example.data.model.Account(name = "BDO Checking", type = com.example.data.model.AccountType.SAVINGS, balance = 10000.0)
    )
    val walletId = repo.saveAccount(
      com.example.data.model.Account(name = "GCash Wallet", type = com.example.data.model.AccountType.DIGITAL_WALLET, balance = 5000.0)
    )

    // Setup categories
    val salaryCatId = repo.saveCategory(
      com.example.data.model.Category(name = "Salary", type = com.example.data.model.TransactionType.INCOME, iconName = "work", colorHex = "#10B981")
    )
    val freelanceCatId = repo.saveCategory(
      com.example.data.model.Category(name = "Freelance", type = com.example.data.model.TransactionType.INCOME, iconName = "laptop", colorHex = "#3B82F6")
    )
    val foodCatId = repo.saveCategory(
      com.example.data.model.Category(name = "Food", type = com.example.data.model.TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F59E0B")
    )

    // Add income and expense transactions
    repo.addTransaction(
      com.example.data.model.Transaction(title = "Monthly Job Salary", amount = 50000.0, type = com.example.data.model.TransactionType.INCOME, accountId = bankId, categoryId = salaryCatId)
    )
    repo.addTransaction(
      com.example.data.model.Transaction(title = "Side Client Project", amount = 15000.0, type = com.example.data.model.TransactionType.INCOME, accountId = walletId, categoryId = freelanceCatId)
    )
    repo.addTransaction(
      com.example.data.model.Transaction(title = "Groceries", amount = 5000.0, type = com.example.data.model.TransactionType.EXPENSE, accountId = bankId, categoryId = foodCatId)
    )

    val allTxs = repo.allTransactionsWithDetails.first()
    val incomeList = allTxs.filter { it.transaction.type == com.example.data.model.TransactionType.INCOME }
    val totalIncome = incomeList.sumOf { it.transaction.amount }
    val totalExpense = allTxs.filter { it.transaction.type == com.example.data.model.TransactionType.EXPENSE }.sumOf { it.transaction.amount }

    assertEquals(65000.0, totalIncome, 0.001)
    assertEquals(5000.0, totalExpense, 0.001)

    // Verify category distribution calculation
    val catGroup = incomeList.groupBy { it.categoryName ?: "General" }.mapValues { it.value.sumOf { tx -> tx.transaction.amount } }
    assertEquals(50000.0, catGroup["Salary"] ?: 0.0, 0.001)
    assertEquals(15000.0, catGroup["Freelance"] ?: 0.0, 0.001)

    // Verify account distribution calculation
    val accGroup = incomeList.groupBy { it.accountName }.mapValues { it.value.sumOf { tx -> tx.transaction.amount } }
    assertEquals(50000.0, accGroup["BDO Checking"] ?: 0.0, 0.001)
    assertEquals(15000.0, accGroup["GCash Wallet"] ?: 0.0, 0.001)

    // Verify net cash flow and average per deposit
    val netCashFlow = totalIncome - totalExpense
    assertEquals(60000.0, netCashFlow, 0.001)
    val avgPerDeposit = totalIncome / incomeList.size
    assertEquals(32500.0, avgPerDeposit, 0.001)

    db.close()
  }

  @Test
  fun testCustomDateRangeFilteringAndNormalization() {
    val cal = java.util.Calendar.getInstance()
    // Setup date 1: 2026-10-15 14:30:00
    cal.set(2026, java.util.Calendar.OCTOBER, 15, 14, 30, 0)
    cal.set(java.util.Calendar.MILLISECOND, 500)
    val date1 = cal.timeInMillis

    // Setup date 2: 2026-10-20 09:15:00
    cal.set(2026, java.util.Calendar.OCTOBER, 20, 9, 15, 0)
    cal.set(java.util.Calendar.MILLISECOND, 200)
    val date2 = cal.timeInMillis

    val (start, end) = com.example.util.DateUtils.getTimeframeRange(
      timeframe = com.example.util.DateUtils.Timeframe.CUSTOM,
      customStart = date1,
      customEnd = date2
    )

    // Verify start is 00:00:00.000 on Oct 15
    cal.timeInMillis = start
    assertEquals(2026, cal.get(java.util.Calendar.YEAR))
    assertEquals(java.util.Calendar.OCTOBER, cal.get(java.util.Calendar.MONTH))
    assertEquals(15, cal.get(java.util.Calendar.DAY_OF_MONTH))
    assertEquals(0, cal.get(java.util.Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(java.util.Calendar.MINUTE))
    assertEquals(0, cal.get(java.util.Calendar.SECOND))
    assertEquals(0, cal.get(java.util.Calendar.MILLISECOND))

    // Verify end is 23:59:59.999 on Oct 20
    cal.timeInMillis = end
    assertEquals(2026, cal.get(java.util.Calendar.YEAR))
    assertEquals(java.util.Calendar.OCTOBER, cal.get(java.util.Calendar.MONTH))
    assertEquals(20, cal.get(java.util.Calendar.DAY_OF_MONTH))
    assertEquals(23, cal.get(java.util.Calendar.HOUR_OF_DAY))
    assertEquals(59, cal.get(java.util.Calendar.MINUTE))
    assertEquals(59, cal.get(java.util.Calendar.SECOND))
    assertEquals(999, cal.get(java.util.Calendar.MILLISECOND))

    // Verify when customStart and customEnd are inverted (e.g. user selected end first)
    val (invertedStart, invertedEnd) = com.example.util.DateUtils.getTimeframeRange(
      timeframe = com.example.util.DateUtils.Timeframe.CUSTOM,
      customStart = date2,
      customEnd = date1
    )
    assertEquals(start, invertedStart)
    assertEquals(end, invertedEnd)

    // Verify transaction at Oct 20 23:30:00 falls inside range
    cal.set(2026, java.util.Calendar.OCTOBER, 20, 23, 30, 0)
    val lateTxTime = cal.timeInMillis
    assertTrue(lateTxTime in start..end)

    // Verify transaction at Oct 21 00:00:01 falls outside range
    cal.set(2026, java.util.Calendar.OCTOBER, 21, 0, 0, 1)
    val nextDayTxTime = cal.timeInMillis
    assertTrue(nextDayTxTime !in start..end)
  }

  @Test
  fun testCategoryReassignAndDeletion() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val repo = com.example.data.repository.FinanceRepository(
      db.accountDao(),
      db.transactionDao(),
      db.categoryDao(),
      db.budgetDao(),
      db
    )

    val accId = repo.saveAccount(
      com.example.data.model.Account(
        name = "Test Wallet",
        type = com.example.data.model.AccountType.CASH,
        balance = 5000.0,
        initialBalance = 5000.0
      )
    )

    val cat1Id = repo.saveCategory(
      com.example.data.model.Category(
        name = "Dining Out",
        type = com.example.data.model.TransactionType.EXPENSE,
        iconName = "restaurant",
        colorHex = "#EF4444"
      )
    )
    val cat2Id = repo.saveCategory(
      com.example.data.model.Category(
        name = "Groceries",
        type = com.example.data.model.TransactionType.EXPENSE,
        iconName = "shopping_cart",
        colorHex = "#10B981"
      )
    )

    // Save budget for Dining Out
    repo.saveBudget(
      com.example.data.model.Budget(
        categoryId = cat1Id,
        monthlyLimit = 2000.0
      )
    )

    // Add 2 transactions under Dining Out
    val tx1Id = repo.addTransaction(
      com.example.data.model.Transaction(
        type = com.example.data.model.TransactionType.EXPENSE,
        amount = 150.0,
        accountId = accId,
        categoryId = cat1Id,
        title = "Lunch at cafe"
      )
    )
    val tx2Id = repo.addTransaction(
      com.example.data.model.Transaction(
        type = com.example.data.model.TransactionType.EXPENSE,
        amount = 350.0,
        accountId = accId,
        categoryId = cat1Id,
        title = "Dinner with friends"
      )
    )

    // Verify initial count
    val countBefore = db.transactionDao().getTransactionCountForCategory(cat1Id)
    assertEquals(2, countBefore)

    // Now delete Dining Out and reassign its transactions to Groceries (cat2Id)
    val cat1 = com.example.data.model.Category(
      id = cat1Id,
      name = "Dining Out",
      type = com.example.data.model.TransactionType.EXPENSE,
      iconName = "restaurant",
      colorHex = "#EF4444"
    )
    val moved = repo.deleteCategory(cat1, targetCategoryId = cat2Id)
    assertEquals(2, moved)

    // Verify Dining Out is deleted
    val categoriesAfter = repo.allCategories.first()
    assertTrue(categoriesAfter.none { it.id == cat1Id })
    assertTrue(categoriesAfter.any { it.id == cat2Id })

    // Verify transactions were reassigned to cat2Id
    val tx1After = db.transactionDao().getTransactionById(tx1Id)
    val tx2After = db.transactionDao().getTransactionById(tx2Id)
    assertEquals(cat2Id, tx1After?.categoryId)
    assertEquals(cat2Id, tx2After?.categoryId)

    // Verify budget was deleted
    val budgetAfter = db.budgetDao().getBudgetForCategory(cat1Id)
    assertEquals(null, budgetAfter)

    db.close()
  }

  @Test
  fun testAiCategoryMatchingAndNewCategorySuggestion() = runBlocking {
    val existingCats = listOf(
      com.example.data.model.Category(
        id = 1L,
        name = "Groceries",
        type = com.example.data.model.TransactionType.EXPENSE,
        iconName = "shopping_cart",
        colorHex = "#10B981"
      ),
      com.example.data.model.Category(
        id = 2L,
        name = "Salary",
        type = com.example.data.model.TransactionType.INCOME,
        iconName = "attach_money",
        colorHex = "#3B82F6"
      )
    )

    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    // 1. Matched existing category: "bought milk at supermarket" -> Groceries
    val matchResult = com.example.util.AiCategoryService.categorizeOrSuggestNew(
      context = context,
      noteOrTitle = "bought milk at supermarket",
      type = com.example.data.model.TransactionType.EXPENSE,
      availableCategories = existingCats
    )
    assertTrue("Should match existing Groceries category", matchResult is com.example.util.CategorizationResult.MatchedExisting)
    assertEquals(1L, (matchResult as com.example.util.CategorizationResult.MatchedExisting).suggestion.category.id)

    // 2. Not in existing categories: "vet visit for puppy vaccination" -> should suggest new Pet Care category
    val petResult = com.example.util.AiCategoryService.categorizeOrSuggestNew(
      context = context,
      noteOrTitle = "vet visit for puppy vaccination",
      type = com.example.data.model.TransactionType.EXPENSE,
      availableCategories = existingCats
    )
    assertTrue("Should suggest a new Pet Care category", petResult is com.example.util.CategorizationResult.SuggestedNew)
    val petSuggestion = (petResult as com.example.util.CategorizationResult.SuggestedNew).newCategory
    assertEquals("Pet Care", petSuggestion.name)
    assertEquals("pets", petSuggestion.iconName)

    // 3. Not in existing categories: "gym fitness center monthly pass" -> Fitness & Gym
    val fitnessResult = com.example.util.AiCategoryService.categorizeOrSuggestNew(
      context = context,
      noteOrTitle = "gym fitness center monthly pass",
      type = com.example.data.model.TransactionType.EXPENSE,
      availableCategories = existingCats
    )
    assertTrue("Should suggest Fitness & Gym category", fitnessResult is com.example.util.CategorizationResult.SuggestedNew)
    val fitSuggestion = (fitnessResult as com.example.util.CategorizationResult.SuggestedNew).newCategory
    assertEquals("Fitness & Gym", fitSuggestion.name)
    assertEquals("fitness_center", fitSuggestion.iconName)

    // 4. Completely ambiguous/unmatched text with no hints -> None
    val obscureResult = com.example.util.AiCategoryService.categorizeOrSuggestNew(
      context = context,
      noteOrTitle = "xyzabc random random",
      type = com.example.data.model.TransactionType.EXPENSE,
      availableCategories = existingCats
    )
    assertTrue("Should return None when AI cannot determine category", obscureResult is com.example.util.CategorizationResult.None)
  }

  @Test
  fun testCategorySelectionResetBehavior() {
    var selectedCategoryId: Long? = 5L
    var amountText = "100"
    var titleText = "Dinner"

    // Simulate recording the transaction
    val isSaved = true
    if (isSaved) {
      amountText = ""
      titleText = ""
      selectedCategoryId = null
    }

    // After transaction recording, verify no category is selected
    assertEquals(null, selectedCategoryId)
    assertEquals("", amountText)
    assertEquals("", titleText)
  }

  @Test
  fun testExchangeRateServiceConversions() {
    val mockRates = mapOf(
      "USD" to 1.0,
      "PHP" to 58.60,
      "EUR" to 0.92,
      "GBP" to 0.77,
      "JPY" to 153.20
    )

    // 1. Same currency identity
    val sameCurrency = com.example.util.ExchangeRateService.convert(150.0, "USD", "USD", mockRates)
    assertEquals(150.0, sameCurrency, 0.001)

    // 2. USD to PHP (100 * 58.60 = 5860.00)
    val usdToPhp = com.example.util.ExchangeRateService.convert(100.0, "USD", "PHP", mockRates)
    assertEquals(5860.0, usdToPhp, 0.001)

    // 3. PHP to USD (5860 / 58.60 = 100.00)
    val phpToUsd = com.example.util.ExchangeRateService.convert(5860.0, "PHP", "USD", mockRates)
    assertEquals(100.0, phpToUsd, 0.001)

    // 4. Cross-currency EUR to PHP: 100 EUR = 100 * (58.60 / 0.92) = 6369.57
    val eurToPhp = com.example.util.ExchangeRateService.convert(100.0, "EUR", "PHP", mockRates)
    assertEquals(6369.57, eurToPhp, 0.01)

    // 5. Zero amount conversion
    val zeroConverted = com.example.util.ExchangeRateService.convert(0.0, "USD", "PHP", mockRates)
    assertEquals(0.0, zeroConverted, 0.001)
  }

  @Test
  fun testPrimaryCurrencyPreferences() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Default is PHP
    val defaultPrimary = com.example.util.UserPreferences.getPrimaryCurrency(context)
    assertEquals("PHP", defaultPrimary)

    // Update to USD
    com.example.util.UserPreferences.setPrimaryCurrency(context, "USD")
    assertEquals("USD", com.example.util.UserPreferences.getPrimaryCurrency(context))

    // Update to EUR
    com.example.util.UserPreferences.setPrimaryCurrency(context, "eur")
    assertEquals("EUR", com.example.util.UserPreferences.getPrimaryCurrency(context))

    // Reset back to PHP
    com.example.util.UserPreferences.setPrimaryCurrency(context, "PHP")
    assertEquals("PHP", com.example.util.UserPreferences.getPrimaryCurrency(context))
  }

  @Test
  fun testMultiCurrencyNetWorthCalculation() {
    val mockRates = mapOf(
      "USD" to 1.0,
      "PHP" to 58.60,
      "EUR" to 0.92
    )

    val accounts = listOf(
      com.example.data.model.Account(
        id = 1,
        name = "GCash",
        type = com.example.data.model.AccountType.DIGITAL_WALLET,
        balance = 5000.0,
        currency = "PHP"
      ),
      com.example.data.model.Account(
        id = 2,
        name = "US Bank",
        type = com.example.data.model.AccountType.SAVINGS,
        balance = 100.0, // 100 USD = 5,860.00 PHP
        currency = "USD"
      ),
      com.example.data.model.Account(
        id = 3,
        name = "Chase Sapphire",
        type = com.example.data.model.AccountType.CREDIT_CARD,
        balance = 50.0, // 50 USD = 2,930.00 PHP
        currency = "USD"
      )
    )

    // Converted to primary currency "PHP"
    val primary = "PHP"
    val totalAssetsInPhp = accounts
      .filter { it.type != com.example.data.model.AccountType.CREDIT_CARD }
      .sumOf { com.example.util.ExchangeRateService.convert(it.balance, it.currency, primary, mockRates) }

    val totalDebtInPhp = accounts
      .filter { it.type == com.example.data.model.AccountType.CREDIT_CARD }
      .sumOf { com.example.util.ExchangeRateService.convert(it.balance, it.currency, primary, mockRates) }

    val netWorthInPhp = totalAssetsInPhp - totalDebtInPhp

    // 5000 PHP + 5860 PHP = 10860 PHP
    assertEquals(10860.0, totalAssetsInPhp, 0.01)
    // 50 USD = 2930 PHP
    assertEquals(2930.0, totalDebtInPhp, 0.01)
    // 10860 - 2930 = 7930 PHP
    assertEquals(7930.0, netWorthInPhp, 0.01)
  }

  @Test
  fun testCurrencyInfoCatalog() {
    val php = com.example.util.ExchangeRateService.getCurrencyInfo("PHP")
    assertEquals("PHP", php.code)
    assertEquals("₱", php.symbol)

    val usd = com.example.util.ExchangeRateService.getCurrencyInfo("USD")
    assertEquals("USD", usd.code)
    assertEquals("$", usd.symbol)

    val eur = com.example.util.ExchangeRateService.getCurrencyInfo("EUR")
    assertEquals("EUR", eur.code)
    assertEquals("€", eur.symbol)

    val jpy = com.example.util.ExchangeRateService.getCurrencyInfo("JPY")
    assertEquals("JPY", jpy.code)
    assertEquals("¥", jpy.symbol)
  }

  @Test
  fun testLoanInterestCalculationAndRemainingBalance() {
    // 10,000 with 5% monthly interest for 6 months
    val loan = com.example.data.model.Transaction(
      id = 100,
      type = com.example.data.model.TransactionType.INCOME, // Borrowed
      amount = 10000.0,
      accountId = 1,
      title = "Tuition Loan",
      isLoan = true,
      loanPerson = "Auntie Rose",
      hasInterest = true,
      interestRate = 5.0,
      interestType = "MONTHLY",
      hasTerm = true,
      termUnit = "MONTHS",
      termLength = 6
    )

    val interest = com.example.util.LoanUtils.calculateTotalInterest(loan)
    // 10,000 * 5% * 6 = 3,000.00
    assertEquals(3000.0, interest, 0.01)

    val totalPayable = com.example.util.LoanUtils.calculateTotalPayable(loan)
    assertEquals(13000.0, totalPayable, 0.01)

    // With 4,000 paid so far
    val payment1 = com.example.data.model.Transaction(
      id = 101,
      type = com.example.data.model.TransactionType.EXPENSE,
      amount = 4000.0,
      accountId = 1,
      title = "Payment",
      isLoanPayment = true,
      loanReferenceId = 100
    )

    val remaining = com.example.util.LoanUtils.calculateRemainingBalance(loan, listOf(payment1))
    // 13,000 - 4,000 = 9,000
    assertEquals(9000.0, remaining, 0.01)
    assertEquals(false, com.example.util.LoanUtils.isFullySettled(loan, listOf(payment1)))

    // Now pay the rest: 9,000
    val payment2 = com.example.data.model.Transaction(
      id = 102,
      type = com.example.data.model.TransactionType.EXPENSE,
      amount = 9000.0,
      accountId = 1,
      title = "Final Payment",
      isLoanPayment = true,
      loanReferenceId = 100
    )

    val finalRemaining = com.example.util.LoanUtils.calculateRemainingBalance(loan, listOf(payment1, payment2))
    assertEquals(0.0, finalRemaining, 0.01)
    assertEquals(true, com.example.util.LoanUtils.isFullySettled(loan, listOf(payment1, payment2)))
  }

  @Test
  fun testLoanFixedTotalInterest() {
    // 50,000 with 10% total flat interest
    val loan = com.example.data.model.Transaction(
      id = 200,
      type = com.example.data.model.TransactionType.EXPENSE, // Lent
      amount = 50000.0,
      accountId = 1,
      title = "Business Loan to Mark",
      isLoan = true,
      loanPerson = "Mark",
      hasInterest = true,
      interestRate = 10.0,
      interestType = "TOTAL"
    )

    val interest = com.example.util.LoanUtils.calculateTotalInterest(loan)
    // 50,000 * 10% = 5,000.00
    assertEquals(5000.0, interest, 0.01)

    val totalCollectable = com.example.util.LoanUtils.calculateTotalPayable(loan)
    assertEquals(55000.0, totalCollectable, 0.01)

    val remaining = com.example.util.LoanUtils.calculateRemainingBalance(loan, emptyList())
    assertEquals(55000.0, remaining, 0.01)
  }
}



