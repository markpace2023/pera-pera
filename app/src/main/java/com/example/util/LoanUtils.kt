package com.example.util

import com.example.data.model.Transaction
import kotlin.math.max

/**
 * Utility helper for calculating debt and loan interest, total expected repayment,
 * and remaining balances to pay or collect.
 */
object LoanUtils {

    /**
     * Calculates the total interest amount for a loan or debt.
     *
     * @param principal The original loan/borrowed principal amount
     * @param hasInterest Whether interest applies
     * @param interestRate The numeric interest rate percentage (e.g. 5.0 for 5%)
     * @param interestType The rate frequency: "MONTHLY", "ANNUAL", or "TOTAL" (Fixed)
     * @param hasTerm Whether a loan term duration was specified
     * @param termUnit The term unit: "MONTHS", "YEARS", "WEEKS", "DAYS"
     * @param termLength The duration count (e.g. 6 for 6 months)
     * @param startDate The transaction timestamp (loan inception)
     * @param dueDate Optional due date timestamp
     * @return Calculated interest amount rounded to 2 decimal places
     */
    fun calculateTotalInterest(
        principal: Double,
        hasInterest: Boolean,
        interestRate: Double?,
        interestType: String = "MONTHLY",
        hasTerm: Boolean = false,
        termUnit: String = "MONTHS",
        termLength: Int = 1,
        startDate: Long = System.currentTimeMillis(),
        dueDate: Long? = null
    ): Double {
        if (!hasInterest || interestRate == null || interestRate <= 0.0 || principal <= 0.0) {
            return 0.0
        }

        val rateDecimal = interestRate / 100.0

        val interest = when (interestType.uppercase()) {
            "TOTAL" -> {
                // Fixed flat rate on the entire loan regardless of duration
                principal * rateDecimal
            }
            "ANNUAL" -> {
                // Annual percentage rate (APR)
                val years = if (hasTerm && termLength > 0) {
                    when (termUnit.uppercase()) {
                        "YEARS" -> termLength.toDouble()
                        "MONTHS" -> termLength / 12.0
                        "WEEKS" -> termLength / 52.0
                        "DAYS" -> termLength / 365.0
                        else -> termLength / 12.0
                    }
                } else if (dueDate != null && dueDate > startDate) {
                    val days = (dueDate - startDate).toDouble() / (24.0 * 3600.0 * 1000.0)
                    max(1.0 / 12.0, days / 365.25)
                } else {
                    1.0 // default 1 annual period
                }
                principal * rateDecimal * years
            }
            "MONTHLY" -> {
                // Monthly percentage rate
                val months = if (hasTerm && termLength > 0) {
                    when (termUnit.uppercase()) {
                        "MONTHS" -> termLength.toDouble()
                        "YEARS" -> termLength * 12.0
                        "WEEKS" -> (termLength * 7.0) / 30.0
                        "DAYS" -> termLength / 30.0
                        else -> termLength.toDouble()
                    }
                } else if (dueDate != null && dueDate > startDate) {
                    val days = (dueDate - startDate).toDouble() / (24.0 * 3600.0 * 1000.0)
                    max(1.0, days / 30.0)
                } else {
                    1.0 // default 1 month period
                }
                principal * rateDecimal * months
            }
            else -> {
                principal * rateDecimal
            }
        }

        return CurrencyUtils.roundTo2Decimals(interest)
    }

    /**
     * Convenience method to calculate total interest from a Transaction object.
     */
    fun calculateTotalInterest(transaction: Transaction): Double {
        if (!transaction.isLoan) return 0.0
        return calculateTotalInterest(
            principal = transaction.amount,
            hasInterest = transaction.hasInterest,
            interestRate = transaction.interestRate,
            interestType = transaction.interestType,
            hasTerm = transaction.hasTerm,
            termUnit = transaction.termUnit,
            termLength = transaction.termLength,
            startDate = transaction.timestamp,
            dueDate = transaction.loanDueDate
        )
    }

    /**
     * Calculates the total amount payable/collectable (Principal + Interest).
     */
    fun calculateTotalPayable(transaction: Transaction): Double {
        val interest = calculateTotalInterest(transaction)
        return CurrencyUtils.roundTo2Decimals(transaction.amount + interest)
    }

    /**
     * Calculates the remaining balance to pay/collect for a loan:
     * (Principal + Interest) - Total Payments Made
     */
    fun calculateRemainingBalance(
        transaction: Transaction,
        payments: List<Transaction> = emptyList()
    ): Double {
        val totalPayable = calculateTotalPayable(transaction)
        val totalPaid = payments.sumOf { it.amount }
        return max(0.0, CurrencyUtils.roundTo2Decimals(totalPayable - totalPaid))
    }

    /**
     * Determines whether a loan is fully settled, either marked manually or fully paid.
     */
    fun isFullySettled(
        transaction: Transaction,
        payments: List<Transaction> = emptyList()
    ): Boolean {
        if (transaction.isLoanSettled) return true
        val totalPayable = calculateTotalPayable(transaction)
        if (totalPayable <= 0.0) return true
        val totalPaid = payments.sumOf { it.amount }
        return (totalPayable - totalPaid) <= 0.01
    }

    /**
     * Formats a descriptive summary of the interest terms (e.g. "5% monthly (+₱500.00)" or "5% monthly (+$50.00)").
     */
    fun formatInterestSummary(transaction: Transaction, currencyCode: String = "PHP"): String {
        if (!transaction.hasInterest || transaction.interestRate == null) return "No interest"
        val interest = calculateTotalInterest(transaction)
        val typeLabel = when (transaction.interestType.uppercase()) {
            "MONTHLY" -> "monthly"
            "ANNUAL" -> "annual"
            else -> "fixed"
        }
        val rateFormatted = if (transaction.interestRate % 1.0 == 0.0) {
            "${transaction.interestRate.toInt()}%"
        } else {
            "${transaction.interestRate}%"
        }
        return "$rateFormatted $typeLabel (+${DateUtils.formatCurrency(interest, currencyCode)})"
    }
}
