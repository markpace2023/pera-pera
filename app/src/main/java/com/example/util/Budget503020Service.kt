package com.example.util

import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.BudgetGroup
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Detailed status for each 50/30/20 budget bucket:
 * - Needs (50% of monthly income)
 * - Wants (30% of monthly income)
 * - Savings & Debt (20% of monthly income)
 */
data class BudgetBucketStatus(
    val group: BudgetGroup,
    val targetRatio: Double,           // 0.50, 0.30, 0.20
    val allocatedLimit: Double,        // targetRatio * totalMonthlyIncome
    val usedAmount: Double,            // actual spent or allocated
    val remainingAmount: Double,       // allocatedLimit - usedAmount
    val usagePercentage: Double,       // (usedAmount / allocatedLimit) * 100
    val isOverBudget: Boolean,
    val isNearDepletion: Boolean,      // >= 80% used
    val memberCategories: List<Category>,
    val contributingTransactions: List<TransactionWithDetails>
) {
    val progressFraction: Float
        get() = if (allocatedLimit <= 0.0) {
            if (usedAmount > 0.0) 1.0f else 0.0f
        } else {
            (usedAmount / allocatedLimit).coerceIn(0.0, 1.0).toFloat()
        }
}

data class MonthlyBudget503020Summary(
    val monthYearLabel: String,
    val totalMonthlyIncome: Double,
    val needsStatus: BudgetBucketStatus,
    val wantsStatus: BudgetBucketStatus,
    val savingsDebtStatus: BudgetBucketStatus,
    val totalSpentAndSaved: Double,
    val netRemaining: Double,
    val hasIncome: Boolean,
    val incomeTransactions: List<TransactionWithDetails>
)

object Budget503020Service {

    /**
     * Determines if an account should be treated as a savings destination for the 50/30/20 rule.
     * Credit cards are strictly excluded per user specification.
     */
    fun isSavingsDestinationAccount(account: Account?): Boolean {
        if (account == null) return false
        if (account.type == AccountType.CREDIT_CARD) return false
        if (account.type == AccountType.SAVINGS) return true
        val nameLower = account.name.lowercase(Locale.ROOT)
        return nameLower.contains("saving") ||
                nameLower.contains("ipon") ||
                nameLower.contains("stash") ||
                nameLower.contains("emergency")
    }

    /**
     * Checks if a destination account name or account type is a credit card.
     * User rule: "transferring into credit card will not because credit card transactions is mixed of needs and wants"
     */
    fun isCreditCardAccount(account: Account?): Boolean {
        if (account == null) return false
        if (account.type == AccountType.CREDIT_CARD) return true
        val nameLower = account.name.lowercase(Locale.ROOT)
        return nameLower.contains("credit card") || nameLower.contains("cc ") || nameLower.endsWith(" cc")
    }

    /**
     * Returns the start and end timestamp of a specific calendar month.
     * Defaults to the current month if calendar is not specified.
     */
    fun getMonthRange(calendar: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        val startCal = calendar.clone() as Calendar
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        val endCal = calendar.clone() as Calendar
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)
        endCal.set(Calendar.MILLISECOND, 999)

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    /**
     * Computes the complete 50/30/20 auto-adjusting budget summary for a given month.
     */
    fun calculateMonthlyBudget(
        accounts: List<Account>,
        categories: List<Category>,
        transactions: List<TransactionWithDetails>,
        monthCalendar: Calendar = Calendar.getInstance(),
        primaryCurrency: String = "PHP",
        exchangeRates: Map<String, Double> = emptyMap()
    ): MonthlyBudget503020Summary {
        val (monthStart, monthEnd) = getMonthRange(monthCalendar)
        val monthYearLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCalendar.time)

        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        // Filter transactions within the month
        val monthTransactions = transactions.filter {
            it.transaction.timestamp in monthStart..monthEnd
        }

        // 1. Calculate Total Monthly Income (Salary 1, Salary 2, Freelance, Business, etc.)
        val incomeTxs = monthTransactions.filter { it.transaction.type == TransactionType.INCOME }
        val totalMonthlyIncome: Double = incomeTxs.sumOf { txWithDetails ->
            ExchangeRateService.convert(
                amount = txWithDetails.transaction.amount,
                fromCurrency = txWithDetails.accountCurrency,
                toCurrency = primaryCurrency,
                rates = exchangeRates
            )
        }

        // Shared budget limits auto-adjust with total income:
        // Needs = 50%, Wants = 30%, Savings & Debt = 20%
        val needsLimit = totalMonthlyIncome * 0.50
        val wantsLimit = totalMonthlyIncome * 0.30
        val savingsDebtLimit = totalMonthlyIncome * 0.20

        // 2. Classify transactions into the 3 buckets
        val needsTransactions = mutableListOf<TransactionWithDetails>()
        val wantsTransactions = mutableListOf<TransactionWithDetails>()
        val savingsDebtTransactions = mutableListOf<TransactionWithDetails>()

        for (item in monthTransactions) {
            val tx = item.transaction
            when (tx.type) {
                TransactionType.EXPENSE -> {
                    val category = tx.categoryId?.let { categoryMap[it] }

                    // Check if it's a loan payment or savings/debt category
                    val isLoanPayment = tx.isLoanPayment ||
                            category?.budgetGroup == BudgetGroup.SAVINGS_DEBT ||
                            category?.name.equals("Debt / Loan Payment", ignoreCase = true) ||
                            category?.name.equals("Loan / Money Lent", ignoreCase = true)

                    if (isLoanPayment) {
                        savingsDebtTransactions.add(item)
                    } else if (category?.budgetGroup == BudgetGroup.WANTS) {
                        wantsTransactions.add(item)
                    } else if (category?.budgetGroup == BudgetGroup.SAVINGS_DEBT) {
                        savingsDebtTransactions.add(item)
                    } else {
                        // Default to NEEDS for essential expenses
                        needsTransactions.add(item)
                    }
                }

                TransactionType.TRANSFER -> {
                    val toAccount = tx.toAccountId?.let { accountMap[it] }

                    // Per user instructions:
                    // 1) Transferring into credit card will NOT count (credit card transactions already categorized)
                    if (isCreditCardAccount(toAccount)) {
                        continue
                    }

                    // 2) Loan payment transfer counts into savings/debt
                    if (tx.isLoanPayment) {
                        savingsDebtTransactions.add(item)
                        continue
                    }

                    // 3) Transferring to savings account counts into savings/debt
                    if (isSavingsDestinationAccount(toAccount)) {
                        savingsDebtTransactions.add(item)
                    }
                }

                TransactionType.INCOME -> {
                    // Income is counted towards budget limits, not spent amounts
                }
            }
        }

        // Calculate used amounts in primary currency
        fun sumTransactions(txList: List<TransactionWithDetails>): Double {
            return txList.sumOf { item ->
                ExchangeRateService.convert(
                    amount = item.transaction.amount,
                    fromCurrency = item.accountCurrency,
                    toCurrency = primaryCurrency,
                    rates = exchangeRates
                )
            }
        }

        val needsUsed = sumTransactions(needsTransactions)
        val wantsUsed = sumTransactions(wantsTransactions)
        val savingsDebtUsed = sumTransactions(savingsDebtTransactions)

        // Categories grouped by budgetGroup
        val needsCategories = categories.filter { it.type == TransactionType.EXPENSE && it.budgetGroup == BudgetGroup.NEEDS }
        val wantsCategories = categories.filter { it.type == TransactionType.EXPENSE && it.budgetGroup == BudgetGroup.WANTS }
        val savingsDebtCategories = categories.filter { it.type == TransactionType.EXPENSE && it.budgetGroup == BudgetGroup.SAVINGS_DEBT }

        fun createStatus(
            group: BudgetGroup,
            ratio: Double,
            limit: Double,
            used: Double,
            cats: List<Category>,
            txList: List<TransactionWithDetails>
        ): BudgetBucketStatus {
            val remaining = limit - used
            val pct = if (limit > 0.0) (used / limit) * 100.0 else if (used > 0.0) 100.0 else 0.0
            val isOver = limit > 0.0 && used > limit
            val isNear = limit > 0.0 && pct >= 80.0 && !isOver

            return BudgetBucketStatus(
                group = group,
                targetRatio = ratio,
                allocatedLimit = limit,
                usedAmount = used,
                remainingAmount = remaining,
                usagePercentage = pct,
                isOverBudget = isOver,
                isNearDepletion = isNear,
                memberCategories = cats,
                contributingTransactions = txList
            )
        }

        val needsStatus = createStatus(BudgetGroup.NEEDS, 0.50, needsLimit, needsUsed, needsCategories, needsTransactions)
        val wantsStatus = createStatus(BudgetGroup.WANTS, 0.30, wantsLimit, wantsUsed, wantsCategories, wantsTransactions)
        val savingsDebtStatus = createStatus(BudgetGroup.SAVINGS_DEBT, 0.20, savingsDebtLimit, savingsDebtUsed, savingsDebtCategories, savingsDebtTransactions)

        val totalSpentAndSaved = needsUsed + wantsUsed + savingsDebtUsed
        val netRemaining = totalMonthlyIncome - totalSpentAndSaved

        return MonthlyBudget503020Summary(
            monthYearLabel = monthYearLabel,
            totalMonthlyIncome = totalMonthlyIncome,
            needsStatus = needsStatus,
            wantsStatus = wantsStatus,
            savingsDebtStatus = savingsDebtStatus,
            totalSpentAndSaved = totalSpentAndSaved,
            netRemaining = netRemaining,
            hasIncome = totalMonthlyIncome > 0.0,
            incomeTransactions = incomeTxs
        )
    }
}
