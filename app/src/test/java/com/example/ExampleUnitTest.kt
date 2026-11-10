package com.example

import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Category
import com.example.data.model.StatementDateType
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import com.example.util.CsvUtils
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {

    @Test
    fun testUnionBankVariableStatementDateCalculation() {
        // Test base cutoff of 18th in UB rule:
        // In a 31-day month (e.g. October, month 9 zero-indexed):
        val ubAccount = Account(
            name = "UB Visa",
            type = AccountType.CREDIT_CARD,
            balance = 500.0,
            statementDateType = StatementDateType.VARIABLE_MONTH_LENGTH,
            statementDay = 18
        )

        // October (31 days) -> cutoff should be 18
        val dayOct = ubAccount.calculateCutoffDay(2026, Calendar.OCTOBER)
        assertEquals(18, dayOct)

        // November (30 days) -> cutoff should be 18 - (31 - 30) = 17
        val dayNov = ubAccount.calculateCutoffDay(2026, Calendar.NOVEMBER)
        assertEquals(17, dayNov)

        // February 2026 (non-leap year, 28 days) -> cutoff should be 18 - (31 - 28) = 15
        val dayFeb = ubAccount.calculateCutoffDay(2026, Calendar.FEBRUARY)
        assertEquals(15, dayFeb)
    }

    @Test
    fun testFixedStatementDateCalculation() {
        val fixedAccount = Account(
            name = "Citi Card",
            type = AccountType.CREDIT_CARD,
            balance = 200.0,
            statementDateType = StatementDateType.FIXED,
            statementDay = 15
        )

        val dayOct = fixedAccount.calculateCutoffDay(2026, Calendar.OCTOBER)
        val dayNov = fixedAccount.calculateCutoffDay(2026, Calendar.NOVEMBER)
        val dayFeb = fixedAccount.calculateCutoffDay(2026, Calendar.FEBRUARY)

        assertEquals(15, dayOct)
        assertEquals(15, dayNov)
        assertEquals(15, dayFeb)
    }

    @Test
    fun testCsvExportAndImportRoundTrip() {
        val testItem = TransactionWithDetails(
            transaction = Transaction(
                id = 101,
                type = TransactionType.EXPENSE,
                amount = 45.50,
                accountId = 1,
                title = "Dinner, with \"Friends\"",
                note = "Italian pasta",
                timestamp = 1761234567000L
            ),
            accountName = "Cash Wallet",
            accountIcon = "local_atm",
            categoryName = "Food & Dining",
            categoryIcon = "restaurant",
            categoryColor = "#F59E0B"
        )

        val csv = CsvUtils.exportTransactionsToCsv(listOf(testItem))
        assertTrue(csv.contains("Dinner, with \"\"Friends\"\""))
        assertTrue(csv.contains("45.50"))

        val parsed = CsvUtils.parseTransactionsCsv(csv)
        assertEquals(1, parsed.size)
        val firstRow = parsed[0]
        assertEquals(TransactionType.EXPENSE, firstRow.type)
        assertEquals(45.50, firstRow.amount, 0.001)
        assertEquals("Dinner, with \"Friends\"", firstRow.title)
        assertEquals("Cash Wallet", firstRow.accountName)
        assertEquals("Food & Dining", firstRow.categoryName)
    }

    @Test
    fun testTimeframeRanges() {
        val (startMonth, endMonth) = DateUtils.getTimeframeRange(DateUtils.Timeframe.MONTHLY)
        assertTrue(endMonth > startMonth)

        val (startDay, endDay) = DateUtils.getTimeframeRange(DateUtils.Timeframe.DAILY)
        assertTrue(endDay > startDay)
        val diffHours = (endDay - startDay) / (1000 * 3600)
        assertEquals(23, diffHours)
    }

    @Test
    fun testCurrencyFormatting() {
        val formattedPhp = DateUtils.formatCurrency(1500.50)
        assertTrue("Formatted currency should contain Peso symbol ₱", formattedPhp.contains("₱"))
        assertTrue("Formatted currency should contain number 1,500.50", formattedPhp.contains("1,500.50"))
        assertEquals("₱", formattedPhp.substring(0, 1))

        // Negative currency test
        val formattedNegative = DateUtils.formatCurrency(-250.75)
        assertEquals("-₱250.75", formattedNegative)

        // Default currency code
        assertEquals("PHP", DateUtils.getDefaultCurrencyCode())
    }

    @Test
    fun testAiCategoryPrediction() {
        val categories = listOf(
            Category(id = 1, name = "Food & Dining", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#EF4444"),
            Category(id = 2, name = "Transportation", type = TransactionType.EXPENSE, iconName = "directions_car", colorHex = "#3B82F6"),
            Category(id = 3, name = "Salary", type = TransactionType.INCOME, iconName = "payments", colorHex = "#10B981"),
            Category(id = 4, name = "Loan / Borrowed Money", type = TransactionType.INCOME, iconName = "handshake", colorHex = "#F59E0B"),
            Category(id = 5, name = "Loan / Money Lent", type = TransactionType.EXPENSE, iconName = "handshake", colorHex = "#8B5CF6")
        )

        val foodPrediction = com.example.util.AiCategoryService.predictOffline(
            noteOrTitle = "Dinner at Jollibee with cousins",
            type = TransactionType.EXPENSE,
            availableCategories = categories
        )
        assertNotNull(foodPrediction)
        assertEquals("Food & Dining", foodPrediction?.name)

        val borrowPrediction = com.example.util.AiCategoryService.predictOffline(
            noteOrTitle = "Borrowed money from kuya Mark for emergency",
            type = TransactionType.INCOME,
            availableCategories = categories
        )
        assertNotNull(borrowPrediction)
        assertEquals("Loan / Borrowed Money", borrowPrediction?.name)

        val lendPrediction = com.example.util.AiCategoryService.predictOffline(
            noteOrTitle = "Lent cash to Maria for tuition",
            type = TransactionType.EXPENSE,
            availableCategories = categories
        )
        assertNotNull(lendPrediction)
        assertEquals("Loan / Money Lent", lendPrediction?.name)
    }

    @Test
    fun testLoanTransactionModel() {
        val loanTx = Transaction(
            id = 201,
            type = TransactionType.INCOME,
            amount = 10000.0,
            accountId = 1,
            title = "Emergency Loan",
            note = "For medical bill",
            isLoan = true,
            loanPerson = "Tito Danny",
            loanDueDate = 1795000000000L,
            hasInterest = true,
            interestRate = 3.5,
            interestType = "monthly",
            hasTerm = true,
            termUnit = "months",
            termLength = 6,
            isLoanSettled = false
        )

        assertTrue(loanTx.isLoan)
        assertEquals("Tito Danny", loanTx.loanPerson)
        assertEquals(3.5, loanTx.interestRate ?: 0.0, 0.001)
        assertEquals("monthly", loanTx.interestType)
        assertEquals("months", loanTx.termUnit)
        assertEquals(6, loanTx.termLength)
        assertEquals(false, loanTx.isLoanSettled)

        val settledTx = loanTx.copy(isLoanSettled = true)
        assertTrue(settledTx.isLoanSettled)
    }

    @Test
    fun testMostRecentlyUsedAccountSorting() {
        val a1 = Account(id = 1, name = "Cash", type = AccountType.CASH, balance = 100.0)
        val a2 = Account(id = 2, name = "Bank", type = AccountType.SAVINGS, balance = 200.0)
        val a3 = Account(id = 3, name = "E-Wallet", type = AccountType.DIGITAL_WALLET, balance = 300.0)
        val a4 = Account(id = 4, name = "Credit Card", type = AccountType.CREDIT_CARD, balance = 400.0)
        val accounts = listOf(a1, a2, a3, a4)

        // Empty recents -> preserved original order
        var sorted = com.example.util.UserPreferences.sortAccountsByRecency(accounts, emptyList())
        assertEquals(listOf(1L, 2L, 3L, 4L), sorted.map { it.id })

        // Transaction 1 picks account 3 -> recents = [3]
        sorted = com.example.util.UserPreferences.sortAccountsByRecency(accounts, listOf(3L))
        assertEquals(listOf(3L, 1L, 2L, 4L), sorted.map { it.id })

        // Transaction 2 picks account 2 -> recents = [2, 3] (2 is 1st, 3 is 2nd)
        sorted = com.example.util.UserPreferences.sortAccountsByRecency(accounts, listOf(2L, 3L))
        assertEquals(listOf(2L, 3L, 1L, 4L), sorted.map { it.id })

        // Transaction 3 picks account 3 again -> recents = [3, 2] (3 moves back to 1st, 2 is 2nd)
        sorted = com.example.util.UserPreferences.sortAccountsByRecency(accounts, listOf(3L, 2L))
        assertEquals(listOf(3L, 2L, 1L, 4L), sorted.map { it.id })

        // Transaction 4 picks account 4 -> recents = [4, 3, 2] (4 is 1st, 3 is 2nd, 2 is 3rd)
        sorted = com.example.util.UserPreferences.sortAccountsByRecency(accounts, listOf(4L, 3L, 2L))
        assertEquals(listOf(4L, 3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun testMostRecentlyUsedCategorySorting() {
        val c1 = Category(id = 10, name = "Food", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#10B981")
        val c2 = Category(id = 20, name = "Bills", type = TransactionType.EXPENSE, iconName = "receipt", colorHex = "#EF4444")
        val c3 = Category(id = 30, name = "Shopping", type = TransactionType.EXPENSE, iconName = "shopping_cart", colorHex = "#3B82F6")
        val c4 = Category(id = 40, name = "Transport", type = TransactionType.EXPENSE, iconName = "directions_car", colorHex = "#8B5CF6")
        val categories = listOf(c1, c2, c3, c4)

        // Transaction 1 picks category 30 -> recents = [30]
        var sorted = com.example.util.UserPreferences.sortCategoriesByRecency(categories, listOf(30L))
        assertEquals(listOf(30L, 10L, 20L, 40L), sorted.map { it.id })

        // Transaction 2 picks category 20 -> recents = [20, 30] (20 is 1st, 30 is 2nd)
        sorted = com.example.util.UserPreferences.sortCategoriesByRecency(categories, listOf(20L, 30L))
        assertEquals(listOf(20L, 30L, 10L, 40L), sorted.map { it.id })

        // Transaction 3 picks category 10 -> recents = [10, 20, 30] (10 is 1st, 20 is 2nd, 30 is 3rd)
        sorted = com.example.util.UserPreferences.sortCategoriesByRecency(categories, listOf(10L, 20L, 30L))
        assertEquals(listOf(10L, 20L, 30L, 40L), sorted.map { it.id })

        // Transaction 4 picks category 30 again -> recents = [30, 10, 20] (30 moves back to 1st)
        sorted = com.example.util.UserPreferences.sortCategoriesByRecency(categories, listOf(30L, 10L, 20L))
        assertEquals(listOf(30L, 10L, 20L, 40L), sorted.map { it.id })
    }

    @Test
    fun testCategoryMatchingFromPastTransactions() {
        val catFood = Category(id = 1, name = "Food & Dining", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F59E0B")
        val catShopping = Category(id = 2, name = "Shopping", type = TransactionType.EXPENSE, iconName = "shopping_bag", colorHex = "#EC4899")
        val categories = listOf(catFood, catShopping)

        val pastTx = Transaction(
            id = 101,
            type = TransactionType.EXPENSE,
            amount = 250.0,
            accountId = 1,
            categoryId = 1,
            title = "Mang Inasal Barkada Meal",
            note = "Chicken inasal and extra rice",
            timestamp = 1760000000000L
        )

        // Matching note or title present in recent transactions -> uses past transaction's category
        val matchByTitle = com.example.util.CategoryMatchingService.matchCategory(
            noteOrTitle = "Mang Inasal Barkada Meal",
            type = TransactionType.EXPENSE,
            availableCategories = categories,
            recentTransactions = listOf(pastTx)
        )
        assertTrue(matchByTitle is com.example.util.CategoryMatchResult.SingleMatch)
        assertEquals(1L, (matchByTitle as com.example.util.CategoryMatchResult.SingleMatch).category.id)

        // Match by note from past transactions
        val matchByNote = com.example.util.CategoryMatchingService.matchCategory(
            noteOrTitle = "Chicken inasal and extra rice",
            type = TransactionType.EXPENSE,
            availableCategories = categories,
            recentTransactions = listOf(pastTx)
        )
        assertTrue(matchByNote is com.example.util.CategoryMatchResult.SingleMatch)
        assertEquals(1L, (matchByNote as com.example.util.CategoryMatchResult.SingleMatch).category.id)
    }

    @Test
    fun testCategoryMatchingKeywordsSingleAndMultiple() {
        val catFood = Category(id = 1, name = "Food & Dining", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F59E0B")
        val catShopping = Category(id = 2, name = "Shopping", type = TransactionType.EXPENSE, iconName = "shopping_bag", colorHex = "#EC4899")
        val catBills = Category(id = 3, name = "Bills & Utilities", type = TransactionType.EXPENSE, iconName = "receipt_long", colorHex = "#EF4444")
        val categories = listOf(catFood, catShopping, catBills)

        // 1. Single keyword match -> SingleMatch
        val singleResult = com.example.util.CategoryMatchingService.matchCategory(
            noteOrTitle = "Starbucks latte",
            type = TransactionType.EXPENSE,
            availableCategories = categories,
            recentTransactions = emptyList()
        )
        assertTrue(singleResult is com.example.util.CategoryMatchResult.SingleMatch)
        assertEquals(1L, (singleResult as com.example.util.CategoryMatchResult.SingleMatch).category.id)

        // 2. Multiple keyword match (e.g. coffee and shoes) -> MultipleMatches
        val multiResult = com.example.util.CategoryMatchingService.matchCategory(
            noteOrTitle = "Coffee and shoes",
            type = TransactionType.EXPENSE,
            availableCategories = categories,
            recentTransactions = emptyList()
        )
        assertTrue(multiResult is com.example.util.CategoryMatchResult.MultipleMatches)
        val matchedIds = (multiResult as com.example.util.CategoryMatchResult.MultipleMatches).matchedCategories.map { it.id }
        assertTrue(matchedIds.contains(1L)) // Food
        assertTrue(matchedIds.contains(2L)) // Shopping

        // 3. No match
        val noMatchResult = com.example.util.CategoryMatchingService.matchCategory(
            noteOrTitle = "xyzrandomnonexistentquery123",
            type = TransactionType.EXPENSE,
            availableCategories = categories,
            recentTransactions = emptyList()
        )
        assertTrue(noMatchResult is com.example.util.CategoryMatchResult.NoMatch)
    }

    @Test
    fun testDynamicKeywordLearningFromTransactions() {
        val catFood = Category(id = 1, name = "Food & Dining", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F59E0B")
        val catSports = Category(id = 2, name = "Sports", type = TransactionType.EXPENSE, iconName = "fitness_center", colorHex = "#3B82F6")
        val categories = listOf(catFood, catSports)

        // Historical transaction with custom store / brand name not in predefined keywords
        val pastTx = Transaction(
            id = 50,
            type = TransactionType.EXPENSE,
            amount = 1200.0,
            accountId = 1,
            categoryId = 2, // Sports
            title = "Decathlon badminton racket",
            note = "Bought sports grip tape",
            timestamp = 1760000000000L
        )

        // Query does NOT equal past title, but contains learned keyword "Decathlon"
        val result = com.example.util.CategoryMatchingService.matchCategory(
            noteOrTitle = "Decathlon shoes",
            type = TransactionType.EXPENSE,
            availableCategories = categories,
            recentTransactions = listOf(pastTx)
        )

        // "Decathlon" is learned from pastTx into Category 2 (Sports)!
        assertTrue(result is com.example.util.CategoryMatchResult.SingleMatch)
        assertEquals(2L, (result as com.example.util.CategoryMatchResult.SingleMatch).category.id)
    }

    @Test
    fun test503020AutoAdjustingWithFirstAndSecondSalary() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.NOVEMBER, 1, 10, 0, 0)
        val day1 = calendar.timeInMillis
        calendar.set(2026, Calendar.NOVEMBER, 5, 12, 0, 0)
        val day5 = calendar.timeInMillis
        calendar.set(2026, Calendar.NOVEMBER, 15, 10, 0, 0)
        val day15 = calendar.timeInMillis
        calendar.set(2026, Calendar.NOVEMBER, 20, 10, 0, 0)
        val day20 = calendar.timeInMillis

        val checkingAcc = Account(id = 1, name = "Checking Account", type = AccountType.DEBIT_CARD, balance = 50000.0)
        val accounts = listOf(checkingAcc)

        val catGroceries = Category(id = 10, name = "Groceries", type = TransactionType.EXPENSE, iconName = "shopping_cart", colorHex = "#10B981", budgetGroup = com.example.data.model.BudgetGroup.NEEDS)
        val catDining = Category(id = 11, name = "Dining Out", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F59E0B", budgetGroup = com.example.data.model.BudgetGroup.WANTS)
        val categories = listOf(catGroceries, catDining)

        // Day 1: 1st Salary arrives = ₱30,000
        val txSalary1 = TransactionWithDetails(
            transaction = Transaction(id = 1, type = TransactionType.INCOME, amount = 30000.0, accountId = 1, title = "1st Cutoff Salary", timestamp = day1),
            accountName = "Checking Account",
            accountIcon = "account_balance"
        )

        // Day 5: Spend ₱8,000 on Groceries (Needs)
        val txGroceries = TransactionWithDetails(
            transaction = Transaction(id = 2, type = TransactionType.EXPENSE, amount = 8000.0, accountId = 1, categoryId = 10, title = "Supermarket", timestamp = day5),
            accountName = "Checking Account",
            accountIcon = "account_balance",
            categoryName = "Groceries"
        )

        val budgetStage1 = com.example.util.Budget503020Service.calculateMonthlyBudget(
            accounts = accounts,
            categories = categories,
            transactions = listOf(txSalary1, txGroceries),
            monthCalendar = calendar
        )

        // Check Stage 1 limits & used
        assertEquals(30000.0, budgetStage1.totalMonthlyIncome, 0.01)
        assertEquals(15000.0, budgetStage1.needsStatus.allocatedLimit, 0.01) // 50%
        assertEquals(9000.0, budgetStage1.wantsStatus.allocatedLimit, 0.01)  // 30%
        assertEquals(6000.0, budgetStage1.savingsDebtStatus.allocatedLimit, 0.01) // 20%
        assertEquals(8000.0, budgetStage1.needsStatus.usedAmount, 0.01) // ₱8k spent
        assertEquals(7000.0, budgetStage1.needsStatus.remainingAmount, 0.01) // ₱15k - ₱8k = ₱7k

        // Day 15: 2nd Salary arrives = +₱30,000 (Total ₱60,000)
        val txSalary2 = TransactionWithDetails(
            transaction = Transaction(id = 3, type = TransactionType.INCOME, amount = 30000.0, accountId = 1, title = "2nd Cutoff Salary", timestamp = day15),
            accountName = "Checking Account",
            accountIcon = "account_balance"
        )

        val budgetStage2 = com.example.util.Budget503020Service.calculateMonthlyBudget(
            accounts = accounts,
            categories = categories,
            transactions = listOf(txSalary1, txGroceries, txSalary2),
            monthCalendar = calendar
        )

        // Check Stage 2: Limits auto-adjust, but used budget stays intact!
        assertEquals(60000.0, budgetStage2.totalMonthlyIncome, 0.01)
        assertEquals(30000.0, budgetStage2.needsStatus.allocatedLimit, 0.01) // 50% of 60k
        assertEquals(18000.0, budgetStage2.wantsStatus.allocatedLimit, 0.01) // 30% of 60k
        assertEquals(12000.0, budgetStage2.savingsDebtStatus.allocatedLimit, 0.01) // 20% of 60k
        assertEquals(8000.0, budgetStage2.needsStatus.usedAmount, 0.01) // Used stays ₱8k!
        assertEquals(22000.0, budgetStage2.needsStatus.remainingAmount, 0.01) // ₱30k - ₱8k = ₱22k remaining!

        // Day 20: Freelance salary arrives = +₱10,000 (Total ₱70,000)
        val txFreelance = TransactionWithDetails(
            transaction = Transaction(id = 4, type = TransactionType.INCOME, amount = 10000.0, accountId = 1, title = "Freelance UI Project", timestamp = day20),
            accountName = "Checking Account",
            accountIcon = "account_balance"
        )

        val budgetStage3 = com.example.util.Budget503020Service.calculateMonthlyBudget(
            accounts = accounts,
            categories = categories,
            transactions = listOf(txSalary1, txGroceries, txSalary2, txFreelance),
            monthCalendar = calendar
        )

        // Check Stage 3: Auto-adjusts again!
        assertEquals(70000.0, budgetStage3.totalMonthlyIncome, 0.01)
        assertEquals(35000.0, budgetStage3.needsStatus.allocatedLimit, 0.01) // 50% of 70k
        assertEquals(21000.0, budgetStage3.wantsStatus.allocatedLimit, 0.01) // 30% of 70k
        assertEquals(14000.0, budgetStage3.savingsDebtStatus.allocatedLimit, 0.01) // 20% of 70k
        assertEquals(8000.0, budgetStage3.needsStatus.usedAmount, 0.01) // Used still stays ₱8k!
        assertEquals(27000.0, budgetStage3.needsStatus.remainingAmount, 0.01)
    }

    @Test
    fun testTransferToSavingsAndLoanPaymentCountsIntoSavingsDebt() {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        val checkingAcc = Account(id = 1, name = "BDO Checking", type = AccountType.DEBIT_CARD, balance = 80000.0)
        val savingsAcc = Account(id = 2, name = "Maya High-Yield Savings", type = AccountType.SAVINGS, balance = 20000.0)
        val ccAcc = Account(id = 3, name = "BPI Gold Credit Card", type = AccountType.CREDIT_CARD, balance = 0.0)
        val accounts = listOf(checkingAcc, savingsAcc, ccAcc)

        val catSavings = Category(id = 20, name = "Investments", type = TransactionType.EXPENSE, iconName = "trending_up", colorHex = "#8B5CF6", budgetGroup = com.example.data.model.BudgetGroup.SAVINGS_DEBT)
        val categories = listOf(catSavings)

        // Monthly Income = ₱50,000
        val txIncome = TransactionWithDetails(
            transaction = Transaction(id = 1, type = TransactionType.INCOME, amount = 50000.0, accountId = 1, title = "Salary", timestamp = now),
            accountName = "BDO Checking",
            accountIcon = "account_balance"
        )

        // Transfer ₱10,000 to Maya Savings -> COUNTS towards Savings & Debt!
        val txTransferSavings = TransactionWithDetails(
            transaction = Transaction(id = 2, type = TransactionType.TRANSFER, amount = 10000.0, accountId = 1, toAccountId = 2, title = "Transfer to Savings", timestamp = now),
            accountName = "BDO Checking",
            toAccountName = "Maya High-Yield Savings",
            accountIcon = "account_balance"
        )

        // Transfer ₱7,000 to BPI Credit Card -> DOES NOT count towards Savings & Debt per user rule!
        val txTransferCC = TransactionWithDetails(
            transaction = Transaction(id = 3, type = TransactionType.TRANSFER, amount = 7000.0, accountId = 1, toAccountId = 3, title = "Pay CC Bill", timestamp = now),
            accountName = "BDO Checking",
            toAccountName = "BPI Gold Credit Card",
            accountIcon = "account_balance"
        )

        // Loan payment of ₱3,000 -> COUNTS towards Savings & Debt!
        val txLoanPayment = TransactionWithDetails(
            transaction = Transaction(id = 4, type = TransactionType.EXPENSE, amount = 3000.0, accountId = 1, isLoanPayment = true, title = "Car Loan Payment", timestamp = now),
            accountName = "BDO Checking",
            accountIcon = "account_balance"
        )

        val budget = com.example.util.Budget503020Service.calculateMonthlyBudget(
            accounts = accounts,
            categories = categories,
            transactions = listOf(txIncome, txTransferSavings, txTransferCC, txLoanPayment),
            monthCalendar = calendar
        )

        assertEquals(50000.0, budget.totalMonthlyIncome, 0.01)
        assertEquals(10000.0, budget.savingsDebtStatus.allocatedLimit, 0.01) // 20% of 50k = 10k

        // Savings & Debt used should be ₱10,000 (savings transfer) + ₱3,000 (loan payment) = ₱13,000.
        // Credit card transfer of ₱7,000 is excluded!
        assertEquals(13000.0, budget.savingsDebtStatus.usedAmount, 0.01)
        assertTrue(budget.savingsDebtStatus.isOverBudget) // 13k > 10k
    }

    @Test
    fun testTransferFromSavingsToSavingsDoesNotCountIntoSavingsDebt() {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        val checkingAcc = Account(id = 1, name = "Checking Account", type = AccountType.DEBIT_CARD, balance = 50000.0)
        val savingsAcc1 = Account(id = 2, name = "Emergency Savings", type = AccountType.SAVINGS, balance = 30000.0)
        val savingsAcc2 = Account(id = 3, name = "High-Yield Stash", type = AccountType.SAVINGS, balance = 10000.0)
        val accounts = listOf(checkingAcc, savingsAcc1, savingsAcc2)

        // Monthly Income = ₱50,000
        val txIncome = TransactionWithDetails(
            transaction = Transaction(id = 1, type = TransactionType.INCOME, amount = 50000.0, accountId = 1, title = "Salary", timestamp = now),
            accountName = "Checking Account",
            accountIcon = "account_balance"
        )

        // Transfer ₱5,000 from Checking to Emergency Savings -> COUNTS towards Savings & Debt!
        val txCheckingToSavings = TransactionWithDetails(
            transaction = Transaction(id = 2, type = TransactionType.TRANSFER, amount = 5000.0, accountId = 1, toAccountId = 2, title = "Save to Emergency", timestamp = now),
            accountName = "Checking Account",
            toAccountName = "Emergency Savings",
            accountIcon = "account_balance"
        )

        // Transfer ₱4,000 from Emergency Savings to High-Yield Stash (Savings -> Savings) -> DOES NOT COUNT per user rule!
        val txSavingsToSavings = TransactionWithDetails(
            transaction = Transaction(id = 3, type = TransactionType.TRANSFER, amount = 4000.0, accountId = 2, toAccountId = 3, title = "Move between savings", timestamp = now),
            accountName = "Emergency Savings",
            toAccountName = "High-Yield Stash",
            accountIcon = "account_balance"
        )

        val budget = com.example.util.Budget503020Service.calculateMonthlyBudget(
            accounts = accounts,
            categories = emptyList(),
            transactions = listOf(txIncome, txCheckingToSavings, txSavingsToSavings),
            monthCalendar = calendar
        )

        assertEquals(50000.0, budget.totalMonthlyIncome, 0.01)
        assertEquals(10000.0, budget.savingsDebtStatus.allocatedLimit, 0.01)

        // Only the ₱5,000 deposit from checking counts. The ₱4,000 savings-to-savings transfer is excluded!
        assertEquals(5000.0, budget.savingsDebtStatus.usedAmount, 0.01)
        assertEquals(5000.0, budget.savingsDebtStatus.remainingAmount, 0.01)
    }

    @Test
    fun testIncomeDirectlyIntoSavingsAccountCountsTowardsSavingsDebtCounter() {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        val checkingAcc = Account(id = 1, name = "Checking Account", type = AccountType.DEBIT_CARD, balance = 50000.0)
        val savingsAcc = Account(id = 2, name = "High-Yield Savings", type = AccountType.SAVINGS, balance = 20000.0)
        val accounts = listOf(checkingAcc, savingsAcc)

        // 1. Regular salary income deposited into Checking Account (₱40,000)
        val txSalaryChecking = TransactionWithDetails(
            transaction = Transaction(id = 1, type = TransactionType.INCOME, amount = 40000.0, accountId = 1, title = "Salary in Checking", timestamp = now),
            accountName = "Checking Account",
            accountIcon = "account_balance"
        )

        // 2. Direct income into Savings Account: e.g. Monthly Bank Interest or Direct Bonus (₱2,500)
        val txInterestSavings = TransactionWithDetails(
            transaction = Transaction(id = 2, type = TransactionType.INCOME, amount = 2500.0, accountId = 2, title = "Savings Interest Earned", timestamp = now),
            accountName = "High-Yield Savings",
            accountIcon = "savings"
        )

        val budget = com.example.util.Budget503020Service.calculateMonthlyBudget(
            accounts = accounts,
            categories = emptyList(),
            transactions = listOf(txSalaryChecking, txInterestSavings),
            monthCalendar = calendar
        )

        // Total Monthly Income = 40,000 + 2,500 = 42,500
        assertEquals(42500.0, budget.totalMonthlyIncome, 0.01)

        // 20% Savings/Debt Limit = 42,500 * 0.20 = 8,500
        assertEquals(8500.0, budget.savingsDebtStatus.allocatedLimit, 0.01)

        // The ₱2,500 income deposited directly into savings account is automatically added to the savings counter!
        assertEquals(2500.0, budget.savingsDebtStatus.usedAmount, 0.01)
        assertEquals(6000.0, budget.savingsDebtStatus.remainingAmount, 0.01)
    }
}


