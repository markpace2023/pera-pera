package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.SpendFlowApplication
import com.example.util.DateUtils
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class QuickTransactionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? SpendFlowApplication ?: return

        app.applicationScope.launch {
            val accounts = app.repository.allAccounts.firstOrNull().orEmpty()
            val totalBalance = accounts.sumOf { if (it.type == com.example.data.model.AccountType.CREDIT_CARD) -it.balance else it.balance }
            val firstAccountName = accounts.firstOrNull()?.name ?: "All Accounts"

            for (widgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId, totalBalance, firstAccountName)
            }
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            totalBalance: Double,
            accountName: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.quick_transaction_widget)

            views.setTextViewText(R.id.widget_balance, "Net: " + DateUtils.formatCurrency(totalBalance))
            views.setTextViewText(R.id.widget_account_name, accountName)

            // Expense action
            val expenseIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "quick_add")
                putExtra("type", "EXPENSE")
            }
            val expensePendingIntent = PendingIntent.getActivity(
                context,
                10,
                expenseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_expense, expensePendingIntent)

            // Income action
            val incomeIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "quick_add")
                putExtra("type", "INCOME")
            }
            val incomePendingIntent = PendingIntent.getActivity(
                context,
                20,
                incomeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_income, incomePendingIntent)

            // Root click -> open app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                30,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun notifyUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuickTransactionWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val intent = Intent(context, QuickTransactionWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
}
