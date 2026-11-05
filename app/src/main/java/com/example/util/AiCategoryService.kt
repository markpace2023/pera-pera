package com.example.util

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Category
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SuggestionSource {
    AI_ONLINE,
    LOCAL_KEYWORD
}

data class CategorySuggestion(
    val category: Category,
    val source: SuggestionSource,
    val message: String
)

data class NewCategorySuggestion(
    val name: String,
    val iconName: String,
    val colorHex: String,
    val source: SuggestionSource,
    val reason: String
)

sealed class CategorizationResult {
    data class MatchedExisting(val suggestion: CategorySuggestion) : CategorizationResult()
    data class SuggestedNew(val newCategory: NewCategorySuggestion) : CategorizationResult()
    object None : CategorizationResult()
}

object AiCategoryService {

    private const val TAG = "AiCategoryService"
    // Recommended fast & free-tier model
    private const val GEMINI_MODEL = "gemini-2.5-flash"
    private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun suggestCategory(
        context: Context,
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>
    ): CategorySuggestion? = withContext(Dispatchers.IO) {
        val query = noteOrTitle.trim()
        if (query.isBlank() || availableCategories.isEmpty()) return@withContext null

        val filteredCategories = availableCategories.filter { it.type == type }
        val categoryPool = if (filteredCategories.isNotEmpty()) filteredCategories else availableCategories

        // 1. Check internet availability
        val isOnline = NetworkUtils.isInternetAvailable(context)
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()

        if (isOnline && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val categoryNames = categoryPool.map { it.name }.joinToString(", ")
                val promptText = "Classify this personal finance transaction into exactly one category from this list: [$categoryNames]. " +
                        "Transaction Type: ${type.name}. " +
                        "Description/Note: \"$query\". " +
                        "Respond with ONLY the exact matched category name from the list. Do not add quotes, markdown, or explanation."

                val jsonBody = JSONObject().apply {
                    val contentsArr = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArr = JSONArray().apply {
                                put(JSONObject().apply { put("text", promptText) })
                            }
                            put("parts", partsArr)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArr)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url("$GEMINI_URL?key=$apiKey")
                    .post(jsonBody.toString().toRequestBody(mediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string().orEmpty()
                        val rootJson = JSONObject(bodyString)
                        val candidates = rootJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val aiResponseText = parts?.optJSONObject(0)?.optString("text")?.trim().orEmpty()

                        // Match AI response with available categories
                        val matchedCategory = categoryPool.find {
                            it.name.equals(aiResponseText, ignoreCase = true) ||
                                    aiResponseText.contains(it.name, ignoreCase = true)
                        }

                        if (matchedCategory != null) {
                            return@withContext CategorySuggestion(
                                category = matchedCategory,
                                source = SuggestionSource.AI_ONLINE,
                                message = "AI categorized as ${matchedCategory.name}"
                            )
                        }
                    } else {
                        Log.w(TAG, "Gemini API error code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API call failed: ${e.message}")
            }
        }

        // 2. Intelligent Offline Keyword Fallback
        val keywordMatched = matchCategoryByKeyword(query, categoryPool)
        if (keywordMatched != null) {
            val statusMessage = if (!isOnline) {
                "Offline: Matched to ${keywordMatched.name}"
            } else {
                "Auto-matched to ${keywordMatched.name}"
            }
            return@withContext CategorySuggestion(
                category = keywordMatched,
                source = SuggestionSource.LOCAL_KEYWORD,
                message = statusMessage
            )
        }

        null
    }

    suspend fun categorizeOrSuggestNew(
        context: Context,
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>
    ): CategorizationResult = withContext(Dispatchers.IO) {
        val query = noteOrTitle.trim()
        if (query.isBlank()) return@withContext CategorizationResult.None

        // 1. Try to match an existing category first
        val existingMatch = suggestCategory(context, query, type, availableCategories)
        if (existingMatch != null) {
            return@withContext CategorizationResult.MatchedExisting(existingMatch)
        }

        // 2. No appropriate existing category found.
        // Ask AI to suggest a new applicable category name!
        val isOnline = NetworkUtils.isInternetAvailable(context)
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()

        if (isOnline && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val existingNames = availableCategories.map { it.name }.joinToString(", ")
                val promptText = "The user is recording a personal finance transaction: " +
                        "Transaction Type: ${type.name}. " +
                        "Description/Note: \"$query\". " +
                        "Existing categories: [$existingNames]. " +
                        "None of the existing categories match this transaction. " +
                        "Suggest a concise, professional category name (1 to 3 words, e.g. 'Pet Care', 'Fitness', 'Personal Care', 'Charity', 'Home Maintenance', 'Tech & Gadgets', 'Childcare'), " +
                        "a matching icon key from this list: [pets, fitness_center, laptop, card_giftcard, medical_services, school, flight, movie, restaurant, shopping_bag, shopping_cart, directions_car, local_gas_station, receipt_long, home, work, storefront, trending_up, more_horiz], " +
                        "and a hex color (e.g. #10B981, #3B82F6, #8B5CF6, #F59E0B, #EF4444, #EC4899, #06B6D4). " +
                        "Respond with ONLY a JSON object in this format: " +
                        "{\"categoryName\": \"...\", \"iconName\": \"...\", \"colorHex\": \"...\", \"reason\": \"...\"}"

                val jsonBody = JSONObject().apply {
                    val contentsArr = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArr = JSONArray().apply {
                                put(JSONObject().apply { put("text", promptText) })
                            }
                            put("parts", partsArr)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArr)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url("$GEMINI_URL?key=$apiKey")
                    .post(jsonBody.toString().toRequestBody(mediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string().orEmpty()
                        val rootJson = JSONObject(bodyString)
                        val candidates = rootJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val aiResponseText = parts?.optJSONObject(0)?.optString("text")?.trim().orEmpty()

                        val cleanJson = aiResponseText
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val parsedJson = JSONObject(cleanJson)
                        val catName = parsedJson.optString("categoryName").trim()
                        val iconName = parsedJson.optString("iconName", "more_horiz").trim()
                        val colorHex = parsedJson.optString("colorHex", "#10B981").trim()
                        val reason = parsedJson.optString("reason", "AI suggested for \"$query\"").trim()

                        if (catName.isNotBlank() && !availableCategories.any { it.name.equals(catName, ignoreCase = true) }) {
                            return@withContext CategorizationResult.SuggestedNew(
                                NewCategorySuggestion(
                                    name = catName,
                                    iconName = validateIconName(iconName),
                                    colorHex = if (colorHex.startsWith("#")) colorHex else "#10B981",
                                    source = SuggestionSource.AI_ONLINE,
                                    reason = reason
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini new category suggestion failed: ${e.message}")
            }
        }

        // 3. Intelligent Offline Suggested New Category Fallback
        val offlineNew = suggestNewCategoryOffline(query, type, availableCategories)
        if (offlineNew != null) {
            return@withContext CategorizationResult.SuggestedNew(offlineNew)
        }

        // 4. No appropriate category found
        CategorizationResult.None
    }

    fun suggestNewCategoryOffline(
        query: String,
        type: TransactionType,
        existingCategories: List<Category>
    ): NewCategorySuggestion? {
        val q = query.lowercase().trim()
        if (q.isBlank()) return null

        data class NewCatRule(
            val name: String,
            val iconName: String,
            val colorHex: String,
            val keywords: List<String>,
            val type: TransactionType,
            val reason: String
        )

        val rules = listOf(
            NewCatRule(
                name = "Pet Care",
                iconName = "pets",
                colorHex = "#10B981",
                keywords = listOf("pet", "pets", "dog", "cat", "puppy", "kitten", "vet", "veterinary", "kibble", "grooming", "dog food", "cat food", "aquarium", "hamster"),
                type = TransactionType.EXPENSE,
                reason = "Identified pet and animal care expenses"
            ),
            NewCatRule(
                name = "Fitness & Gym",
                iconName = "fitness_center",
                colorHex = "#3B82F6",
                keywords = listOf("gym", "fitness", "workout", "crossfit", "yoga", "pilates", "whey", "protein", "trainer"),
                type = TransactionType.EXPENSE,
                reason = "Identified fitness and exercise activity"
            ),
            NewCatRule(
                name = "Personal Care",
                iconName = "shopping_bag",
                colorHex = "#EC4899",
                keywords = listOf("haircut", "salon", "barber", "spa", "massage", "facial", "shampoo", "skincare", "cosmetics", "makeup", "perfume", "lotion", "dermatologist", "nail", "manicure"),
                type = TransactionType.EXPENSE,
                reason = "Identified personal grooming and self-care"
            ),
            NewCatRule(
                name = "Charity & Donations",
                iconName = "card_giftcard",
                colorHex = "#8B5CF6",
                keywords = listOf("donation", "charity", "tithe", "church", "offering", "fundraiser", "alms", "relief"),
                type = TransactionType.EXPENSE,
                reason = "Identified charitable giving or donation"
            ),
            NewCatRule(
                name = "Home & Maintenance",
                iconName = "home",
                colorHex = "#F59E0B",
                keywords = listOf("hardware", "tools", "repair", "plumber", "electrician", "renovation", "furniture", "ikea", "paint", "aircon cleaning", "handyman", "locksmith"),
                type = TransactionType.EXPENSE,
                reason = "Identified home upkeep and maintenance"
            ),
            NewCatRule(
                name = "Tech & Gadgets",
                iconName = "laptop",
                colorHex = "#06B6D4",
                keywords = listOf("gadget", "laptop", "iphone", "samsung", "ipad", "mouse", "keyboard", "monitor", "charger", "cable", "software", "domain", "hosting", "hardware", "computer"),
                type = TransactionType.EXPENSE,
                reason = "Identified electronics and technology purchase"
            ),
            NewCatRule(
                name = "Vehicle & Auto",
                iconName = "directions_car",
                colorHex = "#64748B",
                keywords = listOf("car wash", "oil change", "mechanic", "tire", "battery", "registration", "lto", "auto repair"),
                type = TransactionType.EXPENSE,
                reason = "Identified vehicle servicing and upkeep"
            ),
            NewCatRule(
                name = "Hobbies & Gaming",
                iconName = "movie",
                colorHex = "#F43F5E",
                keywords = listOf("steam", "game", "gaming", "playstation", "nintendo", "xbox", "hobby", "boardgame", "collectibles", "toy", "craft", "guitar", "piano"),
                type = TransactionType.EXPENSE,
                reason = "Identified hobby or gaming purchase"
            ),
            NewCatRule(
                name = "Family & Childcare",
                iconName = "shopping_bag",
                colorHex = "#10B981",
                keywords = listOf("baby", "diaper", "diapers", "infant", "toddler", "formula milk", "daycare", "kindergarten", "stroller", "pediatrician"),
                type = TransactionType.EXPENSE,
                reason = "Identified childcare and family needs"
            ),
            NewCatRule(
                name = "Side Hustle & Freelance",
                iconName = "work",
                colorHex = "#10B981",
                keywords = listOf("freelance", "upwork", "fiverr", "side hustle", "consulting", "gig", "commission", "contract work"),
                type = TransactionType.INCOME,
                reason = "Identified freelance or side gig income"
            ),
            NewCatRule(
                name = "Rental Income",
                iconName = "home",
                colorHex = "#3B82F6",
                keywords = listOf("tenant", "rent payment", "rental", "lease payment", "room rent", "airbnb payout"),
                type = TransactionType.INCOME,
                reason = "Identified rental or property income"
            ),
            NewCatRule(
                name = "Cashback & Rewards",
                iconName = "card_giftcard",
                colorHex = "#F59E0B",
                keywords = listOf("cashback", "rebate", "reward points", "referral bonus", "claim reward"),
                type = TransactionType.INCOME,
                reason = "Identified cashback or promotional rewards"
            )
        )

        for (rule in rules) {
            if (rule.type == type && rule.keywords.any { q.contains(it) }) {
                if (!existingCategories.any { it.name.equals(rule.name, ignoreCase = true) }) {
                    return NewCategorySuggestion(
                        name = rule.name,
                        iconName = rule.iconName,
                        colorHex = rule.colorHex,
                        source = SuggestionSource.LOCAL_KEYWORD,
                        reason = rule.reason
                    )
                }
            }
        }

        return null
    }

    private fun validateIconName(iconName: String): String {
        val validIcons = setOf(
            "local_atm", "credit_card", "account_balance", "account_balance_wallet",
            "trending_up", "payments", "attach_money", "restaurant", "shopping_cart",
            "shopping_bag", "directions_car", "local_gas_station", "receipt_long",
            "home", "movie", "medical_services", "subscriptions", "flight",
            "school", "work", "laptop", "storefront", "card_giftcard",
            "fitness_center", "pets", "handshake", "more_horiz"
        )
        return if (validIcons.contains(iconName)) iconName else "more_horiz"
    }

    fun predictOffline(
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>
    ): Category? {
        val query = noteOrTitle.trim()
        if (query.isBlank() || availableCategories.isEmpty()) return null
        val filtered = availableCategories.filter { it.type == type }
        val pool = if (filtered.isNotEmpty()) filtered else availableCategories
        return matchCategoryByKeyword(query, pool)
    }

    internal fun matchCategoryByKeyword(text: String, categories: List<Category>): Category? {
        val q = text.lowercase()

        val keywordMap = mapOf(
            "Food & Dining" to listOf("lunch", "dinner", "breakfast", "meal", "coffee", "cafe", "starbucks", "jollibee", "mcdo", "mcdonalds", "restaurant", "burger", "pizza", "food", "eat", "snack", "milk tea", "boba", "grabfood", "foodpanda"),
            "Groceries" to listOf("grocery", "groceries", "supermarket", "puregold", "sm supermarket", "waltermart", "robinsons", "meat", "vegetables", "fruit", "rice", "pantry", "market", "palengke"),
            "Transportation" to listOf("gas", "fuel", "petron", "shell", "caltex", "fare", "jeep", "jeepney", "bus", "train", "mrt", "lrt", "grab", "angkas", "joyride", "taxi", "toll", "rfid", "parking", "car", "commute"),
            "Shopping" to listOf("shopping", "shopee", "lazada", "amazon", "clothes", "shoes", "uniqlo", "zara", "bought", "mall", "gadget", "shirt"),
            "Bills & Utilities" to listOf("meralco", "electric", "electricity", "water", "maynilad", "manila water", "pldt", "converge", "globe", "smart", "wifi", "internet", "bill", "utilities", "dues"),
            "Housing & Rent" to listOf("rent", "condo", "apartment", "house", "mortgage", "landlord", "lease"),
            "Entertainment" to listOf("movie", "cinema", "netflix", "spotify", "steam", "game", "gaming", "concert", "party", "arcade"),
            "Health & Medical" to listOf("hospital", "clinic", "doctor", "dentist", "pharmacy", "mercury drug", "watsons", "medicine", "vitamins", "checkup", "dental"),
            "Subscriptions" to listOf("subscription", "sub", "youtube premium", "icloud", "google one", "disney", "prime"),
            "Travel" to listOf("flight", "cebu pacific", "cebpac", "philippine airlines", "hotel", "airbnb", "vacation", "trip", "tour", "resort"),
            "Education" to listOf("tuition", "school", "college", "books", "course", "training", "exam", "lesson"),
            "Salary" to listOf("salary", "sweldo", "payroll", "paycheck", "wages", "13th month", "bonus"),
            "Freelance" to listOf("freelance", "upwork", "fiverr", "client", "project", "gig", "contract"),
            "Investments" to listOf("dividend", "interest", "crypto", "bitcoin", "stocks", "pse", "shares", "yield"),
            "Business" to listOf("revenue", "sales", "client payment", "customer", "invoice", "store"),
            "Gifts & Bonus" to listOf("gift", "pamasko", "angpao", "birthday", "prize", "reward", "cash gift"),
            "Loan / Borrowed Money" to listOf("borrow", "borrowed", "utang", "loan", "lender", "hiram"),
            "Loan / Money Lent" to listOf("lend", "lent", "pautang", "loan out", "receivable", "nagpautang"),
            "Debt / Loan Payment" to listOf("pay debt", "payment for loan", "loan payment", "debt payment", "bayad utang", "repayment", "pay borrow", "paid debt", "repay"),
            "Loan Collection" to listOf("loan collection", "collect loan", "singil", "bayad sa utang", "collected", "repayment from")
        )

        for ((categoryName, keywords) in keywordMap) {
            val matchingCategory = categories.find { it.name.equals(categoryName, ignoreCase = true) }
            if (matchingCategory != null) {
                if (keywords.any { q.contains(it) }) {
                    return matchingCategory
                }
            }
        }

        // Direct containment fallback
        return categories.find { q.contains(it.name.lowercase()) }
    }
}
