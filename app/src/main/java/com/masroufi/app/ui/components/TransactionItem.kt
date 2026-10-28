package com.masroufi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.ui.theme.LocalFinanceColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun categoryLabel(name: String?, systemKey: String?): String {
    val res = systemKey?.let { systemCategoryLabel(it) }
    return when {
        res != null -> stringResource(res)
        !name.isNullOrBlank() -> name
        else -> stringResource(R.string.category_uncategorized)
    }
}

@Composable
fun categoryLabel(category: Category?): String = categoryLabel(category?.name, category?.systemKey)

fun systemCategoryLabel(key: String): Int? = when (key) {
    "food" -> R.string.cat_food
    "groceries" -> R.string.cat_groceries
    "transport" -> R.string.cat_transport
    "bills" -> R.string.cat_bills
    "rent" -> R.string.cat_rent
    "mobile" -> R.string.cat_mobile
    "health" -> R.string.cat_health
    "education" -> R.string.cat_education
    "shopping" -> R.string.cat_shopping
    "entertainment" -> R.string.cat_entertainment
    "family" -> R.string.cat_family
    "gifts" -> R.string.cat_gifts
    "charity" -> R.string.cat_charity
    "other_expense" -> R.string.cat_other_expense
    "salary" -> R.string.cat_salary
    "freelance" -> R.string.cat_freelance
    "business" -> R.string.cat_business
    "gift_income" -> R.string.cat_gift_income
    "other_income" -> R.string.cat_other_income
    else -> null
}

@Composable
fun rememberDateFormatter(style: FormatStyle = FormatStyle.MEDIUM): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, style) { DateTimeFormatter.ofLocalizedDate(style).withLocale(locale) }
}

@Composable
fun TransactionItem(row: TransactionRow, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = true) {
    val tx = row.transaction
    val isTransfer = tx.type == TransactionType.TRANSFER
    val dateFormatter = rememberDateFormatter()
    val title = when {
        isTransfer -> stringResource(R.string.type_transfer)
        else -> categoryLabel(row.categoryName, row.categorySystemKey)
    }
    val subtitle = buildList {
        if (tx.note.isNotBlank()) add(tx.note)
        if (isTransfer) add("${row.walletName.orEmpty()} → ${row.toWalletName.orEmpty()}") else row.walletName?.let(::add)
        if (showDate) add(tx.occurredAt.toLocalDate().format(dateFormatter))
    }.joinToString(" · ")
    val color = when (tx.type) {
        TransactionType.INCOME -> LocalFinanceColors.current.income
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        TransactionType.TRANSFER -> LocalFinanceColors.current.transfer
    }
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = if (isTransfer) MIcons.Transfer else CategoryIcons.of(row.categoryIcon),
            tint = if (isTransfer) LocalFinanceColors.current.transfer else categoryColor(row.categoryColor),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val sign = when (tx.type) {
            TransactionType.INCOME -> 1L
            TransactionType.EXPENSE -> -1L
            TransactionType.TRANSFER -> 0L
        }
        MoneyText(
            minor = if (sign == 0L) tx.amountMinor else tx.amountMinor * sign,
            currency = tx.currency,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            signed = sign > 0,
        )
    }
}
