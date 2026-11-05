package com.example.util

import android.content.Context
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionType

object UserPreferences {
    private const val PREFS_NAME = "spendflow_user_preferences"
    private const val KEY_PRIMARY_CURRENCY = "primary_currency"
    private const val KEY_LAST_USED_ACCOUNT_ID = "last_used_account_id"
    private const val KEY_LAST_USED_CATEGORY_EXPENSE = "last_used_category_expense"
    private const val KEY_LAST_USED_CATEGORY_INCOME = "last_used_category_income"

    fun getPrimaryCurrency(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_PRIMARY_CURRENCY, "PHP") ?: "PHP"
    }

    fun setPrimaryCurrency(context: Context, currencyCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRIMARY_CURRENCY, currencyCode.uppercase().trim())
            .apply()
    }

    fun getLastUsedAccountId(context: Context): Long? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (sp.contains(KEY_LAST_USED_ACCOUNT_ID)) {
            val id = sp.getLong(KEY_LAST_USED_ACCOUNT_ID, -1L)
            if (id != -1L) id else null
        } else null
    }

    fun setLastUsedAccountId(context: Context, accountId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_USED_ACCOUNT_ID, accountId)
            .apply()
    }

    fun getLastUsedCategoryId(context: Context, type: TransactionType): Long? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (type == TransactionType.INCOME) KEY_LAST_USED_CATEGORY_INCOME else KEY_LAST_USED_CATEGORY_EXPENSE
        return if (sp.contains(key)) {
            val id = sp.getLong(key, -1L)
            if (id != -1L) id else null
        } else null
    }

    fun setLastUsedCategoryId(context: Context, type: TransactionType, categoryId: Long) {
        val key = if (type == TransactionType.INCOME) KEY_LAST_USED_CATEGORY_INCOME else KEY_LAST_USED_CATEGORY_EXPENSE
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, categoryId)
            .apply()
    }

    /**
     * Sorts accounts so that the last-used account is the first element in the list.
     */
    fun sortAccountsWithLastUsedFirst(accounts: List<Account>, lastUsedId: Long?): List<Account> {
        if (lastUsedId == null) return accounts
        val lastUsed = accounts.find { it.id == lastUsedId } ?: return accounts
        return listOf(lastUsed) + accounts.filter { it.id != lastUsedId }
    }

    /**
     * Sorts categories so that the last-used category is the first element in the list.
     */
    fun sortCategoriesWithLastUsedFirst(categories: List<Category>, lastUsedId: Long?): List<Category> {
        if (lastUsedId == null) return categories
        val lastUsed = categories.find { it.id == lastUsedId } ?: return categories
        return listOf(lastUsed) + categories.filter { it.id != lastUsedId }
    }

    /**
     * Clears stored last used preferences (e.g. on database reset).
     */
    fun clearPreferences(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
