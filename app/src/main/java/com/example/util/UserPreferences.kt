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
    private const val KEY_RECENT_ACCOUNT_IDS = "recent_account_ids"
    private const val KEY_RECENT_CATEGORY_IDS_EXPENSE = "recent_category_ids_expense"
    private const val KEY_RECENT_CATEGORY_IDS_INCOME = "recent_category_ids_income"
    private const val KEY_RECENT_CATEGORY_IDS_TRANSFER = "recent_category_ids_transfer"

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

    /**
     * Returns the ordered list of most recently used account IDs (MRU order: newest first).
     */
    fun getRecentAccountIds(context: Context): List<Long> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_RECENT_ACCOUNT_IDS, null)
        if (!raw.isNullOrBlank()) {
            return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
        }
        val single = getLastUsedAccountId(context)
        return if (single != null) listOf(single) else emptyList()
    }

    /**
     * Records an account as picked/used, pushing it to the 1st position (MRU order).
     * The previous 1st becomes 2nd, the previous 2nd becomes 3rd, and so on.
     */
    fun recordUsedAccount(context: Context, accountId: Long) {
        if (accountId <= 0L) return
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getRecentAccountIds(context).toMutableList()
        current.remove(accountId)
        current.add(0, accountId)
        val toSave = current.take(50).joinToString(",")
        sp.edit()
            .putString(KEY_RECENT_ACCOUNT_IDS, toSave)
            .putLong(KEY_LAST_USED_ACCOUNT_ID, accountId)
            .apply()
    }

    fun getLastUsedAccountId(context: Context): Long? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_RECENT_ACCOUNT_IDS, null)
        if (!raw.isNullOrBlank()) {
            val first = raw.split(",").firstOrNull()?.trim()?.toLongOrNull()
            if (first != null) return first
        }
        return if (sp.contains(KEY_LAST_USED_ACCOUNT_ID)) {
            val id = sp.getLong(KEY_LAST_USED_ACCOUNT_ID, -1L)
            if (id != -1L) id else null
        } else null
    }

    fun setLastUsedAccountId(context: Context, accountId: Long) {
        recordUsedAccount(context, accountId)
    }

    private fun getCategoryKey(type: TransactionType): String {
        return when (type) {
            TransactionType.INCOME -> KEY_RECENT_CATEGORY_IDS_INCOME
            TransactionType.TRANSFER -> KEY_RECENT_CATEGORY_IDS_TRANSFER
            else -> KEY_RECENT_CATEGORY_IDS_EXPENSE
        }
    }

    /**
     * Returns the ordered list of most recently used category IDs for a transaction type (MRU order: newest first).
     */
    fun getRecentCategoryIds(context: Context, type: TransactionType): List<Long> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getCategoryKey(type)
        val raw = sp.getString(key, null)
        if (!raw.isNullOrBlank()) {
            return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
        }
        val single = getLastUsedCategoryId(context, type)
        return if (single != null) listOf(single) else emptyList()
    }

    /**
     * Records a category as picked/used, pushing it to the 1st position (MRU order).
     * The previous 1st becomes 2nd, the previous 2nd becomes 3rd, and so on.
     */
    fun recordUsedCategory(context: Context, type: TransactionType, categoryId: Long) {
        if (categoryId <= 0L) return
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getCategoryKey(type)
        val current = getRecentCategoryIds(context, type).toMutableList()
        current.remove(categoryId)
        current.add(0, categoryId)
        val toSave = current.take(50).joinToString(",")
        val legacyKey = if (type == TransactionType.INCOME) KEY_LAST_USED_CATEGORY_INCOME else KEY_LAST_USED_CATEGORY_EXPENSE
        sp.edit()
            .putString(key, toSave)
            .putLong(legacyKey, categoryId)
            .apply()
    }

    fun getLastUsedCategoryId(context: Context, type: TransactionType): Long? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getCategoryKey(type)
        val raw = sp.getString(key, null)
        if (!raw.isNullOrBlank()) {
            val first = raw.split(",").firstOrNull()?.trim()?.toLongOrNull()
            if (first != null) return first
        }
        val legacyKey = if (type == TransactionType.INCOME) KEY_LAST_USED_CATEGORY_INCOME else KEY_LAST_USED_CATEGORY_EXPENSE
        return if (sp.contains(legacyKey)) {
            val id = sp.getLong(legacyKey, -1L)
            if (id != -1L) id else null
        } else null
    }

    fun setLastUsedCategoryId(context: Context, type: TransactionType, categoryId: Long) {
        recordUsedCategory(context, type, categoryId)
    }

    /**
     * Sorts accounts so that recently picked accounts appear in MRU order at the beginning.
     * Transaction 1 picked account: 1st option.
     * Transaction 2 picked account: 1st option, Transaction 1: 2nd option, and so on.
     */
    fun sortAccountsByRecency(accounts: List<Account>, recentIds: List<Long>): List<Account> {
        if (recentIds.isEmpty()) return accounts
        val accountMap = accounts.associateBy { it.id }
        val orderedRecent = recentIds.mapNotNull { accountMap[it] }
        val remaining = accounts.filter { it.id !in recentIds }
        return orderedRecent + remaining
    }

    /**
     * Backward-compatible overload accepting a single last-used ID or calling sortAccountsByRecency.
     */
    fun sortAccountsWithLastUsedFirst(accounts: List<Account>, lastUsedId: Long?): List<Account> {
        if (lastUsedId == null) return accounts
        return sortAccountsByRecency(accounts, listOf(lastUsedId))
    }

    /**
     * Backward-compatible overload accepting recentIds list.
     */
    fun sortAccountsWithLastUsedFirst(accounts: List<Account>, recentIds: List<Long>): List<Account> {
        return sortAccountsByRecency(accounts, recentIds)
    }

    /**
     * Sorts categories so that recently picked categories appear in MRU order at the beginning.
     * Transaction 1 picked category: 1st option.
     * Transaction 2 picked category: 1st option, Transaction 1: 2nd option, and so on.
     */
    fun sortCategoriesByRecency(categories: List<Category>, recentIds: List<Long>): List<Category> {
        if (recentIds.isEmpty()) return categories
        val categoryMap = categories.associateBy { it.id }
        val orderedRecent = recentIds.mapNotNull { categoryMap[it] }
        val remaining = categories.filter { it.id !in recentIds }
        return orderedRecent + remaining
    }

    /**
     * Backward-compatible overload accepting a single last-used ID or calling sortCategoriesByRecency.
     */
    fun sortCategoriesWithLastUsedFirst(categories: List<Category>, lastUsedId: Long?): List<Category> {
        if (lastUsedId == null) return categories
        return sortCategoriesByRecency(categories, listOf(lastUsedId))
    }

    /**
     * Backward-compatible overload accepting recentIds list.
     */
    fun sortCategoriesWithLastUsedFirst(categories: List<Category>, recentIds: List<Long>): List<Category> {
        return sortCategoriesByRecency(categories, recentIds)
    }

    /**
     * Clears stored preferences (e.g. on database reset).
     */
    fun clearPreferences(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
