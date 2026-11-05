package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAccountName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN deletedToAccountName TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spendflow_database.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val categoryDao = database.categoryDao()

            if (categoryDao.getCategoryCount() == 0) {
                val defaultCategories = listOf(
                    // Expense categories
                    CategoryEntity(name = "Food & Dining", type = "EXPENSE", iconName = "restaurant", colorHex = "#F59E0B", isDefault = true),
                    CategoryEntity(name = "Groceries", type = "EXPENSE", iconName = "shopping_cart", colorHex = "#10B981", isDefault = true),
                    CategoryEntity(name = "Transportation", type = "EXPENSE", iconName = "directions_car", colorHex = "#3B82F6", isDefault = true),
                    CategoryEntity(name = "Shopping", type = "EXPENSE", iconName = "shopping_bag", colorHex = "#EC4899", isDefault = true),
                    CategoryEntity(name = "Bills & Utilities", type = "EXPENSE", iconName = "receipt_long", colorHex = "#EF4444", isDefault = true),
                    CategoryEntity(name = "Housing & Rent", type = "EXPENSE", iconName = "home", colorHex = "#8B5CF6", isDefault = true),
                    CategoryEntity(name = "Entertainment", type = "EXPENSE", iconName = "movie", colorHex = "#6366F1", isDefault = true),
                    CategoryEntity(name = "Health & Medical", type = "EXPENSE", iconName = "medical_services", colorHex = "#14B8A6", isDefault = true),
                    CategoryEntity(name = "Subscriptions", type = "EXPENSE", iconName = "subscriptions", colorHex = "#F97316", isDefault = true),
                    CategoryEntity(name = "Travel", type = "EXPENSE", iconName = "flight", colorHex = "#06B6D4", isDefault = true),
                    CategoryEntity(name = "Education", type = "EXPENSE", iconName = "school", colorHex = "#84CC16", isDefault = true),
                    CategoryEntity(name = "Loan / Money Lent", type = "EXPENSE", iconName = "handshake", colorHex = "#F43F5E", isDefault = true),
                    CategoryEntity(name = "Debt / Loan Payment", type = "EXPENSE", iconName = "payments", colorHex = "#F59E0B", isDefault = true),
                    CategoryEntity(name = "Other Expense", type = "EXPENSE", iconName = "more_horiz", colorHex = "#64748B", isDefault = true),

                    // Income categories
                    CategoryEntity(name = "Salary", type = "INCOME", iconName = "work", colorHex = "#10B981", isDefault = true),
                    CategoryEntity(name = "Freelance", type = "INCOME", iconName = "laptop", colorHex = "#3B82F6", isDefault = true),
                    CategoryEntity(name = "Investments", type = "INCOME", iconName = "trending_up", colorHex = "#8B5CF6", isDefault = true),
                    CategoryEntity(name = "Business", type = "INCOME", iconName = "storefront", colorHex = "#F59E0B", isDefault = true),
                    CategoryEntity(name = "Gifts & Bonus", type = "INCOME", iconName = "card_giftcard", colorHex = "#EC4899", isDefault = true),
                    CategoryEntity(name = "Loan / Borrowed Money", type = "INCOME", iconName = "handshake", colorHex = "#F59E0B", isDefault = true),
                    CategoryEntity(name = "Loan Collection", type = "INCOME", iconName = "savings", colorHex = "#10B981", isDefault = true),
                    CategoryEntity(name = "Other Income", type = "INCOME", iconName = "account_balance_wallet", colorHex = "#64748B", isDefault = true)
                )
                categoryDao.insertCategories(defaultCategories)
            }
            // Fresh state has no accounts. Users configure their own accounts.
        }
    }
}
