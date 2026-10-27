package com.masroufi.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.masroufi.app.data.model.WalletType

object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "restaurant" to Icons.Outlined.Restaurant,
        "cart" to Icons.Outlined.ShoppingCart,
        "car" to Icons.Outlined.DirectionsCar,
        "bolt" to Icons.Outlined.Bolt,
        "home" to Icons.Outlined.Home,
        "phone" to Icons.Outlined.PhoneAndroid,
        "health" to Icons.Outlined.LocalHospital,
        "school" to Icons.Outlined.School,
        "shopping" to Icons.Outlined.ShoppingBag,
        "movie" to Icons.Outlined.Movie,
        "family" to Icons.Outlined.FamilyRestroom,
        "gift" to Icons.Outlined.CardGiftcard,
        "charity" to Icons.Outlined.VolunteerActivism,
        "work" to Icons.Outlined.Work,
        "payments" to Icons.Outlined.Payments,
        "trending" to Icons.Outlined.ShowChart,
        "cafe" to Icons.Outlined.LocalCafe,
        "clothes" to Icons.Outlined.Checkroom,
        "travel" to Icons.Outlined.Flight,
        "pets" to Icons.Outlined.Pets,
        "games" to Icons.Outlined.SportsEsports,
        "savings" to Icons.Outlined.Savings,
        "category" to Icons.Outlined.Category,
    )

    fun of(key: String?): ImageVector = all[key] ?: Icons.Outlined.Category

    fun wallet(type: WalletType): ImageVector = when (type) {
        WalletType.CASH -> Icons.Outlined.Payments
        WalletType.BANK -> Icons.Outlined.AccountBalance
        WalletType.DIGITAL -> Icons.Outlined.PhoneAndroid
        WalletType.CUSTOM -> Icons.Outlined.AccountBalanceWallet
    }

    val walletGeneric: ImageVector get() = Icons.Outlined.AccountBalanceWallet
}
