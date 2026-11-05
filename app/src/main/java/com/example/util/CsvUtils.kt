package com.example.util

import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.RecurringFrequency
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import java.io.BufferedReader
import java.io.StringReader

object CsvUtils {

    const val CSV_HEADER = "id,date,type,title,amount,account,category,to_account,fee,note,is_recurring,frequency"

    fun exportTransactionsToCsv(transactions: List<TransactionWithDetails>): String {
        val sb = StringBuilder()
        sb.append(CSV_HEADER).append("\n")

        for (item in transactions) {
            val tx = item.transaction
            val escapedTitle = escapeCsvField(tx.title)
            val escapedNote = escapeCsvField(tx.note)
            val escapedAccount = escapeCsvField(item.accountName)
            val escapedCategory = escapeCsvField(item.categoryName ?: "Uncategorized")
            val escapedToAccount = escapeCsvField(item.toAccountName ?: "")

            val line = listOf(
                tx.id.toString(),
                DateUtils.formatCsvDate(tx.timestamp),
                tx.type.name,
                escapedTitle,
                String.format(java.util.Locale.US, "%.2f", tx.amount),
                escapedAccount,
                escapedCategory,
                escapedToAccount,
                String.format(java.util.Locale.US, "%.2f", tx.transferFee),
                escapedNote,
                tx.isRecurring.toString(),
                tx.recurringFrequency.name
            ).joinToString(",")

            sb.append(line).append("\n")
        }

        return sb.toString()
    }

    data class ParsedCsvRow(
        val date: Long,
        val type: TransactionType,
        val title: String,
        val amount: Double,
        val accountName: String,
        val categoryName: String,
        val toAccountName: String,
        val fee: Double,
        val note: String,
        val isRecurring: Boolean,
        val frequency: RecurringFrequency
    )

    fun parseTransactionsCsv(csvContent: String): List<ParsedCsvRow> {
        val rows = mutableListOf<ParsedCsvRow>()
        val reader = BufferedReader(StringReader(csvContent))
        var line: String? = reader.readLine() // Read header

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            if (currentLine.isBlank()) continue

            val tokens = parseCsvLine(currentLine)
            if (tokens.size < 5) continue

            try {
                // Header: id,date,type,title,amount,account,category,to_account,fee,note,is_recurring,frequency
                val dateStr = tokens.getOrNull(1) ?: ""
                val timestamp = DateUtils.parseCsvDate(dateStr) ?: System.currentTimeMillis()
                val typeStr = tokens.getOrNull(2) ?: "EXPENSE"
                val type = runCatching { TransactionType.valueOf(typeStr.uppercase()) }.getOrDefault(TransactionType.EXPENSE)
                val title = tokens.getOrNull(3) ?: "Imported"
                val amount = tokens.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                val accountName = tokens.getOrNull(5) ?: "Default Account"
                val categoryName = tokens.getOrNull(6) ?: "Other Expense"
                val toAccountName = tokens.getOrNull(7) ?: ""
                val fee = tokens.getOrNull(8)?.toDoubleOrNull() ?: 0.0
                val note = tokens.getOrNull(9) ?: ""
                val isRecurring = tokens.getOrNull(10)?.toBooleanStrictOrNull() ?: false
                val frequencyStr = tokens.getOrNull(11) ?: "NONE"
                val frequency = runCatching { RecurringFrequency.valueOf(frequencyStr.uppercase()) }.getOrDefault(RecurringFrequency.NONE)

                rows.add(
                    ParsedCsvRow(
                        date = timestamp,
                        type = type,
                        title = title,
                        amount = amount,
                        accountName = accountName,
                        categoryName = categoryName,
                        toAccountName = toAccountName,
                        fee = fee,
                        note = note,
                        isRecurring = isRecurring,
                        frequency = frequency
                    )
                )
            } catch (_: Exception) {
                // Skip malformed row
            }
        }
        return rows
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
