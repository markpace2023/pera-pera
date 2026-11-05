package com.example.data.model

import java.util.Calendar

enum class AccountType(val displayName: String, val defaultIcon: String) {
    CASH("Cash", "local_atm"),
    DEBIT_CARD("Debit Card", "credit_card"),
    CREDIT_CARD("Credit Card", "credit_card"),
    SAVINGS("Savings Account", "account_balance"),
    DIGITAL_WALLET("Digital Wallet", "account_balance_wallet"),
    INVESTMENT("Investment", "trending_up"),
    OTHER("Other", "payments")
}

enum class StatementDateType(val displayName: String, val description: String) {
    FIXED(
        "Fixed Day of Month",
        "Cutoff occurs on the exact same date each month (e.g. every 18th)"
    ),
    VARIABLE_MONTH_LENGTH(
        "Variable / UnionBank Style",
        "Cutoff shifts based on month length: 31-day: base date, 30-day: -1 day, Feb (28/29d): -2/-3 days"
    ),
    VARIABLE_DAYS_BEFORE_END(
        "Fixed Days Before Month End",
        "Cutoff occurs N days before the end of each month"
    )
}

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val balance: Double,
    val initialBalance: Double = balance,
    val currency: String = "PHP",
    val colorHex: String = "#10B981",
    val iconName: String = type.defaultIcon,
    val accountNumberMask: String = "",
    // Credit card specific fields
    val creditLimit: Double = 0.0,
    val statementDateType: StatementDateType = StatementDateType.FIXED,
    val statementDay: Int = 18, // Base statement day
    val statementOffsetDays: Int = 13, // Used for VARIABLE_DAYS_BEFORE_END
    val paymentGraceDays: Int = 21 // Payment due date after cutoff
) {
    /**
     * Calculates the next statement cutoff date timestamp in milliseconds.
     */
    fun computeNextStatementDate(fromTimestamp: Long = System.currentTimeMillis()): Long {
        if (type != AccountType.CREDIT_CARD) return 0L
        val cal = Calendar.getInstance().apply { timeInMillis = fromTimestamp }
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)

        val targetCutoffThisMonth = calculateCutoffDay(currentYear, currentMonth)

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        if (currentDay <= targetCutoffThisMonth) {
            targetCal.set(currentYear, currentMonth, targetCutoffThisMonth)
        } else {
            // Next month's cutoff
            val nextMonthCal = Calendar.getInstance().apply {
                timeInMillis = fromTimestamp
                add(Calendar.MONTH, 1)
            }
            val nextYear = nextMonthCal.get(Calendar.YEAR)
            val nextMonth = nextMonthCal.get(Calendar.MONTH)
            val nextCutoff = calculateCutoffDay(nextYear, nextMonth)
            targetCal.set(nextYear, nextMonth, nextCutoff)
        }

        return targetCal.timeInMillis
    }

    /**
     * Calculates the statement cutoff day for a specific year and month.
     */
    fun calculateCutoffDay(year: Int, month: Int): Int {
        val testCal = Calendar.getInstance().apply {
            set(year, month, 1)
        }
        val daysInMonth = testCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        return when (statementDateType) {
            StatementDateType.FIXED -> {
                statementDay.coerceIn(1, daysInMonth)
            }
            StatementDateType.VARIABLE_MONTH_LENGTH -> {
                // UnionBank variable rule:
                // Base statementDay is defined for a 31-day month (e.g. 18th).
                // If month has 30 days: cutoff is (base - 1) -> 17th
                // If month has 29 days (leap Feb): cutoff is (base - 2) -> 16th
                // If month has 28 days (Feb): cutoff is (base - 3) -> 15th
                val delta = 31 - daysInMonth
                val calculatedDay = statementDay - delta
                calculatedDay.coerceIn(1, daysInMonth)
            }
            StatementDateType.VARIABLE_DAYS_BEFORE_END -> {
                (daysInMonth - statementOffsetDays).coerceIn(1, daysInMonth)
            }
        }
    }

    /**
     * Calculates the payment due date based on paymentGraceDays after cutoff.
     */
    fun computePaymentDueDate(fromTimestamp: Long = System.currentTimeMillis()): Long {
        val nextCutoff = computeNextStatementDate(fromTimestamp)
        if (nextCutoff == 0L) return 0L
        val cal = Calendar.getInstance().apply {
            timeInMillis = nextCutoff
            add(Calendar.DAY_OF_YEAR, paymentGraceDays)
        }
        return cal.timeInMillis
    }

    /**
     * Days remaining until next statement cutoff.
     */
    fun daysUntilCutoff(fromTimestamp: Long = System.currentTimeMillis()): Int {
        val nextCutoff = computeNextStatementDate(fromTimestamp)
        if (nextCutoff == 0L) return 0
        val diffMillis = nextCutoff - fromTimestamp
        val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        return if (days < 0) 0 else days
    }
}
