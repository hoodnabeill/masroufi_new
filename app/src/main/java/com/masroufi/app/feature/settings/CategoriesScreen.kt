package com.masroufi.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassDialog
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.core.newId
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.CategoryKind
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.feature.transactions.typeLabel
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.ChipChoice
import com.masroufi.app.ui.components.ColorPicker
import com.masroufi.app.ui.components.DropdownField
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repo: CategoryRepository, private val clock: Clock) : ViewModel() {
    val categories: StateFlow<List<Category>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(existing: Category?, name: String, kind: CategoryKind, parentId: String?, icon: String, color: Int, renamed: Boolean) {
        viewModelScope.launch {
            runCatching {
                repo.save(
                    (existing ?: Category(id = newId(), name = name, kind = kind, updatedAt = clock.millis(), sortOrder = 100))
                        // Renaming a built-in category detaches it from its translated label.
                        .copy(name = name, kind = kind, parentId = parentId, iconKey = icon, colorIndex = color, systemKey = if (renamed) null else existing?.systemKey),
                )
            }
        }
    }

    fun delete(category: Category) {
        viewModelScope.launch { repo.delete(category.id) }
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.categories_title), onBack = onBack) { GlassIconButton(onClick = { creating = true }) { Icon(MIcons.Plus, stringResource(R.string.category_add)) } } },
    ) { padding ->
        val roots = categories.filter { it.parentId == null }.sortedBy { it.kind }
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
            items(roots, key = { it.id }) { root ->
                Column {
                    CategoryRow(root, indent = false) { editing = root }
                    categories.filter { it.parentId == root.id }.forEach { child -> CategoryRow(child, indent = true) { editing = child } }
                }
            }
        }
    }
    if (creating || editing != null) {
        CategoryDialog(
            existing = editing,
            parents = categories.filter { it.parentId == null && it.id != editing?.id },
            onDismiss = { creating = false; editing = null },
            onSave = { name, kind, parent, icon, color, renamed -> viewModel.save(editing, name, kind, parent, icon, color, renamed); creating = false; editing = null },
            onDelete = editing?.let { e -> { viewModel.delete(e); editing = null } },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, indent: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(start = if (indent) 32.dp else 0.dp),
        leadingContent = { IconBadge(CategoryIcons.of(category.iconKey), categoryColor(category.colorIndex), size = 36.dp) },
        headlineContent = { Text(categoryLabel(category)) },
        supportingContent = { Text(typeLabel(if (category.kind == CategoryKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE)) },
    )
}

@Composable
private fun CategoryDialog(
    existing: Category?,
    parents: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, CategoryKind, String?, String, Int, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val existingLabel = existing?.let { categoryLabel(it) }.orEmpty()
    var name by rememberSaveable { mutableStateOf(existingLabel) }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: CategoryKind.EXPENSE) }
    var parentId by rememberSaveable { mutableStateOf(existing?.parentId) }
    var icon by rememberSaveable { mutableStateOf(existing?.iconKey ?: "category") }
    var color by rememberSaveable { mutableStateOf(existing?.colorIndex ?: 0) }
    val none = stringResource(R.string.category_no_parent)
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.category_add else R.string.category_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, stringResource(R.string.field_name))
                SegmentedChoice(
                    CategoryKind.entries.toList(), kind, { kind = it; parentId = null },
                    { typeLabel(if (it == CategoryKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE) },
                )
                val options = parents.filter { it.kind == kind }
                DropdownField(
                    stringResource(R.string.category_parent),
                    listOf<String?>(null) + options.map { it.id },
                    parentId,
                    { parentId = it },
                    { id -> options.firstOrNull { it.id == id }?.let { categoryLabel(it) } ?: none },
                    placeholder = none,
                )
                Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.titleSmall)
                ChipChoice(CategoryIcons.all.keys.toList(), icon, { icon = it }, { "" }, leading = { Icon(CategoryIcons.of(it), contentDescription = it) })
                ColorPicker(color, { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), kind, parentId, icon, color, name.trim() != existingLabel) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
