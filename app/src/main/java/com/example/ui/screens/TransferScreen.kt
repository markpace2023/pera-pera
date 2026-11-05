package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.model.AccountType
import com.example.ui.components.IconUtils
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@Composable
fun TransferScreen(
    accounts: List<Account>,
    initialFromAccountId: Long? = null,
    initialToAccountId: Long? = null,
    onExecuteTransfer: (fromId: Long, toId: Long, amount: Double, fee: Double, note: String) -> Unit,
    onBack: () -> Unit
) {
    if (accounts.size < 2) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "At least 2 accounts required for transfer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please create at least two accounts (e.g. Bank, Savings, or Card) first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("Go Back")
            }
        }
        return
    }

    var toAccountId by remember {
        val targetTo = initialToAccountId ?: run {
            val fromCandidate = initialFromAccountId ?: accounts.first().id
            val other = accounts.filter { it.id != fromCandidate }
            other.firstOrNull()?.id ?: accounts.getOrNull(1)?.id ?: accounts.first().id
        }
        mutableLongStateOf(targetTo)
    }

    var fromAccountId by remember {
        val targetFrom = if (initialFromAccountId != null) {
            initialFromAccountId
        } else if (initialToAccountId != null) {
            val nonCredit = accounts.firstOrNull { it.id != initialToAccountId && it.type != AccountType.CREDIT_CARD }
            nonCredit?.id ?: accounts.firstOrNull { it.id != initialToAccountId }?.id ?: accounts.first().id
        } else {
            accounts.first().id
        }
        mutableLongStateOf(targetFrom)
    }

    var amountValue by remember { mutableStateOf(TextFieldValue("")) }
    var feeValue by remember { mutableStateOf(TextFieldValue("")) }
    var noteText by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }

    var isFromDropdownOpen by remember { mutableStateOf(false) }
    var isToDropdownOpen by remember { mutableStateOf(false) }

    val fromAccount = accounts.find { it.id == fromAccountId } ?: accounts.first()
    val toAccount = accounts.find { it.id == toAccountId } ?: accounts[1]

    val isCcPayment = toAccount.type == AccountType.CREDIT_CARD

    val amount = CurrencyUtils.parseCurrencyInput(amountValue)
    val fee = CurrencyUtils.parseCurrencyInput(feeValue)

    // Preview new balances
    val fromNewBalance = if (fromAccount.type == AccountType.CREDIT_CARD) {
        fromAccount.balance + amount + fee
    } else {
        fromAccount.balance - amount - fee
    }

    val toNewBalance = if (toAccount.type == AccountType.CREDIT_CARD) {
        toAccount.balance - amount // paying off credit card
    } else {
        toAccount.balance + amount
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("transfer_screen")
    ) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("transfer_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCcPayment) "Pay Credit Card" else "Transfer Funds",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSuccess) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isCcPayment) "Payment Successful!" else "Transfer Successful!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (isCcPayment) {
                            "${DateUtils.formatCurrency(amount, fromAccount.currency)} paid to ${toAccount.name} from ${fromAccount.name}"
                        } else {
                            "${DateUtils.formatCurrency(amount, fromAccount.currency)} transferred from ${fromAccount.name} to ${toAccount.name}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Done")
                    }
                }
            }
            return
        }

        // Account Transfer Visual Cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // FROM
                Text(
                    text = if (isCcPayment) "Pay From (Funding Account)" else "Transfer From",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isFromDropdownOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_from_select_btn")
                    ) {
                        IconUtils.AccountIconView(fromAccount.iconName, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                            Text(fromAccount.name, fontWeight = FontWeight.Bold)
                            Text("Balance: ${DateUtils.formatCurrency(fromAccount.balance, fromAccount.currency)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = isFromDropdownOpen,
                        onDismissRequest = { isFromDropdownOpen = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                leadingIcon = { IconUtils.AccountIconView(acc.iconName, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("${acc.name} (${DateUtils.formatCurrency(acc.balance, acc.currency)})") },
                                onClick = {
                                    fromAccountId = acc.id
                                    if (toAccountId == acc.id) {
                                        val other = accounts.filter { it.id != acc.id }
                                        toAccountId = other.first().id
                                    }
                                    isFromDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Swap Icon
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                val temp = fromAccountId
                                fromAccountId = toAccountId
                                toAccountId = temp
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap Accounts",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TO
                Text(
                    text = if (isCcPayment) "Pay To (Credit Card)" else "Transfer To",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isToDropdownOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_to_select_btn")
                    ) {
                        Icon(IconUtils.getIcon(toAccount.iconName), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                            Text(toAccount.name, fontWeight = FontWeight.Bold)
                            Text("Balance: ${DateUtils.formatCurrency(toAccount.balance, toAccount.currency)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = isToDropdownOpen,
                        onDismissRequest = { isToDropdownOpen = false }
                    ) {
                        accounts.filter { it.id != fromAccountId }.forEach { acc ->
                            DropdownMenuItem(
                                leadingIcon = { IconUtils.AccountIconView(acc.iconName, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("${acc.name} (${DateUtils.formatCurrency(acc.balance, acc.currency)})") },
                                onClick = {
                                    toAccountId = acc.id
                                    isToDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transfer Amount & Fee Inputs
        OutlinedTextField(
            value = amountValue,
            onValueChange = { amountValue = CurrencyUtils.formatCurrencyInput(it, amountValue.text) },
            label = { Text(if (isCcPayment) "Payment Amount *" else "Amount to Transfer *") },
            placeholder = { Text("0.00") },
            prefix = { Text(if (fromAccount.currency == "PHP") "₱" else "$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transfer_amount_input")
        )

        if (isCcPayment && toAccount.balance > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AssistChip(
                    onClick = {
                        amountValue = CurrencyUtils.toTextFieldValue(toAccount.balance)
                    },
                    label = {
                        Text("Pay Full Balance: ${DateUtils.formatCurrency(toAccount.balance, toAccount.currency)}")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = feeValue,
            onValueChange = { feeValue = CurrencyUtils.formatCurrencyInput(it, feeValue.text) },
            label = { Text("Transfer Fee (Optional)") },
            placeholder = { Text("0.00") },
            prefix = { Text(if (fromAccount.currency == "PHP") "₱" else "$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Note / Purpose (Optional)") },
            placeholder = { Text(if (isCcPayment) "e.g. Credit card monthly payment" else "e.g. Savings deposit or transfer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (amount > 0) {
            Spacer(modifier = Modifier.height(16.dp))

            // Real-time Balance Preview Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isCcPayment) "Balance Preview After Payment" else "Balance Preview After Transfer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${fromAccount.name}:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateUtils.formatCurrency(fromNewBalance, fromAccount.currency),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${toAccount.name}:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            DateUtils.formatCurrency(toNewBalance, toAccount.currency),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (amount > 0) {
                    onExecuteTransfer(fromAccountId, toAccountId, amount, fee, noteText)
                    isSuccess = true
                }
            },
            enabled = amount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = if (isCcPayment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("confirm_transfer_button")
        ) {
            Icon(if (isCcPayment) Icons.Default.Payments else Icons.Default.SwapHoriz, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isCcPayment) "Confirm Payment" else "Confirm Transfer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}
