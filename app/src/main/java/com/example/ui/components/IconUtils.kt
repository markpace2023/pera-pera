package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.util.BankIconSearchService

object IconUtils {

    data class IconEntry(val key: String, val label: String, val icon: ImageVector)

    val availableIcons: List<IconEntry> = listOf(
        IconEntry("local_atm", "Cash", Icons.Default.LocalAtm),
        IconEntry("credit_card", "Card", Icons.Default.CreditCard),
        IconEntry("account_balance", "Bank / Savings", Icons.Default.AccountBalance),
        IconEntry("account_balance_wallet", "Digital Wallet", Icons.Default.AccountBalanceWallet),
        IconEntry("trending_up", "Investment", Icons.Default.TrendingUp),
        IconEntry("payments", "Payments", Icons.Default.Payments),
        IconEntry("attach_money", "Money", Icons.Default.AttachMoney),
        IconEntry("restaurant", "Food & Dining", Icons.Default.Restaurant),
        IconEntry("shopping_cart", "Groceries", Icons.Default.ShoppingCart),
        IconEntry("shopping_bag", "Shopping", Icons.Default.ShoppingBag),
        IconEntry("directions_car", "Transportation", Icons.Default.DirectionsCar),
        IconEntry("local_gas_station", "Fuel", Icons.Default.LocalGasStation),
        IconEntry("receipt_long", "Bills & Utilities", Icons.Default.ReceiptLong),
        IconEntry("home", "Housing / Rent", Icons.Default.Home),
        IconEntry("movie", "Entertainment", Icons.Default.Movie),
        IconEntry("medical_services", "Health & Medical", Icons.Default.MedicalServices),
        IconEntry("subscriptions", "Subscriptions", Icons.Default.Subscriptions),
        IconEntry("flight", "Travel", Icons.Default.Flight),
        IconEntry("school", "Education", Icons.Default.School),
        IconEntry("work", "Salary / Work", Icons.Default.Work),
        IconEntry("laptop", "Tech / Freelance", Icons.Default.Laptop),
        IconEntry("storefront", "Business / Store", Icons.Default.Storefront),
        IconEntry("card_giftcard", "Gift / Bonus", Icons.Default.CardGiftcard),
        IconEntry("fitness_center", "Fitness / Gym", Icons.Default.FitnessCenter),
        IconEntry("pets", "Pets", Icons.Default.Pets),
        IconEntry("handshake", "Loan / Debt / Lending", Icons.Default.Handshake),
        IconEntry("more_horiz", "Other", Icons.Default.MoreHoriz)
    )

    private val iconMap: Map<String, ImageVector> = availableIcons.associate { it.key to it.icon }

    fun getIcon(name: String?): ImageVector {
        return iconMap[name] ?: Icons.Default.CreditCard
    }

    fun suggestIconForAccount(name: String, typeString: String): String {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("cash") || lowerName.contains("wallet") -> "local_atm"
            lowerName.contains("visa") || lowerName.contains("mastercard") || lowerName.contains("credit") || typeString.contains("CREDIT") -> "credit_card"
            lowerName.contains("debit") -> "credit_card"
            lowerName.contains("gcash") || lowerName.contains("maya") || lowerName.contains("paypal") -> "account_balance_wallet"
            lowerName.contains("invest") || lowerName.contains("stock") || lowerName.contains("crypto") -> "trending_up"
            lowerName.contains("savings") || lowerName.contains("bank") || lowerName.contains("bdo") || lowerName.contains("bpi") || lowerName.contains("unionbank") -> "account_balance"
            else -> "account_balance"
        }
    }

    @Composable
    fun AccountIconView(
        iconName: String?,
        modifier: Modifier = Modifier,
        tint: Color = LocalContentColor.current,
        contentDescription: String? = null
    ) {
        val context = LocalContext.current
        val isLocal = BankIconSearchService.isLocalIcon(iconName)
        val localFile = if (isLocal) BankIconSearchService.getLocalIconFile(context, iconName) else null

        if (localFile != null && localFile.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(localFile)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = modifier.clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                imageVector = getIcon(iconName),
                contentDescription = contentDescription,
                tint = tint,
                modifier = modifier
            )
        }
    }
}
