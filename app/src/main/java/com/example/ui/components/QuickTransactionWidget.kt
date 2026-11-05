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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.example.util.AiCategoryService
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

@Composable
fun QuickTransactionWidget(
    accounts: List<Account>,
    categories: List<Category>,
    onSaveQuickTransaction: (Transaction) -> Unit,
    onOpenFullSheet: (
        selectedAccountId: Long?,
        type: TransactionType,
        amount: String,
        title: String,
        categoryId: Long?
    ) -> Unit,
    isPrivacyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (accounts.isEmpty()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val lastUsedAccountId = remember { UserPreferences.getLastUsedAccountId(context) }
    val sortedAccounts = remember(accounts, lastUsedAccountId) {
        UserPreferences.sortAccountsWithLastUsedFirst(accounts, lastUsedAccountId)
    }

    var selectedAccountId by remember(accounts) {
        mutableStateOf(lastUsedAccountId ?: sortedAccounts.firstOrNull()?.id ?: 0L)
    }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountValue by remember { mutableStateOf(TextFieldValue("")) }
    var titleText by remember { mutableStateOf("") }

    // User requirement: No auto-selected category!
    var selectedCategoryId by remember(selectedType) {
        mutableStateOf<Long?>(null)
    }
    var isAiMatching by remember { mutableStateOf(false) }
    var showCategoryPrompt by remember { mutableStateOf(false) }

    val lastUsedCategoryId = remember(selectedType) { UserPreferences.getLastUsedCategoryId(context, selectedType) }
    val availableCategories = remember(categories, selectedType) { categories.filter { it.type == selectedType } }
    val sortedCategories = remember(availableCategories, lastUsedCategoryId) {
        UserPreferences.sortCategoriesWithLastUsedFirst(availableCategories, lastUsedCategoryId)
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
                    Box(
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
                    }
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
                    val isLastUsed = acc.id == lastUsedAccountId
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
                    label = { Text("Note / Title") },
                    placeholder = { Text(if (selectedType == TransactionType.EXPENSE) "e.g. Lunch" else "e.g. Salary") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (titleText.isNotBlank()) {
                                    coroutineScope.launch {
                                        isAiMatching = true
                                        val match = AiCategoryService.suggestCategory(context, titleText, selectedType, categories)
                                        isAiMatching = false
                                        if (match != null) {
                                            selectedCategoryId = match.category.id
                                        }
                                    }
                                }
                            },
                            enabled = !isAiMatching && titleText.isNotBlank(),
                            modifier = Modifier.testTag("quick_ai_categorize_btn")
                        ) {
                            if (isAiMatching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Auto-Categorize",
                                    tint = if (titleText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                    sortedCategories.take(8).forEach { cat ->
                        val isCatSelected = cat.id == selectedCategoryId
                        val isLastUsed = cat.id == lastUsedCategoryId
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
                                UserPreferences.setLastUsedAccountId(context, selectedAccountId)
                                UserPreferences.setLastUsedCategoryId(context, selectedType, selectedCategoryId!!)
                                onSaveQuickTransaction(tx)
                                amountValue = TextFieldValue("")
                                titleText = ""
                                selectedCategoryId = null
                            } else {
                                // User forgot to select category: AI auto-categorizes
                                coroutineScope.launch {
                                    isAiMatching = true
                                    val match = if (titleText.isNotBlank()) {
                                        AiCategoryService.suggestCategory(context, titleText, selectedType, categories)
                                    } else null
                                    isAiMatching = false

                                    if (match != null) {
                                        val defaultTitle = if (titleText.isNotBlank()) titleText else match.category.name
                                        val tx = Transaction(
                                            type = selectedType,
                                            amount = amount,
                                            accountId = selectedAccountId,
                                            categoryId = match.category.id,
                                            title = defaultTitle,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        UserPreferences.setLastUsedAccountId(context, selectedAccountId)
                                        UserPreferences.setLastUsedCategoryId(context, selectedType, match.category.id)
                                        onSaveQuickTransaction(tx)
                                        amountValue = TextFieldValue("")
                                        titleText = ""
                                        selectedCategoryId = null
                                    } else {
                                        // No category inferred: prompt user to choose category
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
                    if (isAiMatching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI...", style = MaterialTheme.typography.labelSmall)
                    } else {
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
    }

    if (showCategoryPrompt) {
        AlertDialog(
            onDismissRequest = { showCategoryPrompt = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select a Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AI couldn't infer a category. Please choose an appropriate category for this ${selectedType.label.lowercase()}:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                    UserPreferences.setLastUsedAccountId(context, selectedAccountId)
                                    UserPreferences.setLastUsedCategoryId(context, selectedType, cat.id)
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
                                }
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
}
