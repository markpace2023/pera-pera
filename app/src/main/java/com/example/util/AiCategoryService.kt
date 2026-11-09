package com.example.util

import android.content.Context
import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType

enum class SuggestionSource {
    RECENT_TRANSACTION,
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
    data class MultipleMatched(val categories: List<Category>, val message: String) : CategorizationResult()
    data class SuggestedNew(val newCategory: NewCategorySuggestion) : CategorizationResult()
    object None : CategorizationResult()
}

/**
 * Local category auto-matcher (replaces AI auto-assign).
 * Prioritizes recent transaction matches, then category keyword lists.
 */
object AiCategoryService {

    fun suggestCategory(
        context: Context,
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>,
        recentTransactions: List<Transaction> = emptyList()
    ): CategorySuggestion? {
        val matchResult = CategoryMatchingService.matchCategory(
            noteOrTitle = noteOrTitle,
            type = type,
            availableCategories = availableCategories,
            recentTransactions = recentTransactions
        )
        return when (matchResult) {
            is CategoryMatchResult.SingleMatch -> {
                val source = if (matchResult.reason.contains("previous transaction")) {
                    SuggestionSource.RECENT_TRANSACTION
                } else {
                    SuggestionSource.LOCAL_KEYWORD
                }
                CategorySuggestion(
                    category = matchResult.category,
                    source = source,
                    message = matchResult.reason
                )
            }
            is CategoryMatchResult.MultipleMatches -> {
                // If multiple match, return the first one as default suggestion
                val first = matchResult.matchedCategories.firstOrNull() ?: return null
                CategorySuggestion(
                    category = first,
                    source = SuggestionSource.LOCAL_KEYWORD,
                    message = "Matched ${first.name}"
                )
            }
            is CategoryMatchResult.NoMatch -> null
        }
    }

    fun categorizeOrSuggestNew(
        context: Context,
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>,
        recentTransactions: List<Transaction> = emptyList()
    ): CategorizationResult {
        val query = noteOrTitle.trim()
        if (query.isBlank()) return CategorizationResult.None

        val matchResult = CategoryMatchingService.matchCategory(
            noteOrTitle = query,
            type = type,
            availableCategories = availableCategories,
            recentTransactions = recentTransactions
        )

        return when (matchResult) {
            is CategoryMatchResult.SingleMatch -> {
                val source = if (matchResult.reason.contains("previous transaction")) {
                    SuggestionSource.RECENT_TRANSACTION
                } else {
                    SuggestionSource.LOCAL_KEYWORD
                }
                CategorizationResult.MatchedExisting(
                    CategorySuggestion(
                        category = matchResult.category,
                        source = source,
                        message = matchResult.reason
                    )
                )
            }
            is CategoryMatchResult.MultipleMatches -> {
                CategorizationResult.MultipleMatched(
                    categories = matchResult.matchedCategories,
                    message = matchResult.reason
                )
            }
            is CategoryMatchResult.NoMatch -> {
                val offlineNew = suggestNewCategoryOffline(query, type, availableCategories)
                if (offlineNew != null) {
                    CategorizationResult.SuggestedNew(offlineNew)
                } else {
                    CategorizationResult.None
                }
            }
        }
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
                iconName = "tools",
                colorHex = "#F59E0B",
                keywords = listOf("hardware", "plumber", "electrician", "renovation", "furniture", "ikea", "paint", "aircon cleaning", "homeware", "curtains"),
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
                iconName = "family",
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

    fun predictOffline(
        noteOrTitle: String,
        type: TransactionType,
        availableCategories: List<Category>,
        recentTransactions: List<Transaction> = emptyList()
    ): Category? {
        val result = CategoryMatchingService.matchCategory(noteOrTitle, type, availableCategories, recentTransactions)
        return when (result) {
            is CategoryMatchResult.SingleMatch -> result.category
            is CategoryMatchResult.MultipleMatches -> result.matchedCategories.firstOrNull()
            is CategoryMatchResult.NoMatch -> null
        }
    }
}
