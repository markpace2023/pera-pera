package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.TransactionType

@Composable
fun CategoryAddDialog(
    defaultType: TransactionType,
    initialName: String = "",
    initialIconKey: String = "shopping_cart",
    initialColorHex: String = "#10B981",
    initialBudgetGroup: com.example.data.model.BudgetGroup = com.example.data.model.BudgetGroup.NEEDS,
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var iconKey by remember(initialIconKey) { mutableStateOf(initialIconKey) }
    var colorHex by remember(initialColorHex) { mutableStateOf(initialColorHex) }
    var selectedBudgetGroup by remember(initialBudgetGroup) { mutableStateOf(initialBudgetGroup) }
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
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_category_name_input")
                )

                // Budget Group Selection (50/30/20) for Expense categories
                if (defaultType == TransactionType.EXPENSE) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "50/30/20 Budget Group",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val groups = listOf(
                                com.example.data.model.BudgetGroup.NEEDS,
                                com.example.data.model.BudgetGroup.WANTS,
                                com.example.data.model.BudgetGroup.SAVINGS_DEBT
                            )
                            groups.forEach { group ->
                                val isSelected = selectedBudgetGroup == group
                                val chipLabel = when (group) {
                                    com.example.data.model.BudgetGroup.NEEDS -> "Needs (50%)"
                                    com.example.data.model.BudgetGroup.WANTS -> "Wants (30%)"
                                    com.example.data.model.BudgetGroup.SAVINGS_DEBT -> "Savings/Debt (20%)"
                                    else -> group.displayName
                                }
                                androidx.compose.material3.FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedBudgetGroup = group },
                                    label = { Text(chipLabel, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            text = when (selectedBudgetGroup) {
                                com.example.data.model.BudgetGroup.NEEDS -> "Essential living costs (groceries, bills, rent, medicine, transport)."
                                com.example.data.model.BudgetGroup.WANTS -> "Lifestyle spending (dining out, shopping, hobbies, streaming)."
                                com.example.data.model.BudgetGroup.SAVINGS_DEBT -> "Debt payments, savings allocation, emergency funds."
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                        TextButton(
                            onClick = { showIconPicker = true },
                            modifier = Modifier.testTag("dialog_choose_icon_button")
                        ) {
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
                            colorHex = colorHex,
                            budgetGroup = if (defaultType == TransactionType.EXPENSE) selectedBudgetGroup else com.example.data.model.BudgetGroup.NONE
                        )
                        onSave(cat)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("dialog_save_category_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_category_btn")
            ) {
                Text("Cancel")
            }
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
