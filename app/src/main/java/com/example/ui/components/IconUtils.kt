package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VolunteerActivism
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
        IconEntry("motorcycle", "Motorcycle", Icons.Default.TwoWheeler),
        IconEntry("local_gas_station", "Fuel", Icons.Default.LocalGasStation),
        IconEntry("receipt_long", "Bills & Utilities", Icons.Default.ReceiptLong),
        IconEntry("telephone", "Telephone / Mobile", Icons.Default.Phone),
        IconEntry("home", "Home / Housing", Icons.Default.Home),
        IconEntry("tools", "Tools / Hardware", Icons.Default.Build),
        IconEntry("family", "Family", Icons.Default.FamilyRestroom),
        IconEntry("grants", "Grants / Funding", Icons.Default.VolunteerActivism),
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
        IconEntry("other_expenses", "Other Expenses", Icons.Default.Receipt),
        IconEntry("more_horiz", "Other", Icons.Default.MoreHoriz)
    )

    private val iconMap: Map<String, ImageVector> = availableIcons.associate { it.key to it.icon }

    fun getIcon(name: String?): ImageVector {
        val key = name?.lowercase()?.trim() ?: ""
        return iconMap[key] ?: when (key) {
            "family", "family_restroom", "people" -> Icons.Default.FamilyRestroom
            "grants", "grant", "volunteer_activism", "aid", "scholarship" -> Icons.Default.VolunteerActivism
            "home", "house", "housing" -> Icons.Default.Home
            "motorcycle", "two_wheeler", "motor", "scooter", "bike" -> Icons.Default.TwoWheeler
            "other_expenses", "other", "receipt", "more_horiz" -> Icons.Default.Receipt
            "telephone", "phone", "call", "mobile", "telco" -> Icons.Default.Phone
            "tools", "tool", "build", "handyman", "hardware" -> Icons.Default.Build
            else -> iconMap[key] ?: Icons.Default.CreditCard
        }
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

    fun suggestIconForCategory(name: String): String {
        val lower = name.lowercase().trim()
        return when {
            lower.contains("motor") || lower.contains("scooter") || lower.contains("bike") || lower.contains("angkas") || lower.contains("joyride") -> "motorcycle"
            lower.contains("phone") || lower.contains("tele") || lower.contains("mobile") || lower.contains("load") || lower.contains("call") -> "telephone"
            lower.contains("tool") || lower.contains("hardware") || lower.contains("repair") || lower.contains("build") || lower.contains("fix") -> "tools"
            lower.contains("family") || lower.contains("child") || lower.contains("baby") || lower.contains("parent") || lower.contains("kid") -> "family"
            lower.contains("grant") || lower.contains("scholarship") || lower.contains("stipend") || lower.contains("subsidy") || lower.contains("aid") -> "grants"
            lower.contains("home") || lower.contains("house") || lower.contains("furniture") || lower.contains("rent") || lower.contains("condo") -> "home"
            lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") || lower.contains("eat") || lower.contains("cafe") -> "restaurant"
            lower.contains("grocery") || lower.contains("market") -> "shopping_cart"
            lower.contains("car") || lower.contains("transport") || lower.contains("taxi") || lower.contains("ride") -> "directions_car"
            lower.contains("gas") || lower.contains("fuel") -> "local_gas_station"
            lower.contains("bill") || lower.contains("utility") || lower.contains("electric") || lower.contains("water") -> "receipt_long"
            lower.contains("other") || lower.contains("misc") -> "other_expenses"
            else -> "shopping_bag"
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
