package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.data.model.TransactionWithDetails
import com.example.util.AiCategoryService
import com.example.util.CategoryMatchingService
import com.example.util.CategoryMatchResult
import com.example.util.UserPreferences
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuickTransactionWidget(
    accounts: List<Account>,
    categories: List<Category>,
    recentTransactions: List<TransactionWithDetails> = emptyList(),
    onSaveQuickTransaction: (Transaction) -> Unit,
    onOpenFullSheet: (
        selectedAccountId: Long?,
        type: TransactionType,
        amount: String,
        title: String,
        categoryId: Long?
    ) -> Unit,
    isPrivacyMode: Boolean = false,
    onSaveCategory: (suspend (Category) -> Long)? = null,
    modifier: Modifier = Modifier
) {
    if (accounts.isEmpty()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var recentAccountIds by remember { mutableStateOf(UserPreferences.getRecentAccountIds(context)) }
    val sortedAccounts = remember(accounts, recentAccountIds) {
        UserPreferences.sortAccountsByRecency(accounts, recentAccountIds)
    }

    var selectedAccountId by remember(accounts) {
        mutableStateOf(recentAccountIds.firstOrNull { id -> accounts.any { it.id == id } } ?: sortedAccounts.firstOrNull()?.id ?: 0L)
    }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountValue by remember { mutableStateOf(TextFieldValue("")) }
    var titleText by remember { mutableStateOf("") }

    // User requirement: No auto-selected category!
    var selectedCategoryId by remember(selectedType) {
        mutableStateOf<Long?>(null)
    }
    var showCategoryPrompt by remember { mutableStateOf(false) }
    var multipleMatchCategories by remember { mutableStateOf<List<Category>>(emptyList()) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryDialogInitialName by remember { mutableStateOf("") }
    var categoryDialogInitialIcon by remember { mutableStateOf("shopping_cart") }
    var categoryDialogInitialColor by remember { mutableStateOf("#10B981") }
    var shouldRecordAfterCategoryCreated by remember { mutableStateOf(false) }

    var recentCategoryIds by remember(selectedType) { mutableStateOf(UserPreferences.getRecentCategoryIds(context, selectedType)) }
    val availableCategories = remember(categories, selectedType) { categories.filter { it.type == selectedType } }
    val sortedCategories = remember(availableCategories, recentCategoryIds) {
        UserPreferences.sortCategoriesByRecency(availableCategories, recentCategoryIds)
    }

    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: sortedAccounts.first()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_transaction_widget_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Uncluttered Top Bar: Account info on left, Clean Icon Toggles on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconUtils.getIcon(selectedAccount.iconName),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedAccount.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (isPrivacyMode) "••••••" else DateUtils.formatCurrency(selectedAccount.balance, selectedAccount.currency),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sleek Icon-only toggle buttons for Expense, Income, Transfer
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isExpense = selectedType == TransactionType.EXPENSE
                    val isIncome = selectedType == TransactionType.INCOME

                    // Expense icon button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedType = TransactionType.EXPENSE
                                selectedCategoryId = null
                            }
                            .testTag("quick_tab_expense"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = "Expense",
                            tint = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Income icon button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedType = TransactionType.INCOME
                                selectedCategoryId = null
                            }
                            .testTag("quick_tab_income"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Income",
                            tint = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Transfer icon button
                    /* Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                onOpenFullSheet(selectedAccountId, TransactionType.TRANSFER, amountValue.text, titleText, selectedCategoryId)
                            }
                            .testTag("quick_tab_transfer"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Transfer",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    } */
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedAccounts.forEach { acc ->
                    val isAccSelected = acc.id == selectedAccountId
                    val isLastUsed = acc.id == recentAccountIds.firstOrNull()
                    FilterChip(
                        selected = isAccSelected,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text(if (isLastUsed) "${acc.name} ★" else acc.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = IconUtils.getIcon(acc.iconName),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("quick_account_chip_${acc.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount and Title Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { amountValue = CurrencyUtils.formatCurrencyInput(it, amountValue.text) },
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    prefix = { Text(if (selectedAccount.currency == "PHP") "₱" else try { java.util.Currency.getInstance(selectedAccount.currency).symbol } catch (_: Exception) { selectedAccount.currency }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_amount_input")
                )

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Note") },
                    placeholder = { Text(if (selectedType == TransactionType.EXPENSE) "e.g. Lunch" else "e.g. Salary") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (titleText.isNotBlank()) {
                                    val matchResult = CategoryMatchingService.matchCategoryWithDetails(
                                        noteOrTitle = titleText,
                                        type = selectedType,
                                        availableCategories = categories,
                                        recentTransactions = recentTransactions
                                    )
                                    when (matchResult) {
                                        is CategoryMatchResult.SingleMatch -> {
                                            selectedCategoryId = matchResult.category.id
                                            multipleMatchCategories = emptyList()
                                        }
                                        is CategoryMatchResult.MultipleMatches -> {
                                            multipleMatchCategories = matchResult.matchedCategories
                                        }
                                        is CategoryMatchResult.NoMatch -> {
                                            showCategoryPrompt = true
                                        }
                                    }
                                }
                            },
                            enabled = titleText.isNotBlank(),
                            modifier = Modifier.testTag("quick_ai_categorize_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Auto-Categorize",
                                tint = if (titleText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("quick_title_input")
                )
            }

            // Category Chips (sorted with last-used first, no auto-selected default)
            if (sortedCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            val candidateName = titleText.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            if (onSaveCategory != null) {
                                categoryDialogInitialName = candidateName
                                categoryDialogInitialIcon = IconUtils.suggestIconForCategory(candidateName)
                                categoryDialogInitialColor = "#10B981"
                                shouldRecordAfterCategoryCreated = false
                                showAddCategoryDialog = true
                            } else {
                                onOpenFullSheet(selectedAccountId, selectedType, amountValue.text, titleText, null)
                            }
                        },
                        label = { Text("New", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("quick_add_category_chip")
                    )

                    sortedCategories.take(8).forEach { cat ->
                        val isCatSelected = cat.id == selectedCategoryId
                        val isLastUsed = cat.id == recentCategoryIds.firstOrNull()
                        FilterChip(
                            selected = isCatSelected,
                            onClick = { selectedCategoryId = if (isCatSelected) null else cat.id },
                            label = { Text(if (isLastUsed) "${cat.name} ★" else cat.name, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = IconUtils.getIcon(cat.iconName),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("quick_cat_chip_${cat.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: 1-Tap Save & Full Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenFullSheet(selectedAccountId, selectedType, amountValue.text, titleText, selectedCategoryId) }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("quick_more_options_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Full Details ↗",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val amount = CurrencyUtils.parseCurrencyInput(amountValue)
                        if (amount > 0) {
                            if (selectedCategoryId != null) {
                                val defaultTitle = if (titleText.isNotBlank()) titleText else {
                                    categories.find { it.id == selectedCategoryId }?.name ?: if (selectedType == TransactionType.EXPENSE) "Expense" else "Income"
                                }
                                val tx = Transaction(
                                    type = selectedType,
                                    amount = amount,
                                    accountId = selectedAccountId,
                                    categoryId = selectedCategoryId,
                                    title = defaultTitle,
                                    timestamp = System.currentTimeMillis()
                                )
                                UserPreferences.recordUsedAccount(context, selectedAccountId)
                                UserPreferences.recordUsedCategory(context, selectedType, selectedCategoryId!!)
                                recentAccountIds = UserPreferences.getRecentAccountIds(context)
                                recentCategoryIds = UserPreferences.getRecentCategoryIds(context, selectedType)
                                selectedAccountId = recentAccountIds.firstOrNull { id -> accounts.any { it.id == id } } ?: selectedAccountId
                                onSaveQuickTransaction(tx)
                                amountValue = TextFieldValue("")
                                titleText = ""
                                selectedCategoryId = null
                            } else {
                                // Category not manually selected yet: match via recent transaction or keywords
                                val matchResult = if (titleText.isNotBlank()) {
                                    CategoryMatchingService.matchCategoryWithDetails(
                                        noteOrTitle = titleText,
                                        type = selectedType,
                                        availableCategories = categories,
                                        recentTransactions = recentTransactions
                                    )
                                } else CategoryMatchResult.NoMatch

                                when (matchResult) {
                                    is CategoryMatchResult.SingleMatch -> {
                                        val defaultTitle = if (titleText.isNotBlank()) titleText else matchResult.category.name
                                        val tx = Transaction(
                                            type = selectedType,
                                            amount = amount,
                                            accountId = selectedAccountId,
                                            categoryId = matchResult.category.id,
                                            title = defaultTitle,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        UserPreferences.recordUsedAccount(context, selectedAccountId)
                                        UserPreferences.recordUsedCategory(context, selectedType, matchResult.category.id)
                                        recentAccountIds = UserPreferences.getRecentAccountIds(context)
                                        recentCategoryIds = UserPreferences.getRecentCategoryIds(context, selectedType)
                                        selectedAccountId = recentAccountIds.firstOrNull { id -> accounts.any { it.id == id } } ?: selectedAccountId
                                        onSaveQuickTransaction(tx)
                                        amountValue = TextFieldValue("")
                                        titleText = ""
                                        selectedCategoryId = null
                                    }
                                    is CategoryMatchResult.MultipleMatches -> {
                                        // Multiple categories match: prompt the user to choose
                                        multipleMatchCategories = matchResult.matchedCategories
                                    }
                                    is CategoryMatchResult.NoMatch -> {
                                        // No category matched: prompt user to choose or create
                                        showCategoryPrompt = true
                                    }
                                }
                            }
                        }
                    },
                    enabled = CurrencyUtils.parseCurrencyInput(amountValue) > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("quick_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record")
                }
            }
        }
    }

    if (multipleMatchCategories.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { multipleMatchCategories = emptyList() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (titleText.isNotBlank()) {
                            "Multiple categories match \"$titleText\". Please choose the correct one:"
                        } else {
                            "Multiple categories match. Please choose the correct one:"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        multipleMatchCategories.forEach { cat ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val chosenCat = cat
                                    multipleMatchCategories = emptyList()
                                    val amount = CurrencyUtils.parseCurrencyInput(amountValue)
                                    if (amount > 0) {
                                        val defaultTitle = if (titleText.isNotBlank()) titleText else chosenCat.name
                                        val tx = Transaction(
                                            type = selectedType,
                                            amount = amount,
                                            accountId = selectedAccountId,
                                            categoryId = chosenCat.id,
                                            title = defaultTitle,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        UserPreferences.recordUsedAccount(context, selectedAccountId)
                                        UserPreferences.recordUsedCategory(context, selectedType, chosenCat.id)
                                        recentAccountIds = UserPreferences.getRecentAccountIds(context)
                                        recentCategoryIds = UserPreferences.getRecentCategoryIds(context, selectedType)
                                        selectedAccountId = recentAccountIds.firstOrNull { id -> accounts.any { it.id == id } } ?: selectedAccountId
                                        onSaveQuickTransaction(tx)
                                        amountValue = TextFieldValue("")
                                        titleText = ""
                                        selectedCategoryId = null
                                    } else {
                                        selectedCategoryId = chosenCat.id
                                    }
                                },
                                label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = IconUtils.getIcon(cat.iconName),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.testTag("quick_multiple_match_cat_${cat.id}")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        multipleMatchCategories = emptyList()
                        showCategoryPrompt = true
                    }
                ) {
                    Text("Show All")
                }
            },
            dismissButton = {
                TextButton(onClick = { multipleMatchCategories = emptyList() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategoryPrompt) {
        AlertDialog(
            onDismissRequest = { showCategoryPrompt = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select a Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Please choose an existing category or create a new one:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Button to add new category
                    Button(
                        onClick = {
                            val candidateName = titleText.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            if (onSaveCategory != null) {
                                categoryDialogInitialName = candidateName
                                categoryDialogInitialIcon = IconUtils.suggestIconForCategory(candidateName)
                                categoryDialogInitialColor = "#10B981"
                                shouldRecordAfterCategoryCreated = true
                                showCategoryPrompt = false
                                showAddCategoryDialog = true
                            } else {
                                showCategoryPrompt = false
                                onOpenFullSheet(selectedAccountId, selectedType, amountValue.text, titleText, null)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_prompt_create_category_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (titleText.isNotBlank()) "Add \"${titleText.trim()}\" as New Category & Record" else "+ Create New Category & Record",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Or choose an existing category:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val candidateName = titleText.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                if (onSaveCategory != null) {
                                    categoryDialogInitialName = candidateName
                                    categoryDialogInitialIcon = IconUtils.suggestIconForCategory(candidateName)
                                    categoryDialogInitialColor = "#10B981"
                                    shouldRecordAfterCategoryCreated = true
                                    showCategoryPrompt = false
                                    showAddCategoryDialog = true
                                } else {
                                    showCategoryPrompt = false
                                    onOpenFullSheet(selectedAccountId, selectedType, amountValue.text, titleText, null)
                                }
                            },
                            label = { Text("New Category", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("quick_prompt_add_new_category_chip")
                        )

                        sortedCategories.forEach { cat ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val amount = CurrencyUtils.parseCurrencyInput(amountValue)
                                    val defaultTitle = if (titleText.isNotBlank()) titleText else cat.name
                                    val tx = Transaction(
                                        type = selectedType,
                                        amount = amount,
                                        accountId = selectedAccountId,
                                        categoryId = cat.id,
                                        title = defaultTitle,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    UserPreferences.recordUsedAccount(context, selectedAccountId)
                                    UserPreferences.recordUsedCategory(context, selectedType, cat.id)
                                    recentAccountIds = UserPreferences.getRecentAccountIds(context)
                                    recentCategoryIds = UserPreferences.getRecentCategoryIds(context, selectedType)
                                    selectedAccountId = recentAccountIds.firstOrNull { id -> accounts.any { it.id == id } } ?: selectedAccountId
                                    showCategoryPrompt = false
                                    onSaveQuickTransaction(tx)
                                    amountValue = TextFieldValue("")
                                    titleText = ""
                                    selectedCategoryId = null
                                },
                                label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = IconUtils.getIcon(cat.iconName),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("quick_prompt_cat_chip_${cat.id}")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCategoryPrompt = false },
                    modifier = Modifier.testTag("quick_prompt_cancel")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        CategoryAddDialog(
            defaultType = selectedType,
            initialName = categoryDialogInitialName,
            initialIconKey = categoryDialogInitialIcon,
            initialColorHex = categoryDialogInitialColor,
            onSave = { newCat ->
                coroutineScope.launch {
                    val newId = onSaveCategory?.invoke(newCat)
                    showAddCategoryDialog = false
                    if (newId != null) {
                        selectedCategoryId = newId
                        if (shouldRecordAfterCategoryCreated) {
                            shouldRecordAfterCategoryCreated = false
                            val amount = CurrencyUtils.parseCurrencyInput(amountValue)
                            if (amount > 0) {
                                val defaultTitle = if (titleText.isNotBlank()) titleText else newCat.name
                                val tx = Transaction(
                                    type = selectedType,
                                    amount = amount,
                                    accountId = selectedAccountId,
                                    categoryId = newId,
                                    title = defaultTitle,
                                    timestamp = System.currentTimeMillis()
                                )
                                UserPreferences.recordUsedAccount(context, selectedAccountId)
                                UserPreferences.recordUsedCategory(context, selectedType, newId)
                                recentAccountIds = UserPreferences.getRecentAccountIds(context)
                                recentCategoryIds = UserPreferences.getRecentCategoryIds(context, selectedType)
                                selectedAccountId = recentAccountIds.firstOrNull { id -> accounts.any { it.id == id } } ?: selectedAccountId
                                onSaveQuickTransaction(tx)
                                amountValue = TextFieldValue("")
                                titleText = ""
                                selectedCategoryId = null
                            }
                        }
                    }
                }
            },
            onDismiss = {
                showAddCategoryDialog = false
                shouldRecordAfterCategoryCreated = false
            }
        )
    }
}
