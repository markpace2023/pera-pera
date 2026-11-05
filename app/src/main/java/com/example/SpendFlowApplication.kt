package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.FinanceRepository
import com.example.util.BiometricHelper
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendFlowApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }

    val repository by lazy {
        FinanceRepository(
            database.accountDao(),
            database.transactionDao(),
            database.categoryDao(),
            database.budgetDao(),
            database
        )
    }

    val notificationHelper by lazy { NotificationHelper(this) }
    val biometricHelper by lazy { BiometricHelper(this) }

    override fun onCreate() {
        super.onCreate()
        // Process any due recurring transactions in the background
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                repository.processDueRecurringTransactions()
                repository.recalculateAccountBalancesFromTransactions()
            }
        }
    }
}
