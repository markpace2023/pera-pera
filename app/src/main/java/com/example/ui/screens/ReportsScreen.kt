package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.CustomDateRangePickerDialog
import com.example.ui.components.IconUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithDetails
import com.example.ui.components.DatabaseBackupDialog
import com.example.util.DatabaseBackupUtils
import com.example.util.DatabaseRestoreResult
import com.example.util.DateUtils
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(
    transactions: List<TransactionWithDetails>,
    onExportCsv: suspend (startTime: Long?, endTime: Long?) -> String,
    onImportCsv: suspend (csvContent: String) -> Int,
    onExportDatabaseBackup: (suspend () -> String)? = null,
    onRestoreDatabaseBackup: (suspend (String) -> DatabaseRestoreResult)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // CSV dialog states
    var showExportModal by remember { mutableStateOf(false) }
    var exportedCsvText by remember { mutableStateOf("") }
    var showImportModal by remember { mutableStateOf(false) }
    var importCsvInput by remember { mutableStateOf("") }
    var importResultMessage by remember { mutableStateOf<String?>(null) }
    var exportCsvSuccessNotice by remember { mutableStateOf<String?>(null) }
    var exportCsvErrorNotice by remember { mutableStateOf<String?>(null) }
    var selectedCsvFileName by remember { mutableStateOf<String?>(null) }
    var selectedCsvContent by remember { mutableStateOf<String?>(null) }

    // Full DB backup dialog states
    var showFullDbBackupDialog by remember { mutableStateOf(false) }
    var fullDbBackupTab by remember { mutableIntStateOf(0) }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = DatabaseBackupUtils.saveTextToUri(context, uri, exportedCsvText)
            if (success) {
                val (name, _) = DatabaseBackupUtils.getFileInfoFromUri(context, uri)
                exportCsvSuccessNotice = "CSV file saved successfully: $name"
                exportCsvErrorNotice = null
            } else {
                exportCsvErrorNotice = "Failed to save CSV file to chosen location."
            }
        }
    }

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val (name, _) = DatabaseBackupUtils.getFileInfoFromUri(context, uri)
                selectedCsvFileName = name
                val fileContent = DatabaseBackupUtils.readTextFromUri(context, uri)
                if (!fileContent.isNullOrBlank()) {
                    selectedCsvContent = fileContent
                    importCsvInput = fileContent
                    val count = onImportCsv(fileContent)
                    importResultMessage = "✓ Successfully imported $count transaction(s) from $name!"
                } else {
                    importResultMessage = "The selected CSV file was empty or could not be read."
                }
            }
        }
    }

    var timeframe by remember { mutableStateOf(DateUtils.Timeframe.MONTHLY) }
    var customStartDate by remember { mutableStateOf(DateUtils.getStartOfDay(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)) }
    var customEndDate by remember { mutableStateOf(DateUtils.getEndOfDay(System.currentTimeMillis())) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val (startTime, endTime) = remember(timeframe, customStartDate, customEndDate) {
        DateUtils.getTimeframeRange(
            timeframe = timeframe,
            customStart = customStartDate,
            customEnd = customEndDate
        )
    }

    val filteredList = remember(transactions, startTime, endTime) {
        transactions.filter { it.transaction.timestamp in startTime..endTime }
    }

    var selectedReportSection by remember { mutableStateOf("ALL") } // "ALL", "INCOME", "EXPENSE", "BACKUP"
    var incomeGroupingMode by remember { mutableStateOf("Category") } // "Category" or "Account"

    val totalExpense = remember(filteredList) {
        filteredList.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amount }
    }

    val totalIncome = remember(filteredList) {
        filteredList.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amount }
    }

    val incomeTransactions = remember(filteredList) {
        filteredList.filter { it.transaction.type == TransactionType.INCOME }
    }

    val categoryIncomes = remember(incomeTransactions, totalIncome) {
        incomeTransactions
            .groupBy { it.categoryName ?: "General Income" }
            .mapValues { entry -> entry.value.sumOf { it.transaction.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val accountIncomes = remember(incomeTransactions, totalIncome) {
        incomeTransactions
            .groupBy { it.accountName }
            .mapValues { entry -> entry.value.sumOf { it.transaction.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val incomeTransactionCount = remember(incomeTransactions) {
        incomeTransactions.size
    }

    val averageIncomePerDeposit = remember(totalIncome, incomeTransactionCount) {
        if (incomeTransactionCount > 0) totalIncome / incomeTransactionCount else 0.0
    }

    val topIncomeSource = remember(categoryIncomes, totalIncome) {
        if (categoryIncomes.isNotEmpty() && totalIncome > 0) {
            val top = categoryIncomes.first()
            val pct = ((top.second / totalIncome) * 100).toInt()
            "${top.first} ($pct%)"
        } else "None"
    }

    val categoryExpenses = remember(filteredList, totalExpense) {
        filteredList
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .groupBy { it.categoryName ?: "Uncategorized" }
            .mapValues { entry -> entry.value.sumOf { it.transaction.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val expenseChartColors = listOf(
        Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFFEC4899),
        Color(0xFF8B5CF6), Color(0xFF3B82F6), Color(0xFF06B6D4),
        Color(0xFF64748B), Color(0xFF10B981), Color(0xFF84CC16)
    )

    val incomeChartColors = listOf(
        Color(0xFF10B981), Color(0xFF0D9488), Color(0xFF06B6D4),
        Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6),
        Color(0xFF84CC16), Color(0xFFF59E0B), Color(0xFF059669)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Analytics & Reports",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track your income, expense distribution, and database records",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section Tabs Filter Chips: All, Income, Expenses, Backups
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All Analytics",
                    "INCOME" to "Income Report & Distribution",
                    "EXPENSE" to "Expense Distribution",
                    "BACKUP" to "Data & Backups"
                ).forEach { (key, label) ->
                    val isSelected = selectedReportSection == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedReportSection = key },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (key == "INCOME") Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = if (key == "INCOME") Color(0xFF047857) else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("reports_section_$key")
                    )
                }
            }
        }

        // Timeframe selector
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateUtils.Timeframe.values().forEach { tf ->
                        val isSelected = timeframe == tf
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                timeframe = tf
                                if (tf == DateUtils.Timeframe.CUSTOM) {
                                    showCustomDateDialog = true
                                }
                            },
                            label = { Text(tf.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("reports_tf_${tf.name}")
                        )
                    }
                }

                if (timeframe == DateUtils.Timeframe.CUSTOM) {
                    Surface(
                        onClick = { showCustomDateDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reports_custom_date_range_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Edit Custom Range",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Custom Range Selected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${DateUtils.formatDate(startTime)} – ${DateUtils.formatDate(endTime)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Summary Card (Overview of Inflow, Outflow, Net Cash Flow, Savings Rate)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().testTag("reports_summary_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Period Overview (${DateUtils.formatDate(startTime)} - ${DateUtils.formatDate(endTime)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (totalIncome > 0) {
                            val savingsRate = (((totalIncome - totalExpense) / totalIncome) * 100).toInt()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (savingsRate >= 0) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Savings Rate: $savingsRate%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (savingsRate >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Inflow (Income)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = DateUtils.formatCurrency(totalIncome),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Outflow (Expense)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = DateUtils.formatCurrency(totalExpense),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Net Cash Flow row
                    val net = totalIncome - totalExpense
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Net Cash Flow",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (net >= 0) "Surplus" else "Deficit",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (net >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }

                        Text(
                            text = (if (net > 0) "+" else "") + DateUtils.formatCurrency(net),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (net >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                }
            }
        }

        // ==========================================
        // INCOME DISTRIBUTION & REPORT SECTION
        // ==========================================
        if (selectedReportSection == "ALL" || selectedReportSection == "INCOME") {
            // 1. Income Distribution Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().testTag("income_distribution_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Income Distribution",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Breakdown by $incomeGroupingMode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Toggle between Category and Account grouping
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(2.dp)
                            ) {
                                val isCategory = incomeGroupingMode == "Category"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCategory) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { incomeGroupingMode = "Category" }
                                        .testTag("income_group_category_btn")
                                ) {
                                    Text(
                                        text = "Category",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isCategory) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCategory) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (!isCategory) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { incomeGroupingMode = "Account" }
                                        .testTag("income_group_account_btn")
                                ) {
                                    Text(
                                        text = "Account",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (!isCategory) FontWeight.Bold else FontWeight.Normal,
                                        color = if (!isCategory) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val activeIncomeList = if (incomeGroupingMode == "Category") categoryIncomes else accountIncomes

                        if (activeIncomeList.isEmpty() || totalIncome <= 0.0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No income records in this period.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Log income transactions (e.g. Salary, Freelance, Business) to view your distribution.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        } else {
                            // Stacked horizontal proportion bar for income
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    var currentLeft = 0f
                                    activeIncomeList.forEachIndexed { index, pair ->
                                        val color = incomeChartColors[index % incomeChartColors.size]
                                        val fraction = if (totalIncome > 0) (pair.second / totalIncome).toFloat() else 0f
                                        val itemWidth = size.width * fraction
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(currentLeft, 0f),
                                            size = Size(itemWidth, size.height)
                                        )
                                        currentLeft += itemWidth
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Breakdown items
                            activeIncomeList.forEachIndexed { index, (name, amount) ->
                                val color = incomeChartColors[index % incomeChartColors.size]
                                val percent = if (totalIncome > 0) ((amount / totalIncome) * 100).toInt() else 0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$percent%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = DateUtils.formatCurrency(amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Income Report & Performance Metrics Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().testTag("income_report_insights_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Income Report & Insights",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Deposit metrics and source performance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4-Card Performance Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Total Inflow
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.1f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Total Inflow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = DateUtils.formatCurrency(totalIncome),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }

                            // Deposit count
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Total Deposits",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$incomeTransactionCount",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Average Deposit
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Avg per Deposit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = DateUtils.formatCurrency(averageIncomePerDeposit),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Top Income Source
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Top Source",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = topIncomeSource,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Detailed list of income transactions in this period
                        if (incomeTransactions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Recorded Inflow Transactions (${incomeTransactions.size}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                incomeTransactions.take(8).forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                IconUtils.AccountIconView(
                                                    iconName = item.categoryIcon ?: item.accountIcon,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = Color(0xFF10B981)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = item.transaction.title.ifBlank { item.categoryName ?: "Income" },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "${item.accountName} • ${DateUtils.formatDate(item.transaction.timestamp)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "+${DateUtils.formatCurrency(item.transaction.amount)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }
                                }
                                if (incomeTransactions.size > 8) {
                                    Text(
                                        text = "+ ${incomeTransactions.size - 8} more income record(s) in this period",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // EXPENSE DISTRIBUTION SECTION
        // ==========================================
        if (selectedReportSection == "ALL" || selectedReportSection == "EXPENSE") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().testTag("expense_distribution_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Expense Distribution by Category",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Outflow breakdown for selected period",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (categoryExpenses.isEmpty()) {
                            Text(
                                text = "No expenses recorded in this period.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Stacked horizontal proportion bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    var currentLeft = 0f
                                    categoryExpenses.forEachIndexed { index, pair ->
                                        val color = expenseChartColors[index % expenseChartColors.size]
                                        val fraction = if (totalExpense > 0) (pair.second / totalExpense).toFloat() else 0f
                                        val itemWidth = size.width * fraction
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(currentLeft, 0f),
                                            size = Size(itemWidth, size.height)
                                        )
                                        currentLeft += itemWidth
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Category items
                            categoryExpenses.forEachIndexed { index, (catName, amount) ->
                                val color = expenseChartColors[index % expenseChartColors.size]
                                val percent = if (totalExpense > 0) ((amount / totalExpense) * 100).toInt() else 0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = catName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$percent%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = DateUtils.formatCurrency(amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // DATA & BACKUP SECTION
        // ==========================================
        // if (selectedReportSection == "ALL" || selectedReportSection == "BACKUP") {

        // // Full Database Backup & Restore Card (User requirement: "make the backup to backup the whole database")
        // if (onExportDatabaseBackup != null && onRestoreDatabaseBackup != null) {
        //     item {
        //         Card(
        //             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        //             shape = RoundedCornerShape(20.dp),
        //             modifier = Modifier.fillMaxWidth()
        //         ) {
        //             Column(modifier = Modifier.padding(18.dp)) {
        //                 Row(verticalAlignment = Alignment.CenterVertically) {
        //                     Icon(
        //                         imageVector = Icons.Default.Backup,
        //                         contentDescription = null,
        //                         tint = MaterialTheme.colorScheme.primary,
        //                         modifier = Modifier.size(24.dp)
        //                     )
        //                     Spacer(modifier = Modifier.width(8.dp))
        //                     Text(
        //                         text = "SQLite Database Backup & Restore",
        //                         style = MaterialTheme.typography.titleMedium,
        //                         fontWeight = FontWeight.Bold
        //                     )
        //                 }
        //                 Text(
        //                     text = "Backup your entire SQLite database: all accounts, categories, transactions, and budgets. Restore anytime.",
        //                     style = MaterialTheme.typography.bodySmall,
        //                     color = MaterialTheme.colorScheme.onSurfaceVariant,
        //                     modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        //                 )

        //                 Row(
        //                     modifier = Modifier.fillMaxWidth(),
        //                     horizontalArrangement = Arrangement.spacedBy(10.dp)
        //                 ) {
        //                     Button(
        //                         onClick = {
        //                             fullDbBackupTab = 0
        //                             showFullDbBackupDialog = true
        //                         },
        //                         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        //                         modifier = Modifier.weight(1f).testTag("reports_backup_full_db_btn")
        //                     ) {
        //                         Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
        //                         Spacer(modifier = Modifier.width(6.dp))
        //                         Text("SQLite Backup")
        //                     }

        //                     OutlinedButton(
        //                         onClick = {
        //                             fullDbBackupTab = 1
        //                             showFullDbBackupDialog = true
        //                         },
        //                         modifier = Modifier.weight(1f).testTag("reports_restore_full_db_btn")
        //                     ) {
        //                         Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
        //                         Spacer(modifier = Modifier.width(6.dp))
        //                         Text("Restore SQLite")
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }

        // // CSV Backup & Restore Action Card (User requirement: "export selected data into csv as a backup and also import from csv to restore backup")
        // item {
        //     Card(
        //         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        //         shape = RoundedCornerShape(20.dp),
        //         modifier = Modifier.fillMaxWidth()
        //     ) {
        //         Column(modifier = Modifier.padding(18.dp)) {
        //             Text(
        //                 text = "Data Backup & Restore (CSV)",
        //                 style = MaterialTheme.typography.titleMedium,
        //                 fontWeight = FontWeight.Bold
        //             )
        //             Text(
        //                 text = "Export your records for backup or import CSV data from other sources.",
        //                 style = MaterialTheme.typography.bodySmall,
        //                 color = MaterialTheme.colorScheme.onSurfaceVariant,
        //                 modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        //             )

        //             Row(
        //                 modifier = Modifier.fillMaxWidth(),
        //                 horizontalArrangement = Arrangement.spacedBy(10.dp)
        //             ) {
        //                 Button(
        //                     onClick = {
        //                         coroutineScope.launch {
        //                             exportedCsvText = onExportCsv(startTime, endTime)
        //                             showExportModal = true
        //                         }
        //                     },
        //                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        //                     modifier = Modifier
        //                         .weight(1f)
        //                         .testTag("export_csv_btn")
        //                 ) {
        //                     Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
        //                     Spacer(modifier = Modifier.width(6.dp))
        //                     Text("Export CSV")
        //                 }

        //                 OutlinedButton(
        //                     onClick = {
        //                         importResultMessage = null
        //                         showImportModal = true
        //                     },
        //                     modifier = Modifier
        //                         .weight(1f)
        //                         .testTag("import_csv_btn")
        //                 ) {
        //                     Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
        //                     Spacer(modifier = Modifier.width(6.dp))
        //                     Text("Import CSV")
        //                 }
        //             }
        //         }
        //     }
        // }
        // }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    // Export Modal Dialog
    /*if (showExportModal) {
        AlertDialog(
            onDismissRequest = {
                showExportModal = false
                exportCsvSuccessNotice = null
                exportCsvErrorNotice = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export CSV Backup File")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Create a CSV file containing your filtered transaction records, or share directly to your files or apps:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (exportCsvSuccessNotice != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = exportCsvSuccessNotice!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val defaultName = DatabaseBackupUtils.generateDefaultCsvFileName()
                            try {
                                createCsvLauncher.launch(defaultName)
                            } catch (_: Exception) {
                                DatabaseBackupUtils.shareCsvFile(context, exportedCsvText, defaultName)
                                exportCsvSuccessNotice = "CSV prepared and ready to share/save!"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_csv_file_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save CSV File (.csv)")
                    }

                    OutlinedButton(
                        onClick = {
                            DatabaseBackupUtils.shareCsvFile(context, exportedCsvText)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("share_csv_file_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share CSV File")
                    }

                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(exportedCsvText))
                            exportCsvSuccessNotice = "Copied CSV to clipboard!"
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("copy_csv_clipboard_btn")
                    ) {
                        Text("Or copy plain text to clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportModal = false
                        exportCsvSuccessNotice = null
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }*/

    // Import Modal Dialog
//     if (showImportModal) {
//         AlertDialog(
//             onDismissRequest = { showImportModal = false },
//             title = {
//                 Row(verticalAlignment = Alignment.CenterVertically) {
//                     Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
//                     Spacer(modifier = Modifier.width(8.dp))
//                     Text("Import & Restore from CSV")
//                 }
//             },
//             text = {
//                 Column(
//                     modifier = Modifier
//                         .fillMaxWidth()
//                         .heightIn(max = 440.dp)
//                         .verticalScroll(rememberScrollState()),
//                     verticalArrangement = Arrangement.spacedBy(10.dp)
//                 ) {
//                     Text(
//                         text = "Select a CSV backup file from your device, or paste CSV lines to restore:",
//                         style = MaterialTheme.typography.bodySmall,
//                         color = MaterialTheme.colorScheme.onSurfaceVariant
//                     )

//                     Button(
//                         onClick = {
//                             try {
//                                 openCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
//                             } catch (_: Exception) {
//                                 importResultMessage = "No file manager available on this device. Please paste CSV text below."
//                             }
//                         },
//                         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
//                         modifier = Modifier.fillMaxWidth().testTag("choose_csv_file_btn")
//                     ) {
//                         Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
//                         Spacer(modifier = Modifier.width(6.dp))
//                         Text("Select CSV File (.csv)")
//                     }

//                     if (selectedCsvFileName != null) {
//                         Text(
//                             text = "Selected file: $selectedCsvFileName",
//                             style = MaterialTheme.typography.bodySmall,
//                             fontWeight = FontWeight.Bold,
//                             color = MaterialTheme.colorScheme.primary
//                         )
//                     }

//                     importResultMessage?.let { msg ->
//                         Card(
//                             colors = CardDefaults.cardColors(
//                                 containerColor = if (msg.startsWith("✓")) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
//                             ),
//                             shape = RoundedCornerShape(10.dp),
//                             modifier = Modifier.fillMaxWidth()
//                         ) {
//                             Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
//                                 Icon(
//                                     if (msg.startsWith("✓")) Icons.Default.CheckCircle else Icons.Default.Warning,
//                                     contentDescription = null,
//                                     tint = if (msg.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
//                                     modifier = Modifier.size(18.dp)
//                                 )
//                                 Spacer(modifier = Modifier.width(6.dp))
//                                 Text(
//                                     text = msg,
//                                     style = MaterialTheme.typography.bodySmall,
//                                     fontWeight = FontWeight.Medium
//                                 )
//                             }
//                         }
//                     }

//                     Text(
//                         text = "Or paste raw CSV text:",
//                         style = MaterialTheme.typography.labelSmall,
//                         color = MaterialTheme.colorScheme.onSurfaceVariant
//                     )

//                     OutlinedTextField(
//                         value = importCsvInput,
//                         onValueChange = { importCsvInput = it },
//                         placeholder = {
//                             Text("Paste CSV data here:\nid,date,type,title,amount,account,category...\n...")
//                         },
//                         maxLines = 4,
//                         modifier = Modifier
//                             .fillMaxWidth()
//                             .height(100.dp)
//                             .testTag("import_csv_textarea")
//                     )

//                     Row(
//                         modifier = Modifier.fillMaxWidth(),
//                         horizontalArrangement = Arrangement.SpaceBetween,
//                         verticalAlignment = Alignment.CenterVertically
//                     ) {
//                         TextButton(
//                             onClick = {
//                                 importCsvInput = """
// id,date,type,title,amount,account,category,to_account,fee,note,is_recurring,frequency
// 1,2026-10-24 10:00:00,EXPENSE,Grocery Shopping,85.50,Cash Wallet,Groceries,,0.00,Weekly food,false,NONE
// 2,2026-10-24 12:30:00,EXPENSE,Online Subscription,15.99,UnionBank Visa CC,Subscriptions,,0.00,Streaming service,true,MONTHLY
// 3,2026-10-24 15:00:00,INCOME,Client Payment,650.00,Main Savings,Freelance,,0.00,Design project,false,NONE
//                                 """.trimIndent()
//                             }
//                         ) {
//                             Text("Sample Template")
//                         }

//                         Button(
//                             onClick = {
//                                 if (importCsvInput.isNotBlank()) {
//                                     coroutineScope.launch {
//                                         val count = onImportCsv(importCsvInput)
//                                         importResultMessage = "✓ Successfully imported $count transaction(s)!"
//                                     }
//                                 }
//                             },
//                             enabled = importCsvInput.isNotBlank(),
//                             modifier = Modifier.testTag("process_import_csv_btn")
//                         ) {
//                             Text("Import Text")
//                         }
//                     }
//                 }
//             },
//             confirmButton = {
//                 TextButton(onClick = { showImportModal = false }) {
//                     Text("Close")
//                 }
//             }
//         )
//     }

    /* if (showFullDbBackupDialog && onExportDatabaseBackup != null && onRestoreDatabaseBackup != null) {
        DatabaseBackupDialog(
            initialTab = fullDbBackupTab,
            onExportBackup = onExportDatabaseBackup,
            onRestoreBackup = onRestoreDatabaseBackup,
            onDismissRequest = { showFullDbBackupDialog = false }
        )
    } */

    if (showCustomDateDialog) {
        CustomDateRangePickerDialog(
            initialStartDate = customStartDate,
            initialEndDate = customEndDate,
            onDateRangeSelected = { start, end ->
                customStartDate = start
                customEndDate = end
                showCustomDateDialog = false
            },
            onDismissRequest = { showCustomDateDialog = false }
        )
    }
}
