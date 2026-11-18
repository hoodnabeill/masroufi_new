package com.masroufi.app.feature.roll

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Which recurring items are installment plans: their roll rows get a link to أقساطي (the row's own tap stays "edit the entry"). */
@HiltViewModel
class InstallmentIdsViewModel @Inject constructor(dao: RecurringDao) : ViewModel() {
    val ids: StateFlow<Set<Long>> = dao.all().map { all -> all.filter { it.kind == RecurringKind.INSTALLMENT }.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}

class InstallmentLinks(val ids: Set<Long>, val onOpen: () -> Unit)

/** Null outside the roll screen (previews, tests): no link. */
val LocalInstallmentLinks = compositionLocalOf<InstallmentLinks?> { null }
