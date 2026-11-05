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
}


