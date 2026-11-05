package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.style.TextOverflow
import com.example.util.CategorizationResult
import com.example.util.NewCategorySuggestion
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.RecurringFrequency
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.util.AiCategoryService
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import com.example.util.LoanUtils
import com.example.util.NotificationHelper
import com.example.util.UserPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddTransactionSheet(
    accounts: List<Account>,
    categories: List<Category>,
    initialAccountId: Long? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    initialAmount: String = "",
    initialTitle: String = "",
    initialCategoryId: Long? = null,
    initialIsLoan: Boolean = false,
    isPrivacyMode: Boolean = false,
    notificationHelper: NotificationHelper? = null,
    onSave: (Transaction) -> Unit,
    onSaveCategory: (suspend (Category) -> Long)? = null,
    onDismissRequest: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showCategoryRequiredPrompt by remember { mutableStateOf(false) }

    var type by remember { mutableStateOf(initialType) }

    val lastUsedAccountId = remember { UserPreferences.getLastUsedAccountId(context) }
    val sortedAccounts = remember(accounts, lastUsedAccountId) {
        UserPreferences.sortAccountsWithLastUsedFirst(accounts, lastUsedAccountId)
    }

    var selectedAccountId by remember {
        mutableLongStateOf(initialAccountId ?: lastUsedAccountId ?: sortedAccounts.firstOrNull()?.id ?: 0L)
    }
    var targetAccountId by remember {
        val remaining = sortedAccounts.filter { it.id != selectedAccountId }
        mutableLongStateOf(remaining.firstOrNull()?.id ?: 0L)
    }
    var amountValue by remember { mutableStateOf(CurrencyUtils.toTextFieldValue(initialAmount)) }
    var feeValue by remember { mutableStateOf(TextFieldValue("")) }
    var titleText by remember { mutableStateOf(initialTitle) }
    var noteText by remember { mutableStateOf("") }

    // User requirement: No auto-selected category unless carried over from quick input
    var selectedCategoryId by remember(type) {
        mutableStateOf<Long?>(if (type == initialType) initialCategoryId else null)
    }

    // AI Auto-Categorize State
    var isAiCategorizing by remember { mutableStateOf(false) }
    var aiSuggestionBanner by remember { mutableStateOf<String?>(null) }
    var aiSuggestedNewCategory by remember { mutableStateOf<NewCategorySuggestion?>(null) }
    var shouldRecordAfterCategoryCreated by remember { mutableStateOf(false) }
    var categoryDialogInitialName by remember { mutableStateOf("") }
    var categoryDialogInitialIcon by remember { mutableStateOf("shopping_cart") }
    var categoryDialogInitialColor by remember { mutableStateOf("#10B981") }

    // Borrowing & Lending State
    var isLoanEnabled by remember { mutableStateOf(initialIsLoan) }
    var loanPerson by remember { mutableStateOf("") }
    var loanDueDate by remember { mutableStateOf<Long?>(null) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var hasInterest by remember { mutableStateOf(false) }
    var interestRateText by remember { mutableStateOf("") }
    var interestType by remember { mutableStateOf("MONTHLY") }
    var isInterestTypeDropdownOpen by remember { mutableStateOf(false) }
    var hasTerm by remember { mutableStateOf(false) }
    var termLengthText by remember { mutableStateOf("1") }
    var termUnit by remember { mutableStateOf("MONTHS") }
    var isTermUnitDropdownOpen by remember { mutableStateOf(false) }
    var enableLoanReminder by remember { mutableStateOf(true) }

    // Recurring parameters
    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    var recurringOccurrencesText by remember { mutableStateOf("12") }
    var isUnlimitedRecurring by remember { mutableStateOf(false) }

    var isAccountDropdownOpen by remember { mutableStateOf(false) }
    var isTargetAccountDropdownOpen by remember { mutableStateOf(false) }
    var isFrequencyDropdownOpen by remember { mutableStateOf(false) }

    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val targetAccount = accounts.find { it.id == targetAccountId }

    fun runAiCategorize() {
        val query = noteText.ifBlank { titleText }
        if (query.isBlank()) {
            aiSuggestionBanner = "Enter a title or note to auto-categorize"
            return
        }
        coroutineScope.launch {
            isAiCategorizing = true
            aiSuggestionBanner = null
            aiSuggestedNewCategory = null
            val result = AiCategoryService.categorizeOrSuggestNew(context, query, type, categories)
            isAiCategorizing = false
            when (result) {
                is CategorizationResult.MatchedExisting -> {
                    selectedCategoryId = result.suggestion.category.id
                    aiSuggestionBanner = "✨ ${result.suggestion.message}"
                    aiSuggestedNewCategory = null
                }
                is CategorizationResult.SuggestedNew -> {
                    aiSuggestedNewCategory = result.newCategory
                    aiSuggestionBanner = "✨ Suggested New Category: \"${result.newCategory.name}\""
                }
                is CategorizationResult.None -> {
                    aiSuggestionBanner = "No matching category found for \"$query\""
                    aiSuggestedNewCategory = null
                }
            }
        }
    }

    fun executeSaveWithCategory(catId: Long?) {
        val amount = CurrencyUtils.parseCurrencyInput(amountValue)
        if (amount <= 0) return

        val fee = CurrencyUtils.parseCurrencyInput(feeValue)
        val finalTitle = if (titleText.isNotBlank()) titleText else {
            if (type == TransactionType.TRANSFER) {
                "Transfer to ${targetAccount?.name ?: "Account"}"
            } else if (isLoanEnabled && loanPerson.isNotBlank()) {
                if (type == TransactionType.INCOME) "Borrowed from $loanPerson" else "Lent to $loanPerson"
            } else {
                categories.find { it.id == catId }?.name ?: type.label
            }
        }

        val totalCount = if (isRecurring) {
            if (isUnlimitedRecurring) 0 else (recurringOccurrencesText.toIntOrNull() ?: 12)
        } else 0

        val isLoanFinal = (type != TransactionType.TRANSFER) && isLoanEnabled && loanPerson.isNotBlank()

        val tx = Transaction(
            type = type,
            amount = amount,
            accountId = selectedAccountId,
            toAccountId = if (type == TransactionType.TRANSFER) targetAccountId else null,
            transferFee = fee,
            categoryId = if (type != TransactionType.TRANSFER) catId else null,
            title = finalTitle,
            note = noteText,
            timestamp = System.currentTimeMillis(),
            isRecurring = isRecurring,
            recurringFrequency = if (isRecurring) frequency else RecurringFrequency.NONE,
            recurringTotalCount = totalCount,
            recurringExecutedCount = 0,
            isLoan = isLoanFinal,
            loanPerson = if (isLoanFinal) loanPerson.trim() else "",
            loanDueDate = if (isLoanFinal) loanDueDate else null,
            hasInterest = isLoanFinal && hasInterest,
            interestRate = if (isLoanFinal && hasInterest) interestRateText.toDoubleOrNull() else null,
            interestType = interestType,
            hasTerm = isLoanFinal && hasTerm,
            termUnit = termUnit,
            termLength = if (isLoanFinal && hasTerm) (termLengthText.toIntOrNull() ?: 1) else 1,
            isLoanSettled = false
        )

        // Save preferences: last used account & category
        UserPreferences.setLastUsedAccountId(context, selectedAccountId)
        if (catId != null) {
            UserPreferences.setLastUsedCategoryId(context, type, catId)
        }

        // Trigger loan notification alert if enabled
        if (isLoanFinal && enableLoanReminder && loanDueDate != null && notificationHelper != null) {
            val loanCurrency = currentAccount?.currency ?: DateUtils.getDefaultCurrencyCode()
            val totalPayable = LoanUtils.calculateTotalPayable(tx)
            notificationHelper.sendLoanDueNotification(
                person = loanPerson.trim(),
                isBorrowing = (type == TransactionType.INCOME),
                amountFormatted = DateUtils.formatCurrency(totalPayable, loanCurrency),
                dueDateFormatted = DateUtils.formatDate(loanDueDate!!)
            )
        }

        selectedCategoryId = null
        aiSuggestedNewCategory = null
        aiSuggestionBanner = null
        amountValue = TextFieldValue("")
        titleText = ""
        noteText = ""
        feeValue = TextFieldValue("")

        onSave(tx)
        onDismissRequest()
    }

    val hasInputtedValues by remember {
        derivedStateOf {
            amountValue.text.isNotBlank() ||
            titleText.isNotBlank() ||
            noteText.isNotBlank() ||
            feeValue.text.isNotBlank() ||
            selectedCategoryId != null ||
            (isLoanEnabled && loanPerson.isNotBlank()) ||
            interestRateText.isNotBlank()
        }
    }

    var isExplicitCloseApproved by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (targetValue == SheetValue.Hidden) {
                if (hasInputtedValues && !isExplicitCloseApproved) {
                    // Prevent accidental dismissal when user swipes down to hide virtual keyboard!
                    false
                } else {
                    true
                }
            } else {
                true
            }
        }
    )

    BackHandler(enabled = true) {
        if (hasInputtedValues && !isExplicitCloseApproved) {
            showDiscardDialog = true
        } else {
            coroutineScope.launch {
                sheetState.hide()
                onDismissRequest()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (hasInputtedValues && !isExplicitCloseApproved) {
                showDiscardDialog = true
            } else {
                onDismissRequest()
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "New Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        if (hasInputtedValues && !isExplicitCloseApproved) {
                            showDiscardDialog = true
                        } else {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismissRequest()
                            }
                        }
                    },
                    modifier = Modifier.testTag("close_sheet_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Type selector pills (Expense, Income, Transfer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TransactionType.values().forEach { t ->
                    val isSelected = type == t
                    val activeBg = when (t) {
                        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                        TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                        TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiary
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) activeBg else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                type = t
                                // User requirement: No auto-selected category!
                                selectedCategoryId = null
                                aiSuggestionBanner = null
                            }
                            .testTag("sheet_type_tab_${t.name}")
                    ) {
                        Text(
                            text = t.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (accounts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "⚠️ No accounts found. Please add an account in the Accounts tab before recording transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Amount field
            OutlinedTextField(
                value = amountValue,
                onValueChange = { amountValue = CurrencyUtils.formatCurrencyInput(it, amountValue.text) },
                label = { Text("Amount *") },
                placeholder = { Text("0.00") },
                prefix = {
                    val curr = currentAccount?.currency ?: DateUtils.getDefaultCurrencyCode()
                    Text(if (curr == "PHP") "₱" else curr, fontWeight = FontWeight.Bold)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_amount_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Account selection
            if (type == TransactionType.TRANSFER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // From account
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { isAccountDropdownOpen = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheet_from_account_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                Text("From Account", style = MaterialTheme.typography.labelSmall)
                                Text(currentAccount?.name ?: "Select", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isAccountDropdownOpen,
                            onDismissRequest = { isAccountDropdownOpen = false }
                        ) {
                            sortedAccounts.forEach { acc ->
                                val isLastUsed = acc.id == lastUsedAccountId
                                val balanceText = if (isPrivacyMode) "" else " (${DateUtils.formatCurrency(acc.balance, acc.currency)})"
                                DropdownMenuItem(
                                    text = { Text("${acc.name}$balanceText${if (isLastUsed) " • Last Used" else ""}") },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        isAccountDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // To account
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { isTargetAccountDropdownOpen = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheet_to_account_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                Text("To Account", style = MaterialTheme.typography.labelSmall)
                                Text(targetAccount?.name ?: "Select", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isTargetAccountDropdownOpen,
                            onDismissRequest = { isTargetAccountDropdownOpen = false }
                        ) {
                            sortedAccounts.filter { it.id != selectedAccountId }.forEach { acc ->
                                val balanceText = if (isPrivacyMode) "" else " (${DateUtils.formatCurrency(acc.balance, acc.currency)})"
                                DropdownMenuItem(
                                    text = { Text("${acc.name}$balanceText") },
                                    onClick = {
                                        targetAccountId = acc.id
                                        isTargetAccountDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Transfer fee
                OutlinedTextField(
                    value = feeValue,
                    onValueChange = { feeValue = CurrencyUtils.formatCurrencyInput(it, feeValue.text) },
                    label = { Text("Transfer Fee (Optional)") },
                    placeholder = { Text("0.00") },
                    prefix = {
                        val curr = currentAccount?.currency ?: DateUtils.getDefaultCurrencyCode()
                        Text(if (curr == "PHP") "₱" else curr)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_transfer_fee_input")
                )
            } else {
                // Single account selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isAccountDropdownOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_account_selector_btn")
                    ) {
                        IconUtils.AccountIconView(
                            iconName = currentAccount?.iconName,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                            Text("Account", style = MaterialTheme.typography.labelSmall)
                            val balanceText = if (isPrivacyMode) "" else " • Balance: ${DateUtils.formatCurrency(currentAccount?.balance ?: 0.0, currentAccount?.currency ?: DateUtils.getDefaultCurrencyCode())}"
                            Text(
                                "${currentAccount?.name ?: "Select Account"}$balanceText",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = isAccountDropdownOpen,
                        onDismissRequest = { isAccountDropdownOpen = false }
                    ) {
                        sortedAccounts.forEach { acc ->
                            val isLastUsed = acc.id == lastUsedAccountId
                            val balanceText = if (isPrivacyMode) "" else " (${DateUtils.formatCurrency(acc.balance, acc.currency)})"
                            DropdownMenuItem(
                                leadingIcon = {
                                    IconUtils.AccountIconView(acc.iconName, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                text = { Text("${acc.name}$balanceText${if (isLastUsed) " • Last Used" else ""}") },
                                onClick = {
                                    selectedAccountId = acc.id
                                    isAccountDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // Category selector (for Expense & Income)
            if (type != TransactionType.TRANSFER) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // AI Auto-Categorize Button
                    TextButton(
                        onClick = { runAiCategorize() },
                        enabled = !isAiCategorizing,
                        modifier = Modifier.testTag("ai_categorize_button")
                    ) {
                        if (isAiCategorizing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Matching...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Auto-Assign", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (aiSuggestionBanner != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = aiSuggestionBanner.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { aiSuggestionBanner = null },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                if (aiSuggestedNewCategory != null) {
                    val suggested = aiSuggestedNewCategory!!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("ai_suggested_new_category_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(runCatching { Color(android.graphics.Color.parseColor(suggested.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.tertiary)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconUtils.getIcon(suggested.iconName),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "AI Suggested Category",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = suggested.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (onSaveCategory != null) {
                                FilledTonalButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val newCat = Category(
                                                name = suggested.name,
                                                type = type,
                                                iconName = suggested.iconName,
                                                colorHex = suggested.colorHex
                                            )
                                            val createdId = onSaveCategory(newCat)
                                            selectedCategoryId = createdId
                                            aiSuggestedNewCategory = null
                                            aiSuggestionBanner = "✨ Created & selected category \"${suggested.name}\""
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("create_ai_suggested_category_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Add & Select", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val lastUsedCategoryId = remember(type) { UserPreferences.getLastUsedCategoryId(context, type) }
                val availableCats = remember(type, categories) { categories.filter { it.type == type } }
                val sortedCategories = remember(availableCats, lastUsedCategoryId) {
                    UserPreferences.sortCategoriesWithLastUsedFirst(availableCats, lastUsedCategoryId)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedCategories.forEach { cat ->
                        val isSelected = cat.id == selectedCategoryId
                        val isLastUsed = cat.id == lastUsedCategoryId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = if (isSelected) null else cat.id },
                            label = { Text(if (isLastUsed) "${cat.name} ★" else cat.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = IconUtils.getIcon(cat.iconName),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("sheet_cat_${cat.id}")
                        )
                    }

                    if (onSaveCategory != null) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                categoryDialogInitialName = ""
                                categoryDialogInitialIcon = "shopping_cart"
                                categoryDialogInitialColor = "#10B981"
                                shouldRecordAfterCategoryCreated = false
                                showAddCategoryDialog = true
                            },
                            label = { Text("+ New Category") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.testTag("sheet_add_new_category_chip")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title and Note
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Title / Description") },
                placeholder = {
                    val catName = categories.find { it.id == selectedCategoryId }?.name
                    Text(catName ?: if (type == TransactionType.EXPENSE) "e.g. Dinner with friends" else "e.g. Monthly salary")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_title_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note (Optional)") },
                placeholder = { Text("Add note (e.g. 'Lunch at Jollibee with Alex')") },
                trailingIcon = {
                    IconButton(
                        onClick = { runAiCategorize() },
                        enabled = !isAiCategorizing,
                        modifier = Modifier.testTag("note_ai_categorize_btn")
                    ) {
                        if (isAiCategorizing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Auto-Categorize",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_note_input")
            )

            // Borrowing & Lending Section (User requirement)
            if (type != TransactionType.TRANSFER) {
                Spacer(modifier = Modifier.height(14.dp))
                val isIncome = type == TransactionType.INCOME
                val loanSectionTitle = if (isIncome) "Borrowed Money (Debt to Repay)" else "Money Lent (Loan to Collect)"
                val loanSectionDesc = if (isIncome) "Record who you borrowed from and repayment terms" else "Record who you lent to and repayment terms"
                val themeAccent = if (isIncome) Color(0xFFF59E0B) else Color(0xFFF43F5E)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isLoanEnabled) themeAccent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isLoanEnabled) androidx.compose.foundation.BorderStroke(1.dp, themeAccent.copy(alpha = 0.4f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Handshake,
                                    contentDescription = null,
                                    tint = if (isLoanEnabled) themeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = loanSectionTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = loanSectionDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isLoanEnabled,
                                onCheckedChange = { checked ->
                                    isLoanEnabled = checked
                                    if (checked) {
                                        val targetCatName = if (isIncome) "Loan / Borrowed Money" else "Loan / Money Lent"
                                        val loanCat = categories.find { it.name.equals(targetCatName, ignoreCase = true) }
                                        if (loanCat != null) {
                                            selectedCategoryId = loanCat.id
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("sheet_loan_toggle")
                            )
                        }

                        if (isLoanEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Who did you borrow from / lend to
                            OutlinedTextField(
                                value = loanPerson,
                                onValueChange = { loanPerson = it },
                                label = { Text(if (isIncome) "Who did you borrow it from? *" else "Who did you lend it to? *") },
                                placeholder = { Text(if (isIncome) "e.g. John, Bank, Boss, Mom" else "e.g. Sarah, Alex, Dave") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sheet_loan_person_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2. When will it be needed to be paid (optional)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isIncome) "Payment Due Date (Optional)" else "Repayment Due Date (Optional)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (loanDueDate != null) DateUtils.formatDate(loanDueDate!!) else "No due date set",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (loanDueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row {
                                    if (loanDueDate != null) {
                                        TextButton(onClick = { loanDueDate = null }) {
                                            Text("Clear")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { showDueDatePicker = true },
                                        modifier = Modifier.testTag("sheet_loan_due_date_btn")
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (loanDueDate != null) "Change" else "Set Due Date")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 3. Interest (optional: total / monthly / annual)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Is there an interest? (Optional)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Checkbox(
                                    checked = hasInterest,
                                    onCheckedChange = { hasInterest = it },
                                    modifier = Modifier.testTag("sheet_has_interest_checkbox")
                                )
                            }

                            if (hasInterest) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = interestRateText,
                                        onValueChange = { interestRateText = it },
                                        label = { Text("Rate (%)") },
                                        placeholder = { Text("e.g. 5") },
                                        suffix = { Text("%") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("sheet_interest_rate_input")
                                    )

                                    Box(modifier = Modifier.weight(1.3f)) {
                                        OutlinedButton(
                                            onClick = { isInterestTypeDropdownOpen = true },
                                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                        ) {
                                            Text(
                                                when (interestType) {
                                                    "MONTHLY" -> "Monthly"
                                                    "ANNUAL" -> "Annual"
                                                    else -> "Total Fixed"
                                                },
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = isInterestTypeDropdownOpen,
                                            onDismissRequest = { isInterestTypeDropdownOpen = false }
                                        ) {
                                            DropdownMenuItem(text = { Text("Monthly Rate") }, onClick = { interestType = "MONTHLY"; isInterestTypeDropdownOpen = false })
                                            DropdownMenuItem(text = { Text("Annual Rate") }, onClick = { interestType = "ANNUAL"; isInterestTypeDropdownOpen = false })
                                            DropdownMenuItem(text = { Text("Total Fixed Rate") }, onClick = { interestType = "TOTAL"; isInterestTypeDropdownOpen = false })
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 4. Term? like weekly, monthly, and how long
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Is there a term? (Optional)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Checkbox(
                                    checked = hasTerm,
                                    onCheckedChange = { hasTerm = it },
                                    modifier = Modifier.testTag("sheet_has_term_checkbox")
                                )
                            }

                            if (hasTerm) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = termLengthText,
                                        onValueChange = { termLengthText = it },
                                        label = { Text("Duration") },
                                        placeholder = { Text("e.g. 6") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("sheet_term_duration_input")
                                    )

                                    Box(modifier = Modifier.weight(1.3f)) {
                                        OutlinedButton(
                                            onClick = { isTermUnitDropdownOpen = true },
                                            modifier = Modifier.fillMaxWidth().height(56.dp)
                                        ) {
                                            Text(
                                                when (termUnit) {
                                                    "WEEKS" -> "Weekly (Weeks)"
                                                    "MONTHS" -> "Monthly (Months)"
                                                    "YEARS" -> "Yearly (Years)"
                                                    else -> "Days"
                                                },
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = isTermUnitDropdownOpen,
                                            onDismissRequest = { isTermUnitDropdownOpen = false }
                                        ) {
                                            DropdownMenuItem(text = { Text("Weekly (Weeks)") }, onClick = { termUnit = "WEEKS"; isTermUnitDropdownOpen = false })
                                            DropdownMenuItem(text = { Text("Monthly (Months)") }, onClick = { termUnit = "MONTHS"; isTermUnitDropdownOpen = false })
                                            DropdownMenuItem(text = { Text("Yearly (Years)") }, onClick = { termUnit = "YEARS"; isTermUnitDropdownOpen = false })
                                            DropdownMenuItem(text = { Text("Days") }, onClick = { termUnit = "DAYS"; isTermUnitDropdownOpen = false })
                                        }
                                    }
                                }
                            }

                            // Live Repayment Preview when interest is entered
                            if (hasInterest && (interestRateText.toDoubleOrNull() ?: 0.0) > 0.0) {
                                val loanCurrency = currentAccount?.currency ?: DateUtils.getDefaultCurrencyCode()
                                val principalAmt = CurrencyUtils.parseCurrencyInput(amountValue)
                                val rate = interestRateText.toDoubleOrNull() ?: 0.0
                                val dur = termLengthText.toIntOrNull() ?: 1
                                val previewInterest = LoanUtils.calculateTotalInterest(
                                    principal = principalAmt,
                                    hasInterest = true,
                                    interestRate = rate,
                                    interestType = interestType,
                                    hasTerm = hasTerm,
                                    termUnit = termUnit,
                                    termLength = dur,
                                    dueDate = loanDueDate
                                )
                                val totalRepayment = principalAmt + previewInterest
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = themeAccent.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, themeAccent.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "Repayment Calculation Preview ($loanCurrency)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = themeAccent
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Principal:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(DateUtils.formatCurrency(principalAmt, loanCurrency), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Calculated Interest:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("+${DateUtils.formatCurrency(previewInterest, loanCurrency)} ($rate% ${interestType.lowercase()})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = themeAccent)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(if (isIncome) "Total to Repay:" else "Total to Collect:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(DateUtils.formatCurrency(totalRepayment, loanCurrency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = themeAccent)
                                        }
                                    }
                                }
                            }

                            // 5. Add to notification toggle
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "Add to notifications",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Alert me for repayment due date",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = enableLoanReminder,
                                    onCheckedChange = { enableLoanReminder = it },
                                    modifier = Modifier.testTag("sheet_loan_reminder_switch")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recurring Transaction Setting Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Recurring Transaction",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Repeat automatically on a schedule",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it },
                            modifier = Modifier.testTag("sheet_recurring_switch")
                        )
                    }

                    if (isRecurring) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Frequency dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isFrequencyDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Repeat: ${frequency.label}", modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = isFrequencyDropdownOpen,
                                onDismissRequest = { isFrequencyDropdownOpen = false }
                            ) {
                                RecurringFrequency.values().filter { it != RecurringFrequency.NONE }.forEach { freq ->
                                    DropdownMenuItem(
                                        text = { Text(freq.label) },
                                        onClick = {
                                            frequency = freq
                                            isFrequencyDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // How many times setting
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Repeat Count",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = isUnlimitedRecurring,
                                    onClick = { isUnlimitedRecurring = !isUnlimitedRecurring },
                                    label = { Text("Indefinite") },
                                    modifier = Modifier.testTag("sheet_unlimited_recurring_chip")
                                )
                            }
                        }

                        if (!isUnlimitedRecurring) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = recurringOccurrencesText,
                                onValueChange = { recurringOccurrencesText = it },
                                label = { Text("Number of times to repeat") },
                                placeholder = { Text("e.g. 12 times") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sheet_recurring_count_input")
                            )
                        }
                    }
                }
            }



            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = {
                    val amount = CurrencyUtils.parseCurrencyInput(amountValue)
                    if (amount > 0) {
                        if (type == TransactionType.TRANSFER) {
                            executeSaveWithCategory(null)
                        } else if (selectedCategoryId != null) {
                            executeSaveWithCategory(selectedCategoryId)
                        } else {
                            // User forgot to select category: AI auto-categorizes or suggests new category, else prompt
                            val query = noteText.ifBlank { titleText }
                            coroutineScope.launch {
                                isAiCategorizing = true
                                aiSuggestionBanner = "AI analyzing transaction..."
                                val result = if (query.isNotBlank()) {
                                    AiCategoryService.categorizeOrSuggestNew(context, query, type, categories)
                                } else CategorizationResult.None
                                isAiCategorizing = false

                                when (result) {
                                    is CategorizationResult.MatchedExisting -> {
                                        selectedCategoryId = result.suggestion.category.id
                                        aiSuggestionBanner = "✨ ${result.suggestion.message}"
                                        executeSaveWithCategory(result.suggestion.category.id)
                                    }
                                    is CategorizationResult.SuggestedNew -> {
                                        aiSuggestedNewCategory = result.newCategory
                                        aiSuggestionBanner = "✨ Suggested New Category: \"${result.newCategory.name}\""
                                        showCategoryRequiredPrompt = true
                                    }
                                    is CategorizationResult.None -> {
                                        aiSuggestedNewCategory = null
                                        showCategoryRequiredPrompt = true
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = accounts.isNotEmpty() && CurrencyUtils.parseCurrencyInput(amountValue) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (type) {
                        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                        TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                        TransactionType.TRANSFER -> MaterialTheme.colorScheme.tertiary
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sheet_submit_button")
            ) {
                if (isAiCategorizing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Selecting Category...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                } else if (accounts.isEmpty()) {
                    Text(
                        text = "Add an Account First",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Save Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Discard Transaction?")
            },
            text = {
                Text("You have entered transaction details that haven't been saved yet. Do you want to discard them?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        isExplicitCloseApproved = true
                        coroutineScope.launch {
                            sheetState.hide()
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_discard_button")
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                    modifier = Modifier.testTag("cancel_discard_button")
                ) {
                    Text("Keep Editing")
                }
            }
        )
    }

    // Prompt Dialog when User Forgot to Select Category or needs to pick/create category
    if (showCategoryRequiredPrompt) {
        val promptCategories = remember(type, categories) {
            val lastUsed = UserPreferences.getLastUsedCategoryId(context, type)
            val filtered = categories.filter { it.type == type }
            UserPreferences.sortCategoriesWithLastUsedFirst(filtered, lastUsed)
        }

        AlertDialog(
            onDismissRequest = { showCategoryRequiredPrompt = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (aiSuggestedNewCategory != null) "Category Suggestion" else "Choose Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (aiSuggestedNewCategory != null) {
                        val suggested = aiSuggestedNewCategory!!
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(runCatching { Color(android.graphics.Color.parseColor(suggested.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.tertiary)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconUtils.getIcon(suggested.iconName),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "AI Suggested New Category",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = suggested.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }

                                if (onSaveCategory != null) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val newCat = Category(
                                                    name = suggested.name,
                                                    type = type,
                                                    iconName = suggested.iconName,
                                                    colorHex = suggested.colorHex
                                                )
                                                val newId = onSaveCategory(newCat)
                                                showCategoryRequiredPrompt = false
                                                executeSaveWithCategory(newId)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("prompt_create_suggested_category_btn")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create \"${suggested.name}\" & Record")
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Or choose an existing category manually:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "AI could not find or suggest a matching category for this transaction. Please select an existing category or create a new one:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Existing categories scroll
                    if (promptCategories.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            promptCategories.forEach { cat ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        showCategoryRequiredPrompt = false
                                        executeSaveWithCategory(cat.id)
                                    },
                                    label = { Text(cat.name) },
                                    leadingIcon = {
                                        Icon(IconUtils.getIcon(cat.iconName), contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("prompt_cat_chip_${cat.id}")
                                )
                            }
                        }
                    }

                    // Manual create new category button
                    if (onSaveCategory != null) {
                        OutlinedButton(
                            onClick = {
                                categoryDialogInitialName = if (aiSuggestedNewCategory != null) "" else titleText.trim()
                                categoryDialogInitialIcon = "shopping_cart"
                                categoryDialogInitialColor = "#10B981"
                                shouldRecordAfterCategoryCreated = true
                                showCategoryRequiredPrompt = false
                                showAddCategoryDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prompt_create_new_category_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create New Category & Record")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCategoryRequiredPrompt = false },
                    modifier = Modifier.testTag("prompt_dismiss_btn")
                ) {
                    Text("Select on Sheet")
                }
            }
        )
    }

    // Due Date Picker Dialog for Borrowing / Lending
    if (showDueDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = loanDueDate ?: (System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
        )
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        loanDueDate = datePickerState.selectedDateMillis
                        showDueDatePicker = false
                    }
                ) {
                    Text("Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Add Category Dialog
    if (showAddCategoryDialog && onSaveCategory != null) {
        CategoryAddDialog(
            defaultType = type,
            initialName = categoryDialogInitialName,
            initialIconKey = categoryDialogInitialIcon,
            initialColorHex = categoryDialogInitialColor,
            onSave = { newCat ->
                coroutineScope.launch {
                    val newId = onSaveCategory(newCat)
                    selectedCategoryId = newId
                    showAddCategoryDialog = false
                    if (shouldRecordAfterCategoryCreated) {
                        shouldRecordAfterCategoryCreated = false
                        executeSaveWithCategory(newId)
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

@Composable
private fun CategoryAddDialog(
    defaultType: TransactionType,
    initialName: String = "",
    initialIconKey: String = "shopping_cart",
    initialColorHex: String = "#10B981",
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var iconKey by remember(initialIconKey) { mutableStateOf(initialIconKey) }
    var colorHex by remember(initialColorHex) { mutableStateOf(initialColorHex) }
    var showIconPicker by remember { mutableStateOf(false) }

    val presetColors = listOf(
        "#10B981", "#3B82F6", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4", "#EC4899", "#1E293B"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Category") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary))
                                .clickable { showIconPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconUtils.getIcon(iconKey),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showIconPicker = true }) {
                            Text("Choose Icon")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val cat = Category(
                            name = name.trim(),
                            type = defaultType,
                            iconName = iconKey,
                            colorHex = colorHex
                        )
                        onSave(cat)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showIconPicker) {
        IconPickerDialog(
            selectedIconKey = iconKey,
            onIconSelected = { key ->
                iconKey = key
                showIconPicker = false
            },
            onDismissRequest = { showIconPicker = false }
        )
    }
}
