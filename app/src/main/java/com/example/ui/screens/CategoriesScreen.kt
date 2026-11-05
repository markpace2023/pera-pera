package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.Budget
import com.example.data.model.Category
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import com.example.ui.components.IconPickerDialog
import com.example.ui.components.IconUtils
import com.example.ui.components.ScreenHeader
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import java.util.Calendar

@Composable
fun CategoriesScreen(
    categories: List<Category>,
    budgets: List<Budget>,
    transactions: List<TransactionWithDetails>,
    onSaveCategory: (Category) -> Unit,
    onDeleteCategory: (category: Category, targetCategoryId: Long?) -> Unit,
    onSaveBudget: (Budget) -> Unit,
    onTestBudgetNotification: (categoryName: String, percent: Int, spentFormatted: String, limitFormatted: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Expense, 1 = Income
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var budgetCategoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryPendingDelete by remember { mutableStateOf<Category?>(null) }

    val currentType = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME
    val filteredCategories = categories.filter { it.type == currentType }

    // Calculate monthly spent per category for current month
    val currentMonthRange = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        Pair(start, end)
    }

    val currentMonthTransactions = remember(transactions, currentMonthRange) {
        transactions.filter { it.transaction.timestamp in currentMonthRange.first..currentMonthRange.second }
    }

    val spendByCategory = remember(currentMonthTransactions) {
        currentMonthTransactions
            .filter { it.transaction.type == TransactionType.EXPENSE && it.transaction.categoryId != null }
            .groupBy { it.transaction.categoryId!! }
            .mapValues { entry -> entry.value.sumOf { it.transaction.amount } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("categories_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ScreenHeader(
                title = "Categories & Budgets",
                subtitle = "Create and organize income & expense categories",
                action = {
                    Button(
                        onClick = {
                            categoryToEdit = null
                            showCategoryDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("add_category_header_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Expenses & Budgets", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Income Sources", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCategories, key = { it.id }) { cat ->
                    val budget = budgets.find { it.categoryId == cat.id }
                    val spent = spendByCategory[cat.id] ?: 0.0
                    val limit = budget?.monthlyLimit ?: 0.0
                    val fraction = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1.5f) else 0f
                    val percent = if (limit > 0) ((spent / limit) * 100).toInt() else 0

                    val catColor = runCatching { Color(android.graphics.Color.parseColor(cat.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_card_${cat.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(catColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconUtils.getIcon(cat.iconName),
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Spent this month: ${DateUtils.formatCurrency(spent)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row {
                                    if (currentType == TransactionType.EXPENSE) {
                                        TextButton(onClick = { budgetCategoryToEdit = cat }) {
                                            Text(if (limit > 0) "Edit Budget" else "+ Budget")
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            categoryToEdit = cat
                                            showCategoryDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { categoryPendingDelete = cat },
                                        modifier = Modifier.size(32.dp).testTag("delete_category_btn_${cat.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Budget progress bar (if expense category has budget)
                            if (currentType == TransactionType.EXPENSE && limit > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${DateUtils.formatCurrency(spent)} of ${DateUtils.formatCurrency(limit)} ($percent%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (percent >= 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (percent >= 80) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                onTestBudgetNotification(
                                                    cat.name,
                                                    percent,
                                                    DateUtils.formatCurrency(spent),
                                                    DateUtils.formatCurrency(limit)
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = "Test alert",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (percent >= 100) "Over Budget!" else "Near Depletion!",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (percent >= 100) MaterialTheme.colorScheme.error else if (percent >= 80) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    // Add / Edit Category Dialog
    if (showCategoryDialog) {
        CategoryEditDialog(
            category = categoryToEdit,
            defaultType = currentType,
            onSave = { cat ->
                onSaveCategory(cat)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    // Edit Budget Dialog
    budgetCategoryToEdit?.let { cat ->
        val currentBudget = budgets.find { it.categoryId == cat.id }
        BudgetEditDialog(
            category = cat,
            budget = currentBudget,
            onSave = { b ->
                onSaveBudget(b)
                budgetCategoryToEdit = null
            },
            onDismiss = { budgetCategoryToEdit = null }
        )
    }

    // Delete Category Confirmation & Reassign Dialog
    categoryPendingDelete?.let { cat ->
        val usedTransactionsCount = transactions.count { it.transaction.categoryId == cat.id }
        val otherCategories = categories.filter { it.type == cat.type && it.id != cat.id }
        val currentBudget = budgets.find { it.categoryId == cat.id }

        DeleteCategoryConfirmDialog(
            category = cat,
            usedCount = usedTransactionsCount,
            otherCategories = otherCategories,
            budgetLimit = currentBudget?.monthlyLimit ?: 0.0,
            onDismiss = { categoryPendingDelete = null },
            onConfirmDelete = { targetCategoryId ->
                onDeleteCategory(cat, targetCategoryId)
                categoryPendingDelete = null
            }
        )
    }
}

@Composable
fun DeleteCategoryConfirmDialog(
    category: Category,
    usedCount: Int,
    otherCategories: List<Category>,
    budgetLimit: Double,
    onDismiss: () -> Unit,
    onConfirmDelete: (targetCategoryId: Long?) -> Unit
) {
    var selectedTargetId by remember(category.id, otherCategories) {
        mutableStateOf(otherCategories.firstOrNull()?.id)
    }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val selectedTargetCategory = otherCategories.find { it.id == selectedTargetId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (usedCount > 0) Icons.Default.SwapHoriz else Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (usedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (usedCount > 0) "Category In Use" else "Delete Category?",
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
                if (usedCount > 0) {
                    // Warning / Info Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "\"${category.name}\" is currently linked to $usedCount transaction(s).",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Text(
                        text = "To delete this category without losing recorded transactions, choose a category to move all existing transactions to, or cancel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (otherCategories.isNotEmpty()) {
                        Text(
                            text = "Move all recorded transactions to:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Interactive Dropdown Menu Box
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDropdownExpanded = true }
                                    .testTag("select_target_category_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (selectedTargetCategory != null) {
                                            val targetColor = runCatching {
                                                Color(android.graphics.Color.parseColor(selectedTargetCategory.colorHex))
                                            }.getOrDefault(MaterialTheme.colorScheme.primary)

                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(targetColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = IconUtils.getIcon(selectedTargetCategory.iconName),
                                                    contentDescription = null,
                                                    tint = targetColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = selectedTargetCategory.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Text(
                                                text = "Select a category",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                otherCategories.forEach { targetCat ->
                                    val color = runCatching {
                                        Color(android.graphics.Color.parseColor(targetCat.colorHex))
                                    }.getOrDefault(MaterialTheme.colorScheme.primary)

                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(color.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = IconUtils.getIcon(targetCat.iconName),
                                                        contentDescription = null,
                                                        tint = color,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(targetCat.name, fontWeight = FontWeight.Medium)
                                            }
                                        },
                                        onClick = {
                                            selectedTargetId = targetCat.id
                                            isDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("target_category_item_${targetCat.id}")
                                    )
                                }
                            }
                        }

                        if (budgetLimit > 0) {
                            Text(
                                text = "Note: The monthly budget limit for \"${category.name}\" will be removed.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "You do not have any other ${if (category.type == TransactionType.EXPENSE) "expense" else "income"} categories available to receive transactions. Please create another category first or cancel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else {
                    // Category is unused
                    Text(
                        text = "Are you sure you want to delete \"${category.name}\"?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (budgetLimit > 0) {
                            "This category has no recorded transactions. Its budget limit of ${DateUtils.formatCurrency(budgetLimit)} will also be deleted."
                        } else {
                            "This category has no recorded transactions and will be permanently removed."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (usedCount > 0) {
                Button(
                    onClick = { onConfirmDelete(selectedTargetId) },
                    enabled = selectedTargetId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_move_and_delete_category_btn")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Move & Delete")
                }
            } else {
                Button(
                    onClick = { onConfirmDelete(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_unused_category_btn")
                ) {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_category_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryEditDialog(
    category: Category?,
    defaultType: TransactionType,
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var colorHex by remember { mutableStateOf(category?.colorHex ?: "#10B981") }
    var iconKey by remember { mutableStateOf(category?.iconName ?: "restaurant") }
    var showIconPicker by remember { mutableStateOf(false) }

    val presetColors = listOf(
        "#10B981", "#3B82F6", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4", "#EC4899", "#84CC16"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category != null) "Edit Category" else "New Category") },
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
                    placeholder = { Text("e.g. Coffee, Streaming, Utilities") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cat_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary).copy(alpha = 0.2f))
                            .clickable { showIconPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconUtils.getIcon(iconKey),
                            contentDescription = null,
                            tint = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)
                        )
                    }

                    TextButton(onClick = { showIconPicker = true }) {
                        Text("Change Icon")
                    }
                }

                Text("Color Theme", style = MaterialTheme.typography.labelMedium)
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
                            id = category?.id ?: 0L,
                            name = name.trim(),
                            type = category?.type ?: defaultType,
                            iconName = iconKey,
                            colorHex = colorHex
                        )
                        onSave(cat)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_category_btn")
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

@Composable
fun BudgetEditDialog(
    category: Category,
    budget: Budget?,
    onSave: (Budget) -> Unit,
    onDismiss: () -> Unit
) {
    var limitValue by remember {
        mutableStateOf(budget?.monthlyLimit?.let { CurrencyUtils.toTextFieldValue(it) } ?: CurrencyUtils.toTextFieldValue(5000.0))
    }
    var thresholdText by remember { mutableStateOf(budget?.notifyThresholdPercent?.toString() ?: "80") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly Budget for ${category.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = limitValue,
                    onValueChange = { limitValue = CurrencyUtils.formatCurrencyInput(it, limitValue.text) },
                    label = { Text("Monthly Spending Limit (₱)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )

                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text("Alert Threshold (%)") },
                    supportingText = { Text("Trigger notification when spending reaches this %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_threshold_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = CurrencyUtils.parseCurrencyInput(limitValue)
                    val threshold = thresholdText.toIntOrNull() ?: 80
                    val b = Budget(
                        id = budget?.id ?: 0L,
                        categoryId = category.id,
                        monthlyLimit = limit,
                        notifyThresholdPercent = threshold
                    )
                    onSave(b)
                },
                modifier = Modifier.testTag("save_budget_btn")
            ) {
                Text("Set Budget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
