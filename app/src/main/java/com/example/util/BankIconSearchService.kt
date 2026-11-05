package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class BankIconCandidate(
    val title: String,
    val imageUrl: String,
    val source: String,
    val domain: String? = null
)

object BankIconSearchService {

    private const val TAG = "BankIconSearchService"
    private const val LOCAL_ICON_PREFIX = "local:"
    private const val ICONS_DIR_NAME = "bank_icons"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // Curated catalog of high-resolution domains for common banks & financial services (PH, US, Global, Fintech)
    private val knownInstitutions: Map<String, Pair<String, String>> = mapOf(
        // Philippines
        "bdo" to Pair("BDO Unibank", "bdo.com.ph"),
        "bpi" to Pair("Bank of the Philippine Islands (BPI)", "bpi.com.ph"),
        "metrobank" to Pair("Metrobank", "metrobank.com.ph"),
        "unionbank" to Pair("UnionBank of the Philippines", "unionbankph.com"),
        "ub" to Pair("UnionBank of the Philippines", "unionbankph.com"),
        "landbank" to Pair("Land Bank of the Philippines", "landbank.com"),
        "security bank" to Pair("Security Bank", "securitybank.com"),
        "rcbc" to Pair("RCBC", "rcbc.com"),
        "pnb" to Pair("Philippine National Bank", "pnb.com.ph"),
        "chinabank" to Pair("China Banking Corporation", "chinabank.ph"),
        "eastwest" to Pair("EastWest Bank", "eastwestbanker.com"),
        "gcash" to Pair("GCash", "gcash.com"),
        "maya" to Pair("Maya", "maya.ph"),
        "paymaya" to Pair("Maya", "maya.ph"),
        "gotyme" to Pair("GoTyme Bank", "gotyme.com.ph"),
        "seabank" to Pair("SeaBank", "seabank.ph"),
        "tonik" to Pair("Tonik Bank", "tonikbank.com"),
        "cimb" to Pair("CIMB Bank", "cimb.com.ph"),
        "komo" to Pair("Komo by EastWest", "komo.ph"),

        // US & International
        "chase" to Pair("JPMorgan Chase", "chase.com"),
        "jpmorgan" to Pair("JPMorgan Chase", "chase.com"),
        "bank of america" to Pair("Bank of America", "bankofamerica.com"),
        "bofa" to Pair("Bank of America", "bankofamerica.com"),
        "wells fargo" to Pair("Wells Fargo", "wellsfargo.com"),
        "citi" to Pair("Citibank", "citi.com"),
        "citibank" to Pair("Citibank", "citi.com"),
        "capital one" to Pair("Capital One", "capitalone.com"),
        "american express" to Pair("American Express", "americanexpress.com"),
        "amex" to Pair("American Express", "americanexpress.com"),
        "discover" to Pair("Discover", "discover.com"),
        "us bank" to Pair("U.S. Bank", "usbank.com"),
        "pnc" to Pair("PNC Bank", "pnc.com"),
        "truist" to Pair("Truist Bank", "truist.com"),
        "fidelity" to Pair("Fidelity Investments", "fidelity.com"),
        "charles schwab" to Pair("Charles Schwab", "schwab.com"),
        "schwab" to Pair("Charles Schwab", "schwab.com"),
        "vanguard" to Pair("Vanguard", "vanguard.com"),

        // Europe & UK
        "hsbc" to Pair("HSBC", "hsbc.com"),
        "barclays" to Pair("Barclays", "barclays.co.uk"),
        "lloyds" to Pair("Lloyds Bank", "lloydsbank.com"),
        "natwest" to Pair("NatWest", "natwest.com"),
        "santander" to Pair("Santander", "santander.com"),
        "revolut" to Pair("Revolut", "revolut.com"),
        "monzo" to Pair("Monzo", "monzo.com"),
        "wise" to Pair("Wise", "wise.com"),
        "transferwise" to Pair("Wise", "wise.com"),

        // Canada & Australia
        "rbc" to Pair("RBC Royal Bank", "rbcroyalbank.com"),
        "td" to Pair("TD Bank", "td.com"),
        "scotiabank" to Pair("Scotiabank", "scotiabank.com"),
        "bmo" to Pair("BMO Bank of Montreal", "bmo.com"),
        "cibc" to Pair("CIBC", "cibc.com"),
        "dbs" to Pair("DBS Bank", "dbs.com"),
        "ocbc" to Pair("OCBC Bank", "ocbc.com"),
        "uob" to Pair("UOB", "uob.com.sg"),
        "commonwealth" to Pair("Commonwealth Bank", "commbank.com.au"),
        "commbank" to Pair("Commonwealth Bank", "commbank.com.au"),
        "anz" to Pair("ANZ", "anz.com"),
        "westpac" to Pair("Westpac", "westpac.com.au"),
        "nab" to Pair("National Australia Bank", "nab.com.au"),

        // Wallets & Crypto
        "paypal" to Pair("PayPal", "paypal.com"),
        "venmo" to Pair("Venmo", "venmo.com"),
        "cash app" to Pair("Cash App", "cash.app"),
        "apple card" to Pair("Apple Card", "apple.com"),
        "apple cash" to Pair("Apple Cash", "apple.com"),
        "apple" to Pair("Apple Card", "apple.com"),
        "google pay" to Pair("Google Pay", "google.com"),
        "stripe" to Pair("Stripe", "stripe.com"),
        "binance" to Pair("Binance", "binance.com"),
        "coinbase" to Pair("Coinbase", "coinbase.com"),
        "crypto.com" to Pair("Crypto.com", "crypto.com")
    )

    /**
     * Checks if an icon identifier represents a locally stored icon file.
     */
    fun isLocalIcon(iconKey: String?): Boolean {
        if (iconKey.isNullOrBlank()) return false
        return iconKey.startsWith(LOCAL_ICON_PREFIX) ||
                iconKey.startsWith("/") ||
                iconKey.endsWith(".png", ignoreCase = true) ||
                iconKey.endsWith(".jpg", ignoreCase = true)
    }

    /**
     * Resolves the local File from an iconKey.
     */
    fun getLocalIconFile(context: Context, iconKey: String?): File? {
        if (iconKey.isNullOrBlank() || !isLocalIcon(iconKey)) return null
        val cleanPath = iconKey.removePrefix(LOCAL_ICON_PREFIX)
        val fileInDir = if (cleanPath.startsWith("/")) {
            File(cleanPath)
        } else {
            File(context.filesDir, cleanPath)
        }
        return if (fileInDir.exists()) fileInDir else null
    }

    /**
     * Retrieves all saved bank icons on local device.
     */
    fun getSavedLocalIcons(context: Context): List<File> {
        val dir = File(context.filesDir, ICONS_DIR_NAME)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file ->
            file.isFile && (file.name.endsWith(".png") || file.name.endsWith(".jpg"))
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    /**
     * Queries the internet for real bank icons based on account name.
     * Uses multiple sources:
     * 1. Curated official high-resolution domain providers (Clearbit, Google S2 Favicon, DuckDuckGo)
     * 2. Live Wikipedia Search API for official logos
     * 3. Domain guessing heuristic
     *
     * Validates each candidate with an HTTP request to ensure reachable real image bytes.
     */
    suspend fun searchBankRealIcons(accountName: String, context: Context): List<BankIconCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<BankIconCandidate>()
        val query = cleanAccountNameForSearch(accountName)
        if (query.isBlank()) return@withContext emptyList()

        val lowerQuery = query.lowercase().trim()

        // 1. Check curated domain matching
        var matchedDomain: String? = null
        var matchedTitle: String? = null

        // Exact or substring match in curated institutions
        for ((key, pair) in knownInstitutions) {
            if (lowerQuery == key || lowerQuery.contains(key) || key.contains(lowerQuery)) {
                matchedTitle = pair.first
                matchedDomain = pair.second
                break
            }
        }

        if (matchedDomain != null) {
            candidates.add(
                BankIconCandidate(
                    title = matchedTitle ?: query,
                    imageUrl = "https://logo.clearbit.com/$matchedDomain",
                    source = "Clearbit HD Logo",
                    domain = matchedDomain
                )
            )
            candidates.add(
                BankIconCandidate(
                    title = matchedTitle ?: query,
                    imageUrl = "https://www.google.com/s2/favicons?domain=$matchedDomain&sz=128",
                    source = "Google 128px Favicon",
                    domain = matchedDomain
                )
            )
            candidates.add(
                BankIconCandidate(
                    title = matchedTitle ?: query,
                    imageUrl = "https://icons.duckduckgo.com/ip3/$matchedDomain.ico",
                    source = "DuckDuckGo Icon",
                    domain = matchedDomain
                )
            )
        }

        // 2. Query Wikipedia API for real official bank logos
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=${encodedQuery}+bank&gsrlimit=4&prop=pageimages|info&piprop=thumbnail&pithumbsize=256"
            val wikiRequest = Request.Builder()
                .url(wikiUrl)
                .header("User-Agent", "SpendFlowApp/1.0 (Android Personal Finance)")
                .build()

            val wikiResponse = httpClient.newCall(wikiRequest).execute()
            if (wikiResponse.isSuccessful) {
                val jsonString = wikiResponse.body?.string()
                if (!jsonString.isNullOrBlank()) {
                    val root = JSONObject(jsonString)
                    val queryObj = root.optJSONObject("query")
                    val pagesObj = queryObj?.optJSONObject("pages")
                    if (pagesObj != null) {
                        val keys = pagesObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val page = pagesObj.optJSONObject(key)
                            val title = page?.optString("title") ?: query
                            val thumb = page?.optJSONObject("thumbnail")
                            val sourceUrl = thumb?.optString("source")
                            if (!sourceUrl.isNullOrBlank() && (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://"))) {
                                candidates.add(
                                    BankIconCandidate(
                                        title = title,
                                        imageUrl = sourceUrl,
                                        source = "Wikipedia Official Logo"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search error: ${e.message}")
        }

        // 3. Fallback domain guessing if not already found in curated map
        if (matchedDomain == null) {
            val cleanAlphanumeric = lowerQuery.replace(Regex("[^a-z0-9]"), "")
            if (cleanAlphanumeric.isNotBlank()) {
                val guessDomains = listOf("$cleanAlphanumeric.com", "${cleanAlphanumeric}bank.com")
                for (d in guessDomains) {
                    candidates.add(
                        BankIconCandidate(
                            title = query.replaceFirstChar { it.uppercase() },
                            imageUrl = "https://logo.clearbit.com/$d",
                            source = "Clearbit Logo ($d)",
                            domain = d
                        )
                    )
                    candidates.add(
                        BankIconCandidate(
                            title = query.replaceFirstChar { it.uppercase() },
                            imageUrl = "https://www.google.com/s2/favicons?domain=$d&sz=128",
                            source = "Google Favicon ($d)",
                            domain = d
                        )
                    )
                }
            }
        }

        // 4. Verify candidates to ensure they return valid HTTP 200 and image content
        val validResults = mutableListOf<BankIconCandidate>()
        val seenUrls = mutableSetOf<String>()

        for (candidate in candidates) {
            if (seenUrls.contains(candidate.imageUrl)) continue
            seenUrls.add(candidate.imageUrl)

            if (verifyImageUrl(candidate.imageUrl)) {
                validResults.add(candidate)
            }
        }

        validResults
    }

    /**
     * Verifies that the URL responds with HTTP 200 and has image content.
     */
    private fun verifyImageUrl(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val contentType = response.header("Content-Type") ?: ""
            val contentLength = response.body?.contentLength() ?: 0L

            response.close()
            // Accept if response is OK and either declared as image or non-empty
            isSuccess && (contentType.startsWith("image") || contentType.contains("icon") || contentLength > 80 || contentLength == -1L)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Downloads the icon from the internet and saves it locally in the app's internal files.
     * Returns the local storage identifier: "local:bank_icons/<filename>.png"
     */
    suspend fun downloadAndSaveIconLocally(
        context: Context,
        imageUrl: String,
        accountName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download image: HTTP ${response.code}"))
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(Exception("Empty image body"))

            // Ensure destination directory exists
            val iconsDir = File(context.filesDir, ICONS_DIR_NAME)
            if (!iconsDir.exists()) {
                iconsDir.mkdirs()
            }

            // Create clean filename
            val cleanName = accountName.lowercase().replace(Regex("[^a-z0-9]"), "_").take(16)
            val fileName = "bank_${System.currentTimeMillis()}_${cleanName}.png"
            val targetFile = File(iconsDir, fileName)

            // Try decoding as bitmap to save standardized crisp PNG
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                // Scale if unnecessarily large (e.g. > 256x256) to save storage and RAM
                val targetSize = 256
                val scaledBitmap = if (bitmap.width > targetSize || bitmap.height > targetSize) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (w, h) = if (ratio > 1) {
                        targetSize to (targetSize / ratio).toInt()
                    } else {
                        (targetSize * ratio).toInt() to targetSize
                    }
                    Bitmap.createScaledBitmap(bitmap, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
                } else {
                    bitmap
                }

                FileOutputStream(targetFile).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } else {
                // Write raw bytes directly if ICO or special format
                FileOutputStream(targetFile).use { out ->
                    out.write(bytes)
                }
            }

            val storageKey = "$LOCAL_ICON_PREFIX$ICONS_DIR_NAME/$fileName"
            Log.d(TAG, "Successfully saved bank icon to: ${targetFile.absolutePath} (key: $storageKey)")
            Result.success(storageKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bank icon: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Cleans an account name for search by removing generic noise words.
     */
    private fun cleanAccountNameForSearch(name: String): String {
        val noiseWords = listOf(
            "account", "acct", "savings", "saving", "checking", "checkings",
            "credit", "card", "debit", "wallet", "personal", "payroll", "main",
            "fund", "my", "the", "bank"
        )
        val tokens = name.split(Regex("[\\s_\\-\\.]+"))
        val filtered = tokens.filter { token ->
            token.isNotBlank() && !noiseWords.contains(token.lowercase())
        }

        return if (filtered.isNotEmpty()) {
            filtered.joinToString(" ")
        } else {
            // If all tokens were noise words (e.g. "My Bank"), keep the original name
            name.trim()
        }
    }
}
