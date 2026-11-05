package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val flag: String
)

object ExchangeRateService {

    private const val TAG = "ExchangeRateService"
    private const val PREFS_NAME = "spendflow_exchange_rates"
    private const val KEY_CACHED_RATES_JSON = "cached_rates_json"
    private const val KEY_LAST_FETCH_TIMESTAMP = "last_fetch_timestamp"
    private const val CACHE_VALIDITY_MS = 6 * 60 * 60 * 1000L // 6 hours

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Supported world currencies with accurate symbols, names, and flag indicators.
     */
    val SUPPORTED_CURRENCIES: List<CurrencyInfo> = listOf(
        CurrencyInfo("PHP", "Philippine Peso", "₱", "🇵🇭"),
        CurrencyInfo("USD", "US Dollar", "$", "🇺🇸"),
        CurrencyInfo("EUR", "Euro", "€", "🇪🇺"),
        CurrencyInfo("GBP", "British Pound", "£", "🇬🇧"),
        CurrencyInfo("JPY", "Japanese Yen", "¥", "🇯🇵"),
        CurrencyInfo("CAD", "Canadian Dollar", "CA$", "🇨🇦"),
        CurrencyInfo("AUD", "Australian Dollar", "A$", "🇦🇺"),
        CurrencyInfo("SGD", "Singapore Dollar", "S$", "🇸🇬"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK$", "🇭🇰"),
        CurrencyInfo("CNY", "Chinese Yuan", "¥", "🇨🇳"),
        CurrencyInfo("INR", "Indian Rupee", "₹", "🇮🇳"),
        CurrencyInfo("KRW", "South Korean Won", "₩", "🇰🇷"),
        CurrencyInfo("CHF", "Swiss Franc", "CHF", "🇨🇭"),
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ$", "🇳🇿"),
        CurrencyInfo("THB", "Thai Baht", "฿", "🇹🇭"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM", "🇲🇾"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Rp", "🇮🇩"),
        CurrencyInfo("VND", "Vietnamese Dong", "₫", "🇻🇳"),
        CurrencyInfo("AED", "UAE Dirham", "AED", "🇦🇪"),
        CurrencyInfo("SAR", "Saudi Riyal", "SAR", "🇸🇦"),
        CurrencyInfo("BRL", "Brazilian Real", "R$", "🇧🇷"),
        CurrencyInfo("MXN", "Mexican Peso", "Mex$", "🇲🇽"),
        CurrencyInfo("TWD", "New Taiwan Dollar", "NT$", "🇹🇼"),
        CurrencyInfo("SEK", "Swedish Krona", "kr", "🇸🇪"),
        CurrencyInfo("NOK", "Norwegian Krone", "kr", "🇳🇴"),
        CurrencyInfo("TRY", "Turkish Lira", "₺", "🇹🇷")
    )

    /**
     * Baseline exchange rates relative to USD = 1.0.
     * Used when offline or before initial network fetch completes.
     */
    val FALLBACK_RATES_TO_USD: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "PHP" to 58.60,
        "EUR" to 0.92,
        "GBP" to 0.77,
        "JPY" to 153.20,
        "CAD" to 1.39,
        "AUD" to 1.52,
        "SGD" to 1.32,
        "HKD" to 7.77,
        "CNY" to 7.15,
        "INR" to 84.10,
        "KRW" to 1385.0,
        "CHF" to 0.87,
        "NZD" to 1.66,
        "THB" to 33.80,
        "MYR" to 4.36,
        "IDR" to 15720.0,
        "VND" to 25350.0,
        "AED" to 3.67,
        "SAR" to 3.75,
        "BRL" to 5.75,
        "MXN" to 20.10,
        "TWD" to 32.10,
        "SEK" to 10.65,
        "NOK" to 11.05,
        "TRY" to 34.35
    )

    @Volatile
    private var inMemoryRates: Map<String, Double>? = null

    fun getCurrencyInfo(code: String): CurrencyInfo {
        val upper = code.uppercase().trim()
        return SUPPORTED_CURRENCIES.find { it.code.equals(upper, ignoreCase = true) }
            ?: CurrencyInfo(code = upper, name = upper, symbol = upper, flag = "🌐")
    }

    fun getCurrencySymbol(code: String): String {
        return getCurrencyInfo(code).symbol
    }

    /**
     * Retrieves currently available exchange rates (relative to USD = 1.0).
     * Order of priority: In-memory cache -> SharedPreferences cache -> Built-in Fallback.
     */
    fun getRates(context: Context): Map<String, Double> {
        inMemoryRates?.let { return it }

        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = sp.getString(KEY_CACHED_RATES_JSON, null)
        if (!jsonStr.isNullOrBlank()) {
            val parsed = parseRatesJson(jsonStr)
            if (parsed.isNotEmpty()) {
                inMemoryRates = parsed
                return parsed
            }
        }

        return FALLBACK_RATES_TO_USD
    }

    /**
     * Checks if cached exchange rates exist and are fresh.
     */
    fun isCacheValid(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = sp.getLong(KEY_LAST_FETCH_TIMESTAMP, 0L)
        return (System.currentTimeMillis() - lastFetch) < CACHE_VALIDITY_MS && sp.contains(KEY_CACHED_RATES_JSON)
    }

    /**
     * Fetches live exchange rates from public API over the internet.
     * Uses open.er-api.com (free, reliable, HTTPS, no API key required).
     */
    suspend fun fetchRatesFromInternet(context: Context, forceRefresh: Boolean = false): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh && isCacheValid(context)) {
                    val cached = getRates(context)
                    if (cached.isNotEmpty()) {
                        return@withContext Result.success(cached)
                    }
                }

                val url = "https://open.er-api.com/v6/latest/USD"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SpendFlow-Android/1.0")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    Log.w(TAG, "Failed HTTP response: ${response.code}")
                    return@withContext Result.success(getRates(context))
                }

                val json = JSONObject(body)
                val resultStatus = json.optString("result", "")
                if (resultStatus.equals("success", ignoreCase = true) || json.has("rates")) {
                    val ratesObj = json.getJSONObject("rates")
                    val newRates = mutableMapOf<String, Double>()

                    // Copy all fallback rates as foundation, then override with live rates
                    newRates.putAll(FALLBACK_RATES_TO_USD)

                    val keys = ratesObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val rate = ratesObj.optDouble(key, -1.0)
                        if (rate > 0) {
                            newRates[key.uppercase()] = rate
                        }
                    }

                    // Save to SharedPreferences
                    val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    sp.edit()
                        .putString(KEY_CACHED_RATES_JSON, JSONObject(newRates as Map<*, *>).toString())
                        .putLong(KEY_LAST_FETCH_TIMESTAMP, System.currentTimeMillis())
                        .apply()

                    inMemoryRates = newRates
                    Log.i(TAG, "Successfully fetched and saved ${newRates.size} exchange rates from internet.")
                    return@withContext Result.success(newRates)
                } else {
                    Log.w(TAG, "Unexpected JSON payload from exchange rate API.")
                    return@withContext Result.success(getRates(context))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying internet exchange rates: ${e.message}")
                return@withContext Result.success(getRates(context))
            }
        }
    }

    /**
     * Converts an amount from one currency to another using the rates table (base USD).
     * conversion formula: amount * (toRate / fromRate)
     */
    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        rates: Map<String, Double>
    ): Double {
        val fromUpper = fromCurrency.uppercase().trim()
        val toUpper = toCurrency.uppercase().trim()

        if (fromUpper == toUpper) return CurrencyUtils.roundTo2Decimals(amount)
        if (amount == 0.0) return 0.0

        val fromRate = rates[fromUpper] ?: FALLBACK_RATES_TO_USD[fromUpper] ?: 1.0
        val toRate = rates[toUpper] ?: FALLBACK_RATES_TO_USD[toUpper] ?: 1.0

        if (fromRate <= 0.0) return CurrencyUtils.roundTo2Decimals(amount)

        val converted = amount * (toRate / fromRate)
        return CurrencyUtils.roundTo2Decimals(converted)
    }

    /**
     * Formats an exchange rate display string, e.g. "1 USD ≈ 58.60 PHP"
     */
    fun formatExchangeRate(
        fromCurrency: String,
        toCurrency: String,
        rates: Map<String, Double>
    ): String {
        val fromUpper = fromCurrency.uppercase().trim()
        val toUpper = toCurrency.uppercase().trim()

        if (fromUpper == toUpper) return "1 $fromUpper = 1 $toUpper"

        val convertedOne = convert(1.0, fromUpper, toUpper, rates)
        return "1 $fromUpper ≈ ${DateUtils.formatNumber(convertedOne)} $toUpper"
    }

    /**
     * Returns a human-readable string indicating when rates were last updated.
     */
    fun getLastUpdatedFormatted(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = sp.getLong(KEY_LAST_FETCH_TIMESTAMP, 0L)
        if (timestamp == 0L) return "Offline estimates (Ready to refresh)"

        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return "Updated: ${sdf.format(Date(timestamp))}"
    }

    private fun parseRatesJson(jsonStr: String): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        try {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key.uppercase()] = obj.getDouble(key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached exchange rates JSON: ${e.message}")
        }
        return map
    }
}
