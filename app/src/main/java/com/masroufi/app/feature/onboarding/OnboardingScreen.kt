package com.masroufi.app.feature.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.data.model.Wallet
import com.masroufi.app.data.model.WalletType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.SegmentedChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val wallets: WalletRepository,
    private val clock: Clock,
) : ViewModel() {
    fun finish(currency: String, cashName: String, openingMinor: Long) {
        viewModelScope.launch {
            if (!wallets.hasAnyWallet()) {
                val now = clock.millis()
                wallets.save(Wallet(newId(), cashName, WalletType.CASH, currency, openingMinor, createdAt = now, updatedAt = now))
            }
            settings.setOnboardingDone(currency)
        }
    }
}

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    var currency by rememberSaveable { mutableStateOf(Money.DEFAULT_CURRENCY) }
    var opening by rememberSaveable { mutableStateOf("") }
    val language = LocalConfiguration.current.locales[0].language
    val cashName = stringResource(R.string.wallet_default_cash)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        IconBadge(Icons.Outlined.Savings, MaterialTheme.colorScheme.primary, size = 80.dp)
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        SegmentedChoice(
            options = listOf("ar", "en"),
            selected = if (language == "ar") "ar" else "en",
            onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
            label = { if (it == "ar") "العربية" else "English" },
        )
        CurrencyField(selected = currency, onSelect = { currency = it })
        AmountField(
            value = opening,
            onValueChange = { opening = it },
            currency = currency,
            label = stringResource(R.string.onboarding_cash_label),
        )
        Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.finish(currency, cashName, Money.parse(opening, currency) ?: 0L) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.onboarding_start)) }
    }
}
