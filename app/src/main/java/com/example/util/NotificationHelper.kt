package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

class NotificationHelper(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("spendflow_notifications", Context.MODE_PRIVATE)

    companion object {
        const val CHANNEL_RECURRING = "channel_recurring_expenses"
        const val CHANNEL_BUDGETS = "channel_budget_depletion"
        const val CHANNEL_CREDIT_CARDS = "channel_credit_card_statements"
        const val CHANNEL_LOANS = "channel_loans_debts"

        const val PREF_NOTIF_RECURRING = "pref_notif_recurring"
        const val PREF_NOTIF_BUDGET = "pref_notif_budget"
        const val PREF_NOTIF_CC = "pref_notif_cc"
        const val PREF_NOTIF_LOANS = "pref_notif_loans"
    }

    init {
        createChannels()
    }

    var isRecurringNotificationEnabled: Boolean
        get() = prefs.getBoolean(PREF_NOTIF_RECURRING, true)
        set(value) = prefs.edit().putBoolean(PREF_NOTIF_RECURRING, value).apply()

    var isBudgetNotificationEnabled: Boolean
        get() = prefs.getBoolean(PREF_NOTIF_BUDGET, true)
        set(value) = prefs.edit().putBoolean(PREF_NOTIF_BUDGET, value).apply()

    var isCreditCardNotificationEnabled: Boolean
        get() = prefs.getBoolean(PREF_NOTIF_CC, true)
        set(value) = prefs.edit().putBoolean(PREF_NOTIF_CC, value).apply()

    var isLoanNotificationEnabled: Boolean
        get() = prefs.getBoolean(PREF_NOTIF_LOANS, true)
        set(value) = prefs.edit().putBoolean(PREF_NOTIF_LOANS, value).apply()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val recurringChannel = NotificationChannel(
                CHANNEL_RECURRING,
                "Recurring Expense Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a recurring bill or expense is approaching or due"
                enableVibration(true)
            }

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGETS,
                "Budget Depletion Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when category spending approaches or exceeds your limit"
                enableVibration(true)
            }

            val ccChannel = NotificationChannel(
                CHANNEL_CREDIT_CARDS,
                "Credit Card Statement Dates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for fixed and variable credit card billing cutoffs"
                enableVibration(true)
            }

            val loanChannel = NotificationChannel(
                CHANNEL_LOANS,
                "Borrowing & Lending Due Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for debts you owe and money people borrowed from you"
                enableVibration(true)
            }

            manager.createNotificationChannel(recurringChannel)
            manager.createNotificationChannel(budgetChannel)
            manager.createNotificationChannel(ccChannel)
            manager.createNotificationChannel(loanChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendRecurringDueNotification(title: String, amountFormatted: String, accountName: String): Boolean {
        if (!isRecurringNotificationEnabled || !hasNotificationPermission()) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RECURRING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Recurring Payment Due: $title")
            .setContentText("$amountFormatted scheduled from $accountName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(1001, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun sendBudgetWarningNotification(categoryName: String, percentSpent: Int, currentSpendFormatted: String, limitFormatted: String): Boolean {
        if (!isBudgetNotificationEnabled || !hasNotificationPermission()) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (percentSpent >= 100) "Budget Exceeded!" else "Near Budget Depletion ($percentSpent%)"
        val message = "$categoryName: Spent $currentSpendFormatted of $limitFormatted limit"

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGETS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(1002, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun sendCreditCardCutoffNotification(cardName: String, daysLeft: Int, cutoffDateFormatted: String, currentBalanceFormatted: String): Boolean {
        if (!isCreditCardNotificationEnabled || !hasNotificationPermission()) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            103,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (daysLeft == 0) {
            "Statement cutoff is TODAY ($cutoffDateFormatted). Balance: $currentBalanceFormatted"
        } else {
            "Cutoff in $daysLeft day${if (daysLeft > 1) "s" else ""} ($cutoffDateFormatted). Balance: $currentBalanceFormatted"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CREDIT_CARDS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Statement Cutoff: $cardName")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(1003, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun sendLoanDueNotification(
        person: String,
        isBorrowing: Boolean,
        amountFormatted: String,
        dueDateFormatted: String,
        isOverdue: Boolean = false
    ): Boolean {
        if (!isLoanNotificationEnabled || !hasNotificationPermission()) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            104,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isBorrowing) {
            if (isOverdue) "Overdue Debt Payment: $person" else "Debt Payment Due: $person"
        } else {
            if (isOverdue) "Overdue Loan Collection: $person" else "Loan Repayment Due: $person"
        }

        val message = if (isBorrowing) {
            "$amountFormatted owed to $person is due on $dueDateFormatted"
        } else {
            "$amountFormatted lent to $person is due for repayment on $dueDateFormatted"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_LOANS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify((2000..9999).random(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
