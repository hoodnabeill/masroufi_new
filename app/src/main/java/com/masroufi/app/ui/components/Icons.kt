package com.masroufi.app.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.masroufi.app.data.model.WalletType

object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "restaurant" to MIcons.Food,
        "cart" to MIcons.Cart,
        "car" to MIcons.Car,
        "bolt" to MIcons.Bolt,
        "home" to MIcons.Home,
        "phone" to MIcons.Phone,
        "health" to MIcons.Health,
        "school" to MIcons.School,
        "shopping" to MIcons.Bag,
        "movie" to MIcons.Movie,
        "family" to MIcons.Family,
        "gift" to MIcons.Gift,
        "charity" to MIcons.Heart,
        "work" to MIcons.Work,
        "payments" to MIcons.Cash,
        "trending" to MIcons.Trending,
        "cafe" to MIcons.Cafe,
        "clothes" to MIcons.Shirt,
        "travel" to MIcons.Plane,
        "pets" to MIcons.Paw,
        "games" to MIcons.Games,
        "savings" to MIcons.Savings,
        "category" to MIcons.Shapes,
    )

    fun of(key: String?): ImageVector = all[key] ?: MIcons.Shapes

    fun wallet(type: WalletType): ImageVector = when (type) {
        WalletType.CASH -> MIcons.Cash
        WalletType.BANK -> MIcons.Bank
        WalletType.DIGITAL -> MIcons.Phone
        WalletType.CUSTOM -> MIcons.Wallet
    }

    val walletGeneric: ImageVector get() = MIcons.Wallet
}
