package com.example.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyUtils {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val displayFormat = DecimalFormat("#,##0.00", symbols)
    private val wholeDisplayFormat = DecimalFormat("#,##0", symbols)

    /**
     * Strictly rounds a double to 2 decimal places using HALF_UP
     * to eliminate IEEE 754 floating-point drift (e.g., 0.000000001).
     */
    fun roundTo2Decimals(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * Formats an amount for display with commas and 2 decimals, e.g. "1,234.56"
     */
    fun formatDisplay(value: Double): String {
        return displayFormat.format(roundTo2Decimals(value))
    }

    /**
     * Formats an amount with currency symbol, e.g. "$1,234.56"
     */
    fun formatWithSymbol(value: Double, symbol: String = "$"): String {
        val rounded = roundTo2Decimals(value)
        return if (rounded < 0) {
            "-$symbol${displayFormat.format(-rounded)}"
        } else {
            "$symbol${displayFormat.format(rounded)}"
        }
    }

    /**
     * Formats a double value for initial display in an input field.
     * If showZeroAsEmpty is true and value is 0.0, returns "" so the placeholder is shown.
     */
    fun formatDoubleToInput(value: Double, showZeroAsEmpty: Boolean = false): String {
        val rounded = roundTo2Decimals(value)
        if (showZeroAsEmpty && rounded == 0.0) return ""
        return displayFormat.format(rounded)
    }

    /**
     * Creates a TextFieldValue from a Double with cursor placed at the end.
     */
    fun toTextFieldValue(value: Double, showZeroAsEmpty: Boolean = false): TextFieldValue {
        val text = formatDoubleToInput(value, showZeroAsEmpty)
        return TextFieldValue(text = text, selection = TextRange(text.length))
    }

    /**
     * Creates a TextFieldValue from a String with commas formatted and cursor at the end.
     */
    fun toTextFieldValue(rawInput: String): TextFieldValue {
        val formatted = formatCurrencyInput(rawInput)
        return TextFieldValue(text = formatted, selection = TextRange(formatted.length))
    }

    /**
     * Automatically formats typed currency input with commas on thousands/millions
     * while preserving up to 2 decimal places.
     * E.g.:
     * "1234" -> "1,234"
     * "1234." -> "1,234."
     * "1234.5" -> "1,234.5"
     * "1234.56" -> "1,234.56"
     * "1234.567" -> "1,234.56" (trims beyond 2 decimals)
     * "-1234" -> "-1,234"
     */
    fun formatCurrencyInput(rawInput: String): String {
        if (rawInput.isBlank()) return ""

        val isNegative = rawInput.trim().startsWith("-")
        // Remove everything except digits and dot
        val cleaned = rawInput.filter { it.isDigit() || it == '.' }
        if (cleaned.isEmpty()) return if (isNegative) "-" else ""

        // Handle multiple decimal dots by keeping only the first
        val parts = cleaned.split('.')
        val integerPartDigits = parts[0].ifEmpty { "0" }
        val hasDecimalPoint = cleaned.contains('.')

        // Format integer part with commas
        val parsedInt = integerPartDigits.toLongOrNull()
        val formattedIntegerPart = if (parsedInt != null) {
            wholeDisplayFormat.format(parsedInt)
        } else {
            formatWithCommasRegex(integerPartDigits)
        }

        val prefix = if (isNegative) "-" else ""

        return if (hasDecimalPoint) {
            // Keep at most 2 decimal digits
            val decimalDigits = if (parts.size > 1) parts[1].take(2) else ""
            "$prefix$formattedIntegerPart.$decimalDigits"
        } else {
            "$prefix$formattedIntegerPart"
        }
    }

    private fun formatWithCommasRegex(digits: String): String {
        val reversed = digits.reversed()
        val chunked = reversed.chunked(3).joinToString(",")
        return chunked.reversed()
    }

    /**
     * Formats a TextFieldValue with commas while preserving the user's cursor position.
     * Prevents the cursor from jumping between the 1st and 2nd digit when commas are added.
     */
    fun formatCurrencyInput(input: TextFieldValue, previousText: String? = null): TextFieldValue {
        val rawText = input.text
        if (rawText.isBlank()) {
            return TextFieldValue("", TextRange.Zero)
        }

        var textToFormat = rawText
        var cursorEnd = input.selection.end

        // Detect if the user backspaced a comma directly in previous text:
        // E.g. previous was "1,234" with cursor at 2 ("1,|234").
        // Backspace removes comma, resulting in "1234" with cursor at 1 ("1|234").
        // If this occurs, also delete the digit preceding the deleted comma so backspacing feels natural.
        if (previousText != null &&
            previousText.length == rawText.length + 1 &&
            input.selection.collapsed &&
            cursorEnd in 1 until previousText.length &&
            previousText[cursorEnd] == ','
        ) {
            val beforeCursor = rawText.substring(0, cursorEnd - 1)
            val afterCursor = rawText.substring(cursorEnd)
            textToFormat = beforeCursor + afterCursor
            cursorEnd = (cursorEnd - 1).coerceAtLeast(0)
        }

        val formatted = formatCurrencyInput(textToFormat)
        val newCursor = calculateCursorPosition(textToFormat, cursorEnd, formatted)

        return TextFieldValue(
            text = formatted,
            selection = TextRange(newCursor)
        )
    }

    /**
     * Calculates the new cursor position after formatting so the cursor stays
     * right after the digit the user just typed, even when commas shift.
     */
    fun calculateCursorPosition(
        originalText: String,
        rawCursor: Int,
        formattedText: String
    ): Int {
        if (formattedText.isEmpty()) return 0
        val safeRawCursor = rawCursor.coerceIn(0, originalText.length)
        val prefix = originalText.take(safeRawCursor)

        val hasDotBeforeCursor = prefix.contains('.')
        val isCursorAfterDot = prefix.endsWith('.')
        val decimalsBeforeCursor = if (hasDotBeforeCursor) {
            prefix.substringAfter('.').count { it.isDigit() }.coerceAtMost(2)
        } else 0
        val digitsBeforeCursor = prefix.count { it.isDigit() }

        // If cursor was in or after the decimal part
        if (hasDotBeforeCursor) {
            val dotIndex = formattedText.indexOf('.')
            if (dotIndex != -1) {
                val targetPos = if (isCursorAfterDot && decimalsBeforeCursor == 0) {
                    dotIndex + 1
                } else {
                    dotIndex + 1 + decimalsBeforeCursor
                }
                return targetPos.coerceIn(0, formattedText.length)
            }
        }

        // If cursor was at the very start before any digits
        if (digitsBeforeCursor == 0) {
            return if (formattedText.startsWith("-")) 1 else 0
        }

        // Match the exact count of digits in the integer part of formattedText
        var digitCount = 0
        for (i in formattedText.indices) {
            if (formattedText[i].isDigit()) {
                digitCount++
                if (digitCount == digitsBeforeCursor) {
                    return (i + 1).coerceIn(0, formattedText.length)
                }
            }
        }

        return formattedText.length
    }

    /**
     * Parses formatted currency input string (e.g. "1,234.56" or "-1,234.5") into a 2-decimal rounded Double.
     * Returns 0.0 if blank or invalid.
     */
    fun parseCurrencyInput(text: String): Double {
        if (text.isBlank() || text == "-" || text == ".") return 0.0
        val clean = text.replace(",", "").trim()
        val parsed = clean.toDoubleOrNull() ?: 0.0
        return roundTo2Decimals(parsed)
    }

    /**
     * Overload for TextFieldValue.
     */
    fun parseCurrencyInput(value: TextFieldValue): Double = parseCurrencyInput(value.text)
}
