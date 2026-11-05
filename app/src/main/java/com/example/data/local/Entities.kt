package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Account
import com.example.data.model.AccountType
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.RecurringFrequency
import com.example.data.model.StatementDateType
import com.example.data.model.Transaction
import com.example.data.model.TransactionType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // AccountType name
    val balance: Double,
    val initialBalance: Double,
    val currency: String,
    val colorHex: String,
    val iconName: String,
    val accountNumberMask: String,
    val creditLimit: Double,
    val statementDateType: String, // StatementDateType name
    val statementDay: Int,
    val statementOffsetDays: Int,
    val paymentGraceDays: Int
) {
    fun toDomain(): Account = Account(
        id = id,
        name = name,
        type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.CASH),
        balance = balance,
        initialBalance = initialBalance,
        currency = currency,
        colorHex = colorHex,
        iconName = iconName,
        accountNumberMask = accountNumberMask,
        creditLimit = creditLimit,
        statementDateType = runCatching { StatementDateType.valueOf(statementDateType) }.getOrDefault(StatementDateType.FIXED),
        statementDay = statementDay,
        statementOffsetDays = statementOffsetDays,
        paymentGraceDays = paymentGraceDays
    )

    companion object {
        fun fromDomain(account: Account): AccountEntity = AccountEntity(
            id = account.id,
            name = account.name,
            type = account.type.name,
            balance = account.balance,
            initialBalance = account.initialBalance,
            currency = account.currency,
            colorHex = account.colorHex,
            iconName = account.iconName,
            accountNumberMask = account.accountNumberMask,
            creditLimit = account.creditLimit,
            statementDateType = account.statementDateType.name,
            statementDay = account.statementDay,
            statementOffsetDays = account.statementOffsetDays,
            paymentGraceDays = account.paymentGraceDays
        )
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // TransactionType name
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long?,
    val transferFee: Double,
    val categoryId: Long?,
    val title: String,
    val note: String,
    val timestamp: Long,
    val isRecurring: Boolean,
    val recurringFrequency: String,
    val recurringTotalCount: Int,
    val recurringExecutedCount: Int,
    val nextDueDate: Long?,
    val isLoan: Boolean = false,
    val loanPerson: String = "",
    val loanDueDate: Long? = null,
    val hasInterest: Boolean = false,
    val interestRate: Double? = null,
    val interestType: String = "MONTHLY",
    val hasTerm: Boolean = false,
    val termUnit: String = "MONTHS",
    val termLength: Int = 1,
    val isLoanSettled: Boolean = false,
    val isLoanPayment: Boolean = false,
    val loanReferenceId: Long? = null,
    val deletedAccountName: String? = null,
    val deletedToAccountName: String? = null
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
        amount = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        transferFee = transferFee,
        categoryId = categoryId,
        title = title,
        note = note,
        timestamp = timestamp,
        isRecurring = isRecurring,
        recurringFrequency = runCatching { RecurringFrequency.valueOf(recurringFrequency) }.getOrDefault(RecurringFrequency.NONE),
        recurringTotalCount = recurringTotalCount,
        recurringExecutedCount = recurringExecutedCount,
        nextDueDate = nextDueDate,
        isLoan = isLoan,
        loanPerson = loanPerson,
        loanDueDate = loanDueDate,
        hasInterest = hasInterest,
        interestRate = interestRate,
        interestType = interestType,
        hasTerm = hasTerm,
        termUnit = termUnit,
        termLength = termLength,
        isLoanSettled = isLoanSettled,
        isLoanPayment = isLoanPayment,
        loanReferenceId = loanReferenceId,
        deletedAccountName = deletedAccountName,
        deletedToAccountName = deletedToAccountName
    )

    companion object {
        fun fromDomain(tx: Transaction): TransactionEntity = TransactionEntity(
            id = tx.id,
            type = tx.type.name,
            amount = tx.amount,
            accountId = tx.accountId,
            toAccountId = tx.toAccountId,
            transferFee = tx.transferFee,
            categoryId = tx.categoryId,
            title = tx.title,
            note = tx.note,
            timestamp = tx.timestamp,
            isRecurring = tx.isRecurring,
            recurringFrequency = tx.recurringFrequency.name,
            recurringTotalCount = tx.recurringTotalCount,
            recurringExecutedCount = tx.recurringExecutedCount,
            nextDueDate = tx.nextDueDate,
            isLoan = tx.isLoan,
            loanPerson = tx.loanPerson,
            loanDueDate = tx.loanDueDate,
            hasInterest = tx.hasInterest,
            interestRate = tx.interestRate,
            interestType = tx.interestType,
            hasTerm = tx.hasTerm,
            termUnit = tx.termUnit,
            termLength = tx.termLength,
            isLoanSettled = tx.isLoanSettled,
            isLoanPayment = tx.isLoanPayment,
            loanReferenceId = tx.loanReferenceId,
            deletedAccountName = tx.deletedAccountName,
            deletedToAccountName = tx.deletedToAccountName
        )
    }
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // TransactionType name
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
        iconName = iconName,
        colorHex = colorHex,
        isDefault = isDefault
    )

    companion object {
        fun fromDomain(category: Category): CategoryEntity = CategoryEntity(
            id = category.id,
            name = category.name,
            type = category.type.name,
            iconName = category.iconName,
            colorHex = category.colorHex,
            isDefault = category.isDefault
        )
    }
}

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val monthlyLimit: Double,
    val notifyThresholdPercent: Int
) {
    fun toDomain(): Budget = Budget(
        id = id,
        categoryId = categoryId,
        monthlyLimit = monthlyLimit,
        notifyThresholdPercent = notifyThresholdPercent
    )

    companion object {
        fun fromDomain(budget: Budget): BudgetEntity = BudgetEntity(
            id = budget.id,
            categoryId = budget.categoryId,
            monthlyLimit = budget.monthlyLimit,
            notifyThresholdPercent = budget.notifyThresholdPercent
        )
    }
}
