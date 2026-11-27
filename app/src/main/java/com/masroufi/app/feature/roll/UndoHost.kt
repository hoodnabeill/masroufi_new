package com.masroufi.app.feature.roll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.ui.components.UndoBar
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The undo bar for every delete but entries (the roll has its own): one bar, over whatever screen the user is on. */
@HiltViewModel
class UndoViewModel @Inject constructor(private val deletes: PendingDeletes) : ViewModel() {
    val visible: StateFlow<Boolean> = deletes.waiting(entries = false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Every real delete that is not an entry: the host plays the Heavy haptic. */
    val committed = deletes.committed.filter { it != DeleteKind.ENTRY }

    fun undo() {
        deletes.undoNewest(entries = false)
    }
}

@Composable
fun UndoHost(viewModel: UndoViewModel = hiltViewModel()) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(viewModel) { viewModel.committed.collect { haptics.perform(HapticLevel.Heavy) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        UndoBar(visible, viewModel::undo, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    }
}
