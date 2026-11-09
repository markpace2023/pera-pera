package com.example.util

import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails

sealed class CategoryMatchResult {
    data class SingleMatch(
        val category: Category,
        val reason: String
    ) : CategoryMatchResult()

    data class MultipleMatches(
        val matchedCategories: List<Category>,
        val reason: String
    ) : CategoryMatchResult()

    object NoMatch : CategoryMatchResult()
}

object CategoryMatchingService {

    /**
     * Matches a transaction's note or title against:
     * 1. Recent transactions (exact note/title match from previous records)
     * 2. Category keyword dictionary & custom category words
     *
     * Returns:
     * - SingleMatch: Exactly one category matched (auto-selected).
     * - MultipleMatches: Two or more categories matched (user prompted to choose).
     * - NoMatch: No matching category found.
     */
    fun matchCategory(
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>,
        recentTransactions: List<Transaction> = emptyList()
    ): CategoryMatchResult {
        val query = noteOrTitle.trim()
        if (query.isBlank() || availableCategories.isEmpty()) {
            return CategoryMatchResult.NoMatch
        }

        val typeCategories = availableCategories.filter { it.type == type }
        val categoryPool = if (typeCategories.isNotEmpty()) typeCategories else availableCategories

        // 1. Check if the query is an exact match for a category name
        val exactCategory = categoryPool.firstOrNull { it.name.trim().equals(query, ignoreCase = true) }
        if (exactCategory != null) {
            return CategoryMatchResult.SingleMatch(exactCategory, "Exact category name match")
        }

        // 2. Search recent transactions for a previous transaction with the same title or note
        val pastCategory = searchRecentTransactionCategory(query, type, recentTransactions, categoryPool)
        if (pastCategory != null) {
            return CategoryMatchResult.SingleMatch(pastCategory, "Matched from previous transaction \"$query\"")
        }

        // 3. Search category keyword dictionary (including dynamically learned keywords from past transactions)
        val matchedCategories = matchCategoriesByKeywords(query, type, categoryPool, recentTransactions)

        return when {
            matchedCategories.size == 1 -> {
                CategoryMatchResult.SingleMatch(
                    matchedCategories.first(),
                    "Matched keyword for ${matchedCategories.first().name}"
                )
            }
            matchedCategories.size > 1 -> {
                CategoryMatchResult.MultipleMatches(
                    matchedCategories,
                    "Multiple categories match \"$query\""
                )
            }
            else -> CategoryMatchResult.NoMatch
        }
    }

    /**
     * Overload supporting TransactionWithDetails list.
     */
    fun matchCategoryWithDetails(
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>,
        recentTransactions: List<TransactionWithDetails>
    ): CategoryMatchResult {
        return matchCategory(
            noteOrTitle = noteOrTitle,
            type = type,
            availableCategories = availableCategories,
            recentTransactions = recentTransactions.map { it.transaction }
        )
    }

    /**
     * Searches past transactions in reverse-chronological order for the same note or title.
     * If found and its category is valid, returns that Category.
     */
    fun searchRecentTransactionCategory(
        query: String,
        type: TransactionType,
        recentTransactions: List<Transaction>,
        availableCategories: List<Category>
    ): Category? {
        val q = query.trim()
        if (q.isBlank() || recentTransactions.isEmpty()) return null
        val categoryMap = availableCategories.associateBy { it.id }

        val sortedRecent = recentTransactions.sortedByDescending { it.timestamp }

        // Exact match on title or note
        val exactMatch = sortedRecent.firstOrNull { tx ->
            tx.type == type &&
            tx.categoryId != null &&
            categoryMap.containsKey(tx.categoryId) &&
            (tx.title.trim().equals(q, ignoreCase = true) || tx.note.trim().equals(q, ignoreCase = true))
        }
        if (exactMatch != null && exactMatch.categoryId != null) {
            return categoryMap[exactMatch.categoryId]
        }

        // Word/prefix match if query has 3+ characters
        if (q.length >= 3) {
            val partialMatch = sortedRecent.firstOrNull { tx ->
                tx.type == type &&
                tx.categoryId != null &&
                categoryMap.containsKey(tx.categoryId) &&
                (tx.title.trim().contains(q, ignoreCase = true) ||
                 q.contains(tx.title.trim(), ignoreCase = true) ||
                 (tx.note.isNotBlank() && (tx.note.trim().contains(q, ignoreCase = true) || q.contains(tx.note.trim(), ignoreCase = true))))
            }
            if (partialMatch != null && partialMatch.categoryId != null) {
                return categoryMap[partialMatch.categoryId]
            }
        }

        return null
    }

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "from", "into", "this", "that", "some",
        "paid", "bought", "payment", "spend", "expense", "income", "transaction",
        "ang", "ng", "mga", "sa", "para", "kay", "kina", "at", "na", "pa",
        "may", "meron", "ko", "mo", "niya", "namin", "nila"
    )

    /**
     * Extracts dynamic learned keywords for a category from historical transactions.
     * As the user logs more transactions, this list expands automatically.
     */
    fun getLearnedKeywordsForCategory(
        categoryId: Long,
        recentTransactions: List<Transaction>
    ): List<String> {
        if (recentTransactions.isEmpty()) return emptyList()

        val learned = mutableSetOf<String>()
        val categoryTxs = recentTransactions.filter { it.categoryId == categoryId }

        for (tx in categoryTxs) {
            // Learn full trimmed title/phrase if meaningful (e.g. "7-Eleven", "Aling Nena")
            val cleanTitle = tx.title.trim().lowercase()
            if (cleanTitle.length in 3..40 && !STOP_WORDS.contains(cleanTitle)) {
                learned.add(cleanTitle)
            }

            // Learn individual words from title and note
            val combinedText = "${tx.title} ${tx.note}".lowercase()
            val words = combinedText.split(Regex("[\\s,;:.!?-]+"))
                .filter { it.length >= 3 && !STOP_WORDS.contains(it) && !it.all { ch -> ch.isDigit() } }

            learned.addAll(words)
        }

        return learned.toList()
    }

    /**
     * Matches the given text against category keyword lists, including dynamically learned keywords from past transactions.
     * Returns all categories that matched.
     */
    fun matchCategoriesByKeywords(
        query: String,
        type: TransactionType,
        categories: List<Category>,
        recentTransactions: List<Transaction> = emptyList()
    ): List<Category> {
        val q = query.lowercase().trim()
        if (q.isBlank() || categories.isEmpty()) return emptyList()

        val tokens = q.split(Regex("[\\s,;:.!?-]+")).filter { it.length >= 2 }
        val matched = mutableListOf<Category>()

        for (category in categories) {
            val baseKeywords = getKeywordListForCategory(category.name, category.iconName)
            val learnedKeywords = getLearnedKeywordsForCategory(category.id, recentTransactions)
            val allKeywords = (baseKeywords + learnedKeywords).distinct()

            // Check if any keyword matches
            val hasKeywordMatch = allKeywords.any { kw ->
                val kwLower = kw.lowercase().trim()
                if (kwLower.contains(" ")) {
                    q.contains(kwLower)
                } else {
                    tokens.contains(kwLower) || q.contains(kwLower)
                }
            }

            // Also check words from the category's own name
            val nameWords = category.name.lowercase().split(Regex("[\\s&/]+")).filter { it.length >= 3 }
            val hasNameMatch = nameWords.any { word ->
                tokens.contains(word) || q.contains(word)
            }

            if (hasKeywordMatch || hasNameMatch) {
                matched.add(category)
            }
        }

        return matched.distinctBy { it.id }
    }

    /**
     * Returns a rich, curated list of keywords for a given category name and icon.
     */
    fun getKeywordListForCategory(categoryName: String, iconName: String = ""): List<String> {
        val nameLower = categoryName.lowercase().trim()
        val customKeywords = mutableListOf<String>()

        // Add words from category name
        customKeywords.addAll(nameLower.split(Regex("[\\s&/]+")).filter { it.length >= 2 })

        // Predefined keyword map covering default and common custom categories
        val baseKeywords = when {
            nameLower.contains("food") || nameLower.contains("dining") -> listOf(
                "food", "dining", "lunch", "dinner", "breakfast", "brunch", "meal", "coffee", "cafe",
                "starbucks", "jollibee", "mcdo", "mcdonalds", "kfc", "chowking", "mang inasal", "restaurant",
                "burger", "pizza", "snack", "merienda", "milk tea", "boba", "grabfood", "foodpanda",
                "dine", "buffet", "bakery", "bread", "pastry", "drinks", "bar", "cocktail", "beer", "wine",
                "tea", "ramen", "sushi", "takeout", "eat", "eating"
            )

            nameLower.contains("grocer") -> listOf(
                "grocery", "groceries", "supermarket", "puregold", "sm supermarket", "waltermart", "robinsons",
                "savemore", "meat", "vegetables", "veggies", "fruit", "fruits", "rice", "pantry", "market",
                "palengke", "wet market", "canned", "milk", "eggs", "condiments", "spices", "cooking oil",
                "detergent", "soap", "toothpaste", "toiletries", "tissue", "household supplies"
            )

            nameLower.contains("transport") -> listOf(
                "transport", "transportation", "gas", "gasoline", "fuel", "diesel", "petron", "shell", "caltex",
                "seaoil", "cleanfuel", "fare", "jeep", "jeepney", "bus", "train", "mrt", "lrt", "pnr", "grab",
                "angkas", "joyride", "move it", "taxi", "cab", "toll", "rfid", "easytrip", "autosweep", "parking",
                "car", "commute", "flight ticket", "subway", "uber"
            )

            nameLower.contains("shop") -> listOf(
                "shopping", "shop", "shopee", "lazada", "amazon", "tiktok shop", "clothes", "clothing", "apparel",
                "shoes", "sneakers", "uniqlo", "zara", "h&m", "shein", "bought", "mall", "shirt", "pants", "dress",
                "bag", "wallet", "accessory", "jewelry", "watch", "perfume", "cosmetics", "skincare", "online purchase"
            )

            nameLower.contains("bill") || nameLower.contains("utilit") -> listOf(
                "meralco", "electric", "electricity", "power", "water", "maynilad", "manila water", "pldt",
                "converge", "globe", "smart", "dito", "wifi", "internet", "bill", "bills", "utilities", "utility",
                "dues", "association dues", "hoa", "cable"
            )

            nameLower.contains("housing") || nameLower.contains("rent") -> listOf(
                "rent", "rental", "condo", "apartment", "house", "mortgage", "landlord", "lease",
                "boarding house", "dorm", "housing", "property tax", "amortization"
            )

            nameLower.contains("entertain") -> listOf(
                "entertainment", "movie", "cinema", "netflix", "spotify", "apple music", "disney", "hbo",
                "steam", "game", "games", "gaming", "playstation", "nintendo", "xbox", "concert", "party",
                "arcade", "karaoke", "ktv", "bowling", "billiards", "cinema ticket"
            )

            nameLower.contains("health") || nameLower.contains("medic") -> listOf(
                "health", "medical", "hospital", "clinic", "doctor", "dentist", "dental", "pharmacy",
                "mercury drug", "watsons", "southstar", "medicine", "meds", "vitamins", "supplement",
                "checkup", "consultation", "lab test", "xray", "optical", "glasses", "prescription"
            )

            nameLower.contains("subscri") -> listOf(
                "subscription", "subscriptions", "sub", "recurring", "membership", "youtube premium",
                "icloud", "google one", "microsoft 365", "chatgpt", "canva", "adobe", "gym membership", "patreon"
            )

            nameLower.contains("travel") -> listOf(
                "travel", "flight", "plane", "airline", "cebu pacific", "cebpac", "philippine airlines",
                "pal", "airasia", "hotel", "hostel", "airbnb", "agoda", "booking", "klook", "vacation",
                "trip", "tour", "resort", "beach", "passport", "visa", "luggage", "getaway"
            )

            nameLower.contains("educat") -> listOf(
                "education", "tuition", "school", "college", "university", "books", "textbook", "supplies",
                "course", "training", "udemy", "coursera", "bootcamp", "exam", "lesson", "allowance", "baon", "uniform", "enrollment"
            )

            nameLower.contains("family") -> listOf(
                "family", "parents", "mom", "dad", "mother", "father", "kids", "children", "son", "daughter",
                "sister", "brother", "baby", "diaper", "diapers", "infant", "toddler", "formula milk",
                "pediatrician", "stroller", "toys", "relatives", "padala", "support"
            )

            nameLower.contains("grant") -> listOf(
                "grant", "grants", "scholarship", "subsidy", "financial aid", "stipend", "endowment", "funding",
                "allowance", "bursary", "government grant", "research grant"
            )

            nameLower.contains("home") -> listOf(
                "home", "household", "furniture", "appliance", "ikea", "curtains", "bedding", "decor",
                "kitchenware", "homeware", "repair", "home improvement", "interior"
            )

            nameLower.contains("motorcycle") || nameLower.contains("motor") -> listOf(
                "motorcycle", "motor", "motorbike", "scooter", "vespa", "aerox", "nmax", "click", "parix",
                "helmet", "motul", "change oil", "motor parts", "tune up", "two wheeler"
            )

            nameLower.contains("telephon") || nameLower.contains("phone") -> listOf(
                "telephone", "phone", "landline", "mobile", "cellphone", "prepaid", "postpaid", "load",
                "call", "sms", "roaming", "telecom", "sim"
            )

            nameLower.contains("tool") -> listOf(
                "tools", "hardware", "tool", "hammer", "drill", "screwdriver", "wrench", "pliers", "saw",
                "nails", "screws", "toolbox", "ace hardware", "handyman", "wilcon", "plumbing", "electrical"
            )

            nameLower.contains("salary") -> listOf(
                "salary", "sweldo", "sahod", "payroll", "paycheck", "wages", "13th month", "14th month",
                "bonus", "midyear bonus", "performance bonus"
            )

            nameLower.contains("freelance") -> listOf(
                "freelance", "upwork", "fiverr", "online jobs", "ojph", "client", "project", "gig",
                "contract", "consulting", "side hustle"
            )

            nameLower.contains("invest") -> listOf(
                "investment", "investments", "dividend", "dividends", "interest", "crypto", "bitcoin",
                "ethereum", "stocks", "pse", "shares", "yield", "capital gain", "mutual fund", "uitf", "bonds"
            )

            nameLower.contains("business") -> listOf(
                "business", "revenue", "sales", "client payment", "customer", "invoice", "store",
                "shop sales", "profit", "order payout", "e-commerce"
            )

            nameLower.contains("gift") || nameLower.contains("bonus") -> listOf(
                "gift", "gifts", "pamasko", "angpao", "birthday", "prize", "raffle", "reward",
                "cash gift", "donation received", "monetary gift"
            )

            nameLower.contains("borrow") -> listOf(
                "borrow", "borrowed", "utang", "loan", "lender", "hiram", "inutang", "received loan", "loan proceeds"
            )

            nameLower.contains("collection") || nameLower.contains("singil") -> listOf(
                "singil", "collect", "collected", "bayad sa utang", "repayment received", "loan repayment",
                "debt payment received", "loan collection"
            )

            nameLower.contains("lent") || nameLower.contains("pautang") -> listOf(
                "lend", "lent", "pautang", "loan out", "receivable", "nagpautang", "money lent"
            )

            nameLower.contains("debt payment") || nameLower.contains("loan payment") -> listOf(
                "pay debt", "payment for loan", "loan payment", "debt payment", "bayad utang", "repayment", "pay borrow", "paid debt", "repay"
            )

            nameLower.contains("pet") -> listOf(
                "pet", "pets", "dog", "cat", "puppy", "kitten", "vet", "veterinary", "kibble", "grooming", "dog food", "cat food", "aquarium"
            )

            nameLower.contains("fitness") || nameLower.contains("gym") -> listOf(
                "gym", "fitness", "workout", "crossfit", "yoga", "pilates", "whey", "protein", "trainer"
            )

            nameLower.contains("other") -> listOf(
                "other", "misc", "miscellaneous", "etc", "extra", "unforeseen", "petty cash", "general"
            )

            else -> emptyList()
        }

        // Add icon-specific keywords if present
        val iconKeywords = when (iconName) {
            "motorcycle", "two_wheeler" -> listOf("motorcycle", "motor", "scooter", "helmet")
            "telephone", "phone" -> listOf("phone", "cellphone", "telephone", "load", "mobile")
            "tools", "build" -> listOf("tools", "tool", "hardware", "repair")
            "family", "family_restroom" -> listOf("family", "baby", "kids", "diaper", "parents")
            "grants", "volunteer_activism" -> listOf("grant", "scholarship", "aid", "stipend")
            "home" -> listOf("home", "house", "curtains", "furniture")
            "pets" -> listOf("pet", "dog", "cat", "vet")
            "fitness_center" -> listOf("gym", "fitness", "workout")
            else -> emptyList()
        }

        return (customKeywords + baseKeywords + iconKeywords).distinct()
    }
}
