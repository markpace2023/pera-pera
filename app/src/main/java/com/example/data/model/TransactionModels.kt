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

enum class BudgetGroup(val key: String, val displayName: String, val percentage: Double, val description: String) {
    NEEDS("NEEDS", "Needs (50%)", 0.50, "Rent, groceries, utilities, transport, essentials"),
    WANTS("WANTS", "Wants (30%)", 0.30, "Dining out, shopping, entertainment, travel, hobbies"),
    SAVINGS_DEBT("SAVINGS_DEBT", "Savings & Debt (20%)", 0.20, "Savings transfers, loan & debt payments, emergency fund"),
    NONE("NONE", "None", 0.0, "Income / not applicable");

    companion object {
        fun fromKey(key: String?): BudgetGroup {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) } ?: NEEDS
        }
    }
}

data class Category(
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false,
    val budgetGroup: BudgetGroup = BudgetGroup.NEEDS
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
