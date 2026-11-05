package com.example.data.model

enum class TransactionType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

enum class RecurringFrequency(val label: String, val daysStep: Int) {
    NONE("One-time", 0),
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    BIWEEKLY("Bi-weekly (14 days)", 14),
    MONTHLY("Monthly", 30),
    YEARLY("Yearly", 365)
}

data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long? = null, // for TRANSFER
    val transferFee: Double = 0.0,
    val categoryId: Long? = null,
    val title: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurringFrequency: RecurringFrequency = RecurringFrequency.NONE,
    val recurringTotalCount: Int = 0, // 0 = unlimited/indefinite
    val recurringExecutedCount: Int = 0,
    val nextDueDate: Long? = null,
    // Borrowing & Lending fields
    val isLoan: Boolean = false,
    val loanPerson: String = "", // who did you borrow it from / lend to
    val loanDueDate: Long? = null, // when it will be needed to be paid (optional)
    val hasInterest: Boolean = false,
    val interestRate: Double? = null,
    val interestType: String = "MONTHLY", // TOTAL, MONTHLY, ANNUAL
    val hasTerm: Boolean = false,
    val termUnit: String = "MONTHS", // WEEKS, MONTHS, YEARS, DAYS
    val termLength: Int = 1,
    val isLoanSettled: Boolean = false,
    val isLoanPayment: Boolean = false,
    val loanReferenceId: Long? = null,
    val deletedAccountName: String? = null,
    val deletedToAccountName: String? = null
)

data class Category(
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false
)

data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val monthlyLimit: Double,
    val notifyThresholdPercent: Int = 80 // alert when spending reaches e.g. 80%
)

data class TransactionWithDetails(
    val transaction: Transaction,
    val accountName: String,
    val accountIcon: String,
    val toAccountName: String? = null,
    val categoryName: String? = null,
    val categoryIcon: String? = null,
    val categoryColor: String? = null,
    val accountCurrency: String = "PHP",
    val toAccountCurrency: String? = null
)
