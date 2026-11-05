package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val shortDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val dayOfWeekFormat = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
    private val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun formatDate(timestamp: Long): String = shortDateFormat.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))
    fun formatMonthYear(timestamp: Long): String = monthYearFormat.format(Date(timestamp))
    fun formatDayOfWeek(timestamp: Long): String = dayOfWeekFormat.format(Date(timestamp))
    fun formatCsvDate(timestamp: Long): String = csvDateFormat.format(Date(timestamp))

    fun parseCsvDate(dateStr: String): Long? {
        return runCatching { csvDateFormat.parse(dateStr)?.time }.getOrNull()
            ?: runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time }.getOrNull()
    }

    fun getDefaultCurrencyCode(): String {
        return "PHP"
    }

    fun formatNumber(amount: Double): String {
        return String.format(Locale.US, "%,.2f", kotlin.math.abs(amount))
    }

    fun formatCurrency(amount: Double, currencyCode: String = getDefaultCurrencyCode()): String {
        if (currencyCode == "PHP") {
            val isNegative = amount < 0
            val absAmount = kotlin.math.abs(amount)
            val formatted = String.format(Locale.US, "%,.2f", absAmount)
            return if (isNegative) "-₱$formatted" else "₱$formatted"
        }
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale.US)
            format.currency = java.util.Currency.getInstance(currencyCode)
            format.format(amount)
        } catch (_: Exception) {
            val isNegative = amount < 0
            val absAmount = kotlin.math.abs(amount)
            val formatted = String.format(Locale.US, "%,.2f", absAmount)
            if (currencyCode == "PHP") (if (isNegative) "-₱$formatted" else "₱$formatted")
            else (if (isNegative) "-$$formatted" else "$$formatted")
        }
    }

    enum class Timeframe(val label: String) {
        DAILY("Daily"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly"),
        YEARLY("Yearly"),
        CUSTOM("Custom")
    }

    fun getTimeframeRange(
        timeframe: Timeframe,
        referenceTime: Long = System.currentTimeMillis(),
        customStart: Long = referenceTime - 30L * 24 * 3600 * 1000,
        customEnd: Long = referenceTime
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTime }

        return when (timeframe) {
            Timeframe.DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            Timeframe.WEEKLY -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            Timeframe.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            Timeframe.YEARLY -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            Timeframe.CUSTOM -> {
                val start = getStartOfDay(minOf(customStart, customEnd))
                val end = getEndOfDay(maxOf(customStart, customEnd))
                Pair(start, end)
            }
        }
    }

    fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }
}
