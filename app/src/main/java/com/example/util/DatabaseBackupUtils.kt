package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionEntity
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupMetadata(
    val app: String,
    val version: Int,
    val exportTimestamp: Long,
    val exportDateFormatted: String,
    val accountsCount: Int,
    val categoriesCount: Int,
    val transactionsCount: Int,
    val budgetsCount: Int
)

data class FullDatabaseBackup(
    val metadata: BackupMetadata,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>
)

sealed class DatabaseRestoreResult {
    data class Success(
        val accountsCount: Int,
        val categoriesCount: Int,
        val transactionsCount: Int,
        val budgetsCount: Int
    ) : DatabaseRestoreResult()

    data class Error(val message: String) : DatabaseRestoreResult()
}

object DatabaseBackupUtils {

    const val CURRENT_BACKUP_VERSION = 1
    const val APP_IDENTIFIER = "SpendFlow"

    fun exportDatabaseToJson(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>
    ): String {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val formattedDate = sdf.format(Date(now))

        val root = JSONObject()

        // Metadata
        val meta = JSONObject().apply {
            put("app", APP_IDENTIFIER)
            put("version", CURRENT_BACKUP_VERSION)
            put("exportTimestamp", now)
            put("exportDate", formattedDate)
            put("accountsCount", accounts.size)
            put("categoriesCount", categories.size)
            put("transactionsCount", transactions.size)
            put("budgetsCount", budgets.size)
        }
        root.put("metadata", meta)

        // Accounts
        val accountsArray = JSONArray()
        for (acc in accounts) {
            val item = JSONObject().apply {
                put("id", acc.id)
                put("name", acc.name)
                put("type", acc.type)
                put("balance", acc.balance)
                put("initialBalance", acc.initialBalance)
                put("currency", acc.currency)
                put("colorHex", acc.colorHex)
                put("iconName", acc.iconName)
                put("accountNumberMask", acc.accountNumberMask)
                put("creditLimit", acc.creditLimit)
                put("statementDateType", acc.statementDateType)
                put("statementDay", acc.statementDay)
                put("statementOffsetDays", acc.statementOffsetDays)
                put("paymentGraceDays", acc.paymentGraceDays)
            }
            accountsArray.put(item)
        }
        root.put("accounts", accountsArray)

        // Categories
        val categoriesArray = JSONArray()
        for (cat in categories) {
            val item = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("type", cat.type)
                put("iconName", cat.iconName)
                put("colorHex", cat.colorHex)
                put("isDefault", cat.isDefault)
            }
            categoriesArray.put(item)
        }
        root.put("categories", categoriesArray)

        // Transactions
        val transactionsArray = JSONArray()
        for (tx in transactions) {
            val item = JSONObject().apply {
                put("id", tx.id)
                put("type", tx.type)
                put("amount", tx.amount)
                put("accountId", tx.accountId)
                if (tx.toAccountId != null) put("toAccountId", tx.toAccountId)
                put("transferFee", tx.transferFee)
                if (tx.categoryId != null) put("categoryId", tx.categoryId)
                put("title", tx.title)
                put("note", tx.note)
                put("timestamp", tx.timestamp)
                put("isRecurring", tx.isRecurring)
                put("recurringFrequency", tx.recurringFrequency)
                put("recurringTotalCount", tx.recurringTotalCount)
                put("recurringExecutedCount", tx.recurringExecutedCount)
                if (tx.nextDueDate != null) put("nextDueDate", tx.nextDueDate)
                put("isLoan", tx.isLoan)
                put("loanPerson", tx.loanPerson)
                if (tx.loanDueDate != null) put("loanDueDate", tx.loanDueDate)
                put("hasInterest", tx.hasInterest)
                if (tx.interestRate != null) put("interestRate", tx.interestRate)
                put("interestType", tx.interestType)
                put("hasTerm", tx.hasTerm)
                put("termUnit", tx.termUnit)
                put("termLength", tx.termLength)
                put("isLoanSettled", tx.isLoanSettled)
                put("isLoanPayment", tx.isLoanPayment)
                if (tx.loanReferenceId != null) put("loanReferenceId", tx.loanReferenceId)
                if (tx.deletedAccountName != null) put("deletedAccountName", tx.deletedAccountName)
                if (tx.deletedToAccountName != null) put("deletedToAccountName", tx.deletedToAccountName)
            }
            transactionsArray.put(item)
        }
        root.put("transactions", transactionsArray)

        // Budgets
        val budgetsArray = JSONArray()
        for (b in budgets) {
            val item = JSONObject().apply {
                put("id", b.id)
                put("categoryId", b.categoryId)
                put("monthlyLimit", b.monthlyLimit)
                put("notifyThresholdPercent", b.notifyThresholdPercent)
            }
            budgetsArray.put(item)
        }
        root.put("budgets", budgetsArray)

        return root.toString(2)
    }

    fun parseBackupJson(jsonString: String): Result<FullDatabaseBackup> {
        return runCatching {
            val trimmed = jsonString.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                throw IllegalArgumentException("Input is not a valid JSON object")
            }

            val root = JSONObject(trimmed)

            // Parse or infer metadata
            val metaObj = root.optJSONObject("metadata")
            val app = metaObj?.optString("app", APP_IDENTIFIER) ?: APP_IDENTIFIER
            val version = metaObj?.optInt("version", CURRENT_BACKUP_VERSION) ?: CURRENT_BACKUP_VERSION
            val exportTimestamp = metaObj?.optLong("exportTimestamp", System.currentTimeMillis()) ?: System.currentTimeMillis()
            val exportDate = metaObj?.optString("exportDate", "") ?: ""

            // Accounts
            val accountsList = mutableListOf<AccountEntity>()
            val accountsArray = root.optJSONArray("accounts") ?: JSONArray()
            for (i in 0 until accountsArray.length()) {
                val obj = accountsArray.getJSONObject(i)
                accountsList.add(
                    AccountEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        type = obj.optString("type", "CASH"),
                        balance = obj.optDouble("balance", 0.0),
                        initialBalance = obj.optDouble("initialBalance", 0.0),
                        currency = obj.optString("currency", "PHP"),
                        colorHex = obj.optString("colorHex", "#10B981"),
                        iconName = obj.optString("iconName", "account_balance_wallet"),
                        accountNumberMask = obj.optString("accountNumberMask", ""),
                        creditLimit = obj.optDouble("creditLimit", 0.0),
                        statementDateType = obj.optString("statementDateType", "FIXED"),
                        statementDay = obj.optInt("statementDay", 1),
                        statementOffsetDays = obj.optInt("statementOffsetDays", 0),
                        paymentGraceDays = obj.optInt("paymentGraceDays", 0)
                    )
                )
            }

            // Categories
            val categoriesList = mutableListOf<CategoryEntity>()
            val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until categoriesArray.length()) {
                val obj = categoriesArray.getJSONObject(i)
                categoriesList.add(
                    CategoryEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        type = obj.optString("type", "EXPENSE"),
                        iconName = obj.optString("iconName", "category"),
                        colorHex = obj.optString("colorHex", "#64748B"),
                        isDefault = obj.optBoolean("isDefault", false)
                    )
                )
            }

            // Transactions
            val transactionsList = mutableListOf<TransactionEntity>()
            val transactionsArray = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until transactionsArray.length()) {
                val obj = transactionsArray.getJSONObject(i)
                val toAccountId = if (obj.has("toAccountId") && !obj.isNull("toAccountId")) obj.getLong("toAccountId") else null
                val categoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.getLong("categoryId") else null
                val nextDueDate = if (obj.has("nextDueDate") && !obj.isNull("nextDueDate")) obj.getLong("nextDueDate") else null
                val loanDueDate = if (obj.has("loanDueDate") && !obj.isNull("loanDueDate")) obj.getLong("loanDueDate") else null
                val interestRate = if (obj.has("interestRate") && !obj.isNull("interestRate")) obj.getDouble("interestRate") else null
                val loanReferenceId = if (obj.has("loanReferenceId") && !obj.isNull("loanReferenceId")) obj.getLong("loanReferenceId") else null
                val deletedAccountName = if (obj.has("deletedAccountName") && !obj.isNull("deletedAccountName")) obj.getString("deletedAccountName") else null
                val deletedToAccountName = if (obj.has("deletedToAccountName") && !obj.isNull("deletedToAccountName")) obj.getString("deletedToAccountName") else null

                transactionsList.add(
                    TransactionEntity(
                        id = obj.optLong("id", 0L),
                        type = obj.optString("type", "EXPENSE"),
                        amount = obj.optDouble("amount", 0.0),
                        accountId = obj.optLong("accountId", 0L),
                        toAccountId = toAccountId,
                        transferFee = obj.optDouble("transferFee", 0.0),
                        categoryId = categoryId,
                        title = obj.optString("title", "Transaction"),
                        note = obj.optString("note", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isRecurring = obj.optBoolean("isRecurring", false),
                        recurringFrequency = obj.optString("recurringFrequency", "NONE"),
                        recurringTotalCount = obj.optInt("recurringTotalCount", 0),
                        recurringExecutedCount = obj.optInt("recurringExecutedCount", 0),
                        nextDueDate = nextDueDate,
                        isLoan = obj.optBoolean("isLoan", false),
                        loanPerson = obj.optString("loanPerson", ""),
                        loanDueDate = loanDueDate,
                        hasInterest = obj.optBoolean("hasInterest", false),
                        interestRate = interestRate,
                        interestType = obj.optString("interestType", "MONTHLY"),
                        hasTerm = obj.optBoolean("hasTerm", false),
                        termUnit = obj.optString("termUnit", "MONTHS"),
                        termLength = obj.optInt("termLength", 1),
                        isLoanSettled = obj.optBoolean("isLoanSettled", false),
                        isLoanPayment = obj.optBoolean("isLoanPayment", false),
                        loanReferenceId = loanReferenceId,
                        deletedAccountName = deletedAccountName,
                        deletedToAccountName = deletedToAccountName
                    )
                )
            }

            // Budgets
            val budgetsList = mutableListOf<BudgetEntity>()
            val budgetsArray = root.optJSONArray("budgets") ?: JSONArray()
            for (i in 0 until budgetsArray.length()) {
                val obj = budgetsArray.getJSONObject(i)
                budgetsList.add(
                    BudgetEntity(
                        id = obj.optLong("id", 0L),
                        categoryId = obj.optLong("categoryId", 0L),
                        monthlyLimit = obj.optDouble("monthlyLimit", 0.0),
                        notifyThresholdPercent = obj.optInt("notifyThresholdPercent", 80)
                    )
                )
            }

            val metadata = BackupMetadata(
                app = app,
                version = version,
                exportTimestamp = exportTimestamp,
                exportDateFormatted = exportDate,
                accountsCount = accountsList.size,
                categoriesCount = categoriesList.size,
                transactionsCount = transactionsList.size,
                budgetsCount = budgetsList.size
            )

            FullDatabaseBackup(
                metadata = metadata,
                accounts = accountsList,
                categories = categoriesList,
                transactions = transactionsList,
                budgets = budgetsList
            )
        }
    }

    fun generateDefaultBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "spendflow_backup_$timestamp.db"
    }

    fun generateDefaultJsonBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "spendflow_backup_$timestamp.json"
    }

    fun generateDefaultCsvFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "spendflow_transactions_$timestamp.csv"
    }

    fun getFileInfoFromUri(context: Context, uri: Uri): Pair<String, Long> {
        var name = "spendflow_backup.db"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // Use defaults if query fails
        }
        return Pair(name, size)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }

    fun isSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                val read = input.read(header)
                if (read == 16) {
                    val headerStr = String(header, Charsets.US_ASCII)
                    headerStr.startsWith("SQLite format 3")
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun checkpointDatabase(context: Context) {
        try {
            val dbFile = context.getDatabasePath("spendflow_database.db")
            if (dbFile.exists()) {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                ).use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                }
            }
        } catch (_: Exception) {
            // Checkpoint completed or ignored
        }
    }

    fun copyDatabaseToUri(context: Context, uri: Uri): Boolean {
        return try {
            checkpointDatabase(context)
            val dbFile = context.getDatabasePath("spendflow_database.db")
            if (!dbFile.exists()) return false
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                dbFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun copyUriToTempFile(context: Context, uri: Uri, tempFileName: String = "probe_restore.db"): File? {
        return try {
            val tempFile = File(context.cacheDir, tempFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveSqliteDatabaseToDevice(
        context: Context,
        fileName: String = generateDefaultBackupFileName()
    ): File? {
        return try {
            checkpointDatabase(context)
            val dbFile = context.getDatabasePath("spendflow_database.db")
            if (!dbFile.exists()) return null

            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(context.filesDir, "backups")
            if (!docsDir.exists()) docsDir.mkdirs()
            val backupFile = File(docsDir, fileName)
            dbFile.copyTo(backupFile, overwrite = true)
            backupFile
        } catch (_: Exception) {
            try {
                val internalDir = File(context.filesDir, "backups").apply { mkdirs() }
                val backupFile = File(internalDir, fileName)
                val dbFile = context.getDatabasePath("spendflow_database.db")
                if (dbFile.exists()) {
                    dbFile.copyTo(backupFile, overwrite = true)
                    backupFile
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun shareSqliteDatabase(
        context: Context,
        fileName: String = generateDefaultBackupFileName()
    ) {
        try {
            checkpointDatabase(context)
            val dbFile = context.getDatabasePath("spendflow_database.db")
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val backupFile = File(backupDir, fileName)
            if (dbFile.exists()) {
                dbFile.copyTo(backupFile, overwrite = true)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-sqlite3"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "SpendFlow SQLite Database Backup ($fileName)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share SpendFlow SQLite Database")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (_: Exception) {
            // Fallback
        }
    }

    private fun hasTable(db: android.database.sqlite.SQLiteDatabase, tableName: String): Boolean {
        return try {
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use { cursor ->
                cursor.count > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parseSqliteDatabase(file: File): Result<FullDatabaseBackup> {
        return runCatching {
            val accounts = mutableListOf<AccountEntity>()
            val categories = mutableListOf<CategoryEntity>()
            val transactions = mutableListOf<TransactionEntity>()
            val budgets = mutableListOf<BudgetEntity>()

            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                // Accounts
                if (hasTable(db, "accounts")) {
                    db.rawQuery("SELECT * FROM accounts", null).use { cursor ->
                        val idIdx = cursor.getColumnIndex("id")
                        val nameIdx = cursor.getColumnIndex("name")
                        val typeIdx = cursor.getColumnIndex("type")
                        val balanceIdx = cursor.getColumnIndex("balance")
                        val initialBalanceIdx = cursor.getColumnIndex("initialBalance")
                        val currencyIdx = cursor.getColumnIndex("currency")
                        val colorHexIdx = cursor.getColumnIndex("colorHex")
                        val iconNameIdx = cursor.getColumnIndex("iconName")
                        val maskIdx = cursor.getColumnIndex("accountNumberMask")
                        val limitIdx = cursor.getColumnIndex("creditLimit")
                        val stmtTypeIdx = cursor.getColumnIndex("statementDateType")
                        val stmtDayIdx = cursor.getColumnIndex("statementDay")
                        val stmtOffsetIdx = cursor.getColumnIndex("statementOffsetDays")
                        val graceIdx = cursor.getColumnIndex("paymentGraceDays")

                        while (cursor.moveToNext()) {
                            accounts.add(
                                AccountEntity(
                                    id = if (idIdx != -1) cursor.getLong(idIdx) else 0L,
                                    name = if (nameIdx != -1) cursor.getString(nameIdx) else "",
                                    type = if (typeIdx != -1) cursor.getString(typeIdx) else "CASH",
                                    balance = if (balanceIdx != -1) cursor.getDouble(balanceIdx) else 0.0,
                                    initialBalance = if (initialBalanceIdx != -1) cursor.getDouble(initialBalanceIdx) else 0.0,
                                    currency = if (currencyIdx != -1) cursor.getString(currencyIdx) else "PHP",
                                    colorHex = if (colorHexIdx != -1) cursor.getString(colorHexIdx) else "#10B981",
                                    iconName = if (iconNameIdx != -1) cursor.getString(iconNameIdx) else "account_balance_wallet",
                                    accountNumberMask = if (maskIdx != -1) cursor.getString(maskIdx) ?: "" else "",
                                    creditLimit = if (limitIdx != -1) cursor.getDouble(limitIdx) else 0.0,
                                    statementDateType = if (stmtTypeIdx != -1) cursor.getString(stmtTypeIdx) ?: "FIXED" else "FIXED",
                                    statementDay = if (stmtDayIdx != -1) cursor.getInt(stmtDayIdx) else 1,
                                    statementOffsetDays = if (stmtOffsetIdx != -1) cursor.getInt(stmtOffsetIdx) else 0,
                                    paymentGraceDays = if (graceIdx != -1) cursor.getInt(graceIdx) else 0
                                )
                            )
                        }
                    }
                }

                // Categories
                if (hasTable(db, "categories")) {
                    db.rawQuery("SELECT * FROM categories", null).use { cursor ->
                        val idIdx = cursor.getColumnIndex("id")
                        val nameIdx = cursor.getColumnIndex("name")
                        val typeIdx = cursor.getColumnIndex("type")
                        val iconIdx = cursor.getColumnIndex("iconName")
                        val colorIdx = cursor.getColumnIndex("colorHex")
                        val defaultIdx = cursor.getColumnIndex("isDefault")

                        while (cursor.moveToNext()) {
                            categories.add(
                                CategoryEntity(
                                    id = if (idIdx != -1) cursor.getLong(idIdx) else 0L,
                                    name = if (nameIdx != -1) cursor.getString(nameIdx) else "",
                                    type = if (typeIdx != -1) cursor.getString(typeIdx) else "EXPENSE",
                                    iconName = if (iconIdx != -1) cursor.getString(iconIdx) else "category",
                                    colorHex = if (colorIdx != -1) cursor.getString(colorIdx) else "#64748B",
                                    isDefault = if (defaultIdx != -1) cursor.getInt(defaultIdx) == 1 else false
                                )
                            )
                        }
                    }
                }

                // Transactions
                if (hasTable(db, "transactions")) {
                    db.rawQuery("SELECT * FROM transactions", null).use { cursor ->
                        val idIdx = cursor.getColumnIndex("id")
                        val typeIdx = cursor.getColumnIndex("type")
                        val amountIdx = cursor.getColumnIndex("amount")
                        val accountIdIdx = cursor.getColumnIndex("accountId")
                        val toAccountIdIdx = cursor.getColumnIndex("toAccountId")
                        val feeIdx = cursor.getColumnIndex("transferFee")
                        val catIdIdx = cursor.getColumnIndex("categoryId")
                        val titleIdx = cursor.getColumnIndex("title")
                        val noteIdx = cursor.getColumnIndex("note")
                        val timeIdx = cursor.getColumnIndex("timestamp")
                        val recurIdx = cursor.getColumnIndex("isRecurring")
                        val freqIdx = cursor.getColumnIndex("recurringFrequency")
                        val totalCntIdx = cursor.getColumnIndex("recurringTotalCount")
                        val execCntIdx = cursor.getColumnIndex("recurringExecutedCount")
                        val nextDueIdx = cursor.getColumnIndex("nextDueDate")
                        val isLoanIdx = cursor.getColumnIndex("isLoan")
                        val loanPersonIdx = cursor.getColumnIndex("loanPerson")
                        val loanDueIdx = cursor.getColumnIndex("loanDueDate")
                        val hasIntIdx = cursor.getColumnIndex("hasInterest")
                        val intRateIdx = cursor.getColumnIndex("interestRate")
                        val intTypeIdx = cursor.getColumnIndex("interestType")
                        val hasTermIdx = cursor.getColumnIndex("hasTerm")
                        val termUnitIdx = cursor.getColumnIndex("termUnit")
                        val termLenIdx = cursor.getColumnIndex("termLength")
                        val isSettledIdx = cursor.getColumnIndex("isLoanSettled")
                        val isLoanPayIdx = cursor.getColumnIndex("isLoanPayment")
                        val loanRefIdx = cursor.getColumnIndex("loanReferenceId")
                        val delAccIdx = cursor.getColumnIndex("deletedAccountName")
                        val delToAccIdx = cursor.getColumnIndex("deletedToAccountName")

                        while (cursor.moveToNext()) {
                            val toAcc = if (toAccountIdIdx != -1 && !cursor.isNull(toAccountIdIdx)) cursor.getLong(toAccountIdIdx) else null
                            val catId = if (catIdIdx != -1 && !cursor.isNull(catIdIdx)) cursor.getLong(catIdIdx) else null
                            val nextDue = if (nextDueIdx != -1 && !cursor.isNull(nextDueIdx)) cursor.getLong(nextDueIdx) else null
                            val loanDue = if (loanDueIdx != -1 && !cursor.isNull(loanDueIdx)) cursor.getLong(loanDueIdx) else null
                            val intRate = if (intRateIdx != -1 && !cursor.isNull(intRateIdx)) cursor.getDouble(intRateIdx) else null
                            val loanRef = if (loanRefIdx != -1 && !cursor.isNull(loanRefIdx)) cursor.getLong(loanRefIdx) else null
                            val delAcc = if (delAccIdx != -1 && !cursor.isNull(delAccIdx)) cursor.getString(delAccIdx) else null
                            val delToAcc = if (delToAccIdx != -1 && !cursor.isNull(delToAccIdx)) cursor.getString(delToAccIdx) else null

                            transactions.add(
                                TransactionEntity(
                                    id = if (idIdx != -1) cursor.getLong(idIdx) else 0L,
                                    type = if (typeIdx != -1) cursor.getString(typeIdx) else "EXPENSE",
                                    amount = if (amountIdx != -1) cursor.getDouble(amountIdx) else 0.0,
                                    accountId = if (accountIdIdx != -1) cursor.getLong(accountIdIdx) else 0L,
                                    toAccountId = toAcc,
                                    transferFee = if (feeIdx != -1) cursor.getDouble(feeIdx) else 0.0,
                                    categoryId = catId,
                                    title = if (titleIdx != -1) cursor.getString(titleIdx) else "Transaction",
                                    note = if (noteIdx != -1) cursor.getString(noteIdx) ?: "" else "",
                                    timestamp = if (timeIdx != -1) cursor.getLong(timeIdx) else System.currentTimeMillis(),
                                    isRecurring = if (recurIdx != -1) cursor.getInt(recurIdx) == 1 else false,
                                    recurringFrequency = if (freqIdx != -1) cursor.getString(freqIdx) ?: "NONE" else "NONE",
                                    recurringTotalCount = if (totalCntIdx != -1) cursor.getInt(totalCntIdx) else 0,
                                    recurringExecutedCount = if (execCntIdx != -1) cursor.getInt(execCntIdx) else 0,
                                    nextDueDate = nextDue,
                                    isLoan = if (isLoanIdx != -1) cursor.getInt(isLoanIdx) == 1 else false,
                                    loanPerson = if (loanPersonIdx != -1) cursor.getString(loanPersonIdx) ?: "" else "",
                                    loanDueDate = loanDue,
                                    hasInterest = if (hasIntIdx != -1) cursor.getInt(hasIntIdx) == 1 else false,
                                    interestRate = intRate,
                                    interestType = if (intTypeIdx != -1) cursor.getString(intTypeIdx) ?: "MONTHLY" else "MONTHLY",
                                    hasTerm = if (hasTermIdx != -1) cursor.getInt(hasTermIdx) == 1 else false,
                                    termUnit = if (termUnitIdx != -1) cursor.getString(termUnitIdx) ?: "MONTHS" else "MONTHS",
                                    termLength = if (termLenIdx != -1) cursor.getInt(termLenIdx) else 1,
                                    isLoanSettled = if (isSettledIdx != -1) cursor.getInt(isSettledIdx) == 1 else false,
                                    isLoanPayment = if (isLoanPayIdx != -1) cursor.getInt(isLoanPayIdx) == 1 else false,
                                    loanReferenceId = loanRef,
                                    deletedAccountName = delAcc,
                                    deletedToAccountName = delToAcc
                                )
                            )
                        }
                    }
                }

                // Budgets
                if (hasTable(db, "budgets")) {
                    db.rawQuery("SELECT * FROM budgets", null).use { cursor ->
                        val idIdx = cursor.getColumnIndex("id")
                        val catIdIdx = cursor.getColumnIndex("categoryId")
                        val limitIdx = cursor.getColumnIndex("monthlyLimit")
                        val notifyIdx = cursor.getColumnIndex("notifyThresholdPercent")

                        while (cursor.moveToNext()) {
                            budgets.add(
                                BudgetEntity(
                                    id = if (idIdx != -1) cursor.getLong(idIdx) else 0L,
                                    categoryId = if (catIdIdx != -1) cursor.getLong(catIdIdx) else 0L,
                                    monthlyLimit = if (limitIdx != -1) cursor.getDouble(limitIdx) else 0.0,
                                    notifyThresholdPercent = if (notifyIdx != -1) cursor.getInt(notifyIdx) else 80
                                )
                            )
                        }
                    }
                }
            }

            val metadata = BackupMetadata(
                app = APP_IDENTIFIER,
                version = CURRENT_BACKUP_VERSION,
                exportTimestamp = file.lastModified(),
                exportDateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(file.lastModified())),
                accountsCount = accounts.size,
                categoriesCount = categories.size,
                transactionsCount = transactions.size,
                budgetsCount = budgets.size
            )

            FullDatabaseBackup(
                metadata = metadata,
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                budgets = budgets
            )
        }
    }

    fun saveTextToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveBackupToDevice(context: Context, backupJson: String, fileName: String = generateDefaultJsonBackupFileName()): File? {
        return try {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(context.filesDir, "backups")
            if (!docsDir.exists()) docsDir.mkdirs()
            val backupFile = File(docsDir, fileName)
            backupFile.writeText(backupJson, Charsets.UTF_8)
            backupFile
        } catch (_: Exception) {
            try {
                val internalDir = File(context.filesDir, "backups").apply { mkdirs() }
                val backupFile = File(internalDir, fileName)
                backupFile.writeText(backupJson, Charsets.UTF_8)
                backupFile
            } catch (_: Exception) {
                null
            }
        }
    }

    fun getDeviceBackupFiles(context: Context): List<File> {
        val files = mutableListOf<File>()
        val allowedExtensions = setOf("db", "sqlite", "sqlite3", "json")
        try {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            docsDir?.listFiles { f -> f.extension.lowercase(Locale.US) in allowedExtensions }?.let {
                files.addAll(it)
            }
            val internalDir = File(context.filesDir, "backups")
            internalDir.listFiles { f -> f.extension.lowercase(Locale.US) in allowedExtensions }?.let {
                files.addAll(it)
            }
        } catch (_: Exception) {
            // Ignore
        }
        return files.distinctBy { it.name }.sortedByDescending { it.lastModified() }
    }

    fun shareBackupFile(context: Context, backupJson: String, fileName: String = generateDefaultJsonBackupFileName()) {
        try {
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val backupFile = File(backupDir, fileName)
            backupFile.writeText(backupJson, Charsets.UTF_8)

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "SpendFlow Database Backup ($fileName)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share SpendFlow Backup File")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (_: Exception) {
            // Fallback to text intent if file provider fails
            shareBackup(context, backupJson)
        }
    }

    fun shareCsvFile(context: Context, csvContent: String, fileName: String = generateDefaultCsvFileName()) {
        try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val csvFile = File(reportsDir, fileName)
            csvFile.writeText(csvContent, Charsets.UTF_8)

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "SpendFlow CSV Report ($fileName)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share SpendFlow CSV File")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (_: Exception) {
            // Fallback
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, csvContent)
                putExtra(Intent.EXTRA_SUBJECT, "SpendFlow CSV Report ($fileName)")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share SpendFlow CSV Report")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        }
    }

    fun shareBackup(context: Context, backupJson: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, backupJson)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "SpendFlow Full Database Backup (${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())})"
            )
            type = "application/json"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooserIntent = Intent.createChooser(sendIntent, "Share SpendFlow Database Backup")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    fun getSampleBackupTemplate(): String {
        return """
{
  "metadata": {
    "app": "SpendFlow",
    "version": 1,
    "exportTimestamp": 1792834921000,
    "exportDate": "2026-10-25 12:00:00",
    "accountsCount": 2,
    "categoriesCount": 3,
    "transactionsCount": 2,
    "budgetsCount": 1
  },
  "accounts": [
    {
      "id": 1,
      "name": "Cash Wallet",
      "type": "CASH",
      "balance": 5000.0,
      "initialBalance": 5000.0,
      "currency": "PHP",
      "colorHex": "#10B981",
      "iconName": "local_atm",
      "accountNumberMask": "",
      "creditLimit": 0.0,
      "statementDateType": "FIXED",
      "statementDay": 1,
      "statementOffsetDays": 0,
      "paymentGraceDays": 0
    },
    {
      "id": 2,
      "name": "Savings Account",
      "type": "SAVINGS",
      "balance": 25000.0,
      "initialBalance": 25000.0,
      "currency": "PHP",
      "colorHex": "#3B82F6",
      "iconName": "account_balance",
      "accountNumberMask": "*4521",
      "creditLimit": 0.0,
      "statementDateType": "FIXED",
      "statementDay": 1,
      "statementOffsetDays": 0,
      "paymentGraceDays": 0
    }
  ],
  "categories": [
    {
      "id": 1,
      "name": "Food & Dining",
      "type": "EXPENSE",
      "iconName": "restaurant",
      "colorHex": "#F59E0B",
      "isDefault": true
    },
    {
      "id": 2,
      "name": "Transportation",
      "type": "EXPENSE",
      "iconName": "directions_car",
      "colorHex": "#3B82F6",
      "isDefault": true
    },
    {
      "id": 3,
      "name": "Salary",
      "type": "INCOME",
      "iconName": "work",
      "colorHex": "#10B981",
      "isDefault": true
    }
  ],
  "transactions": [
    {
      "id": 1,
      "type": "INCOME",
      "amount": 25000.0,
      "accountId": 2,
      "categoryId": 3,
      "title": "Monthly Salary",
      "note": "Company payroll",
      "timestamp": 1792800000000,
      "isRecurring": true,
      "recurringFrequency": "MONTHLY"
    },
    {
      "id": 2,
      "type": "EXPENSE",
      "amount": 350.0,
      "accountId": 1,
      "categoryId": 1,
      "title": "Lunch with colleagues",
      "note": "Bistro",
      "timestamp": 1792820000000,
      "isRecurring": false
    }
  ],
  "budgets": [
    {
      "id": 1,
      "categoryId": 1,
      "monthlyLimit": 10000.0,
      "notifyThresholdPercent": 80
    }
  ]
}
        """.trimIndent()
    }
}
