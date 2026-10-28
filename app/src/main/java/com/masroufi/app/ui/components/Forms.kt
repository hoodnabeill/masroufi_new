@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.masroufi.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.core.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.field_amount),
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(colors = glassFieldColors(), shape = MaterialTheme.shapes.medium,
        value = value,
        onValueChange = { input ->
            val cleaned = Money.normalizeDigits(input).filter { it.isDigit() || it == '.' }
            val digits = Money.fractionDigits(currency)
            val parts = cleaned.split('.')
            val valid = parts.size <= 2 && (parts.size < 2 || parts[1].length <= digits) && (digits > 0 || parts.size == 1)
            if (valid && cleaned.length <= 15) onValueChange(cleaned)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text(currency) },
        singleLine = true,
        isError = isError,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(colors = glassFieldColors(), shape = MaterialTheme.shapes.medium,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/** Date picker values are UTC midnight; converting through UTC keeps the chosen calendar day in every time zone. */
@Composable
fun DateChip(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val formatter = rememberDateFormatter()
    AssistChip(colors = glassAssistChipColors(),
        onClick = { open = true },
        label = { Text(label?.let { "$it: ${date.format(formatter)}" } ?: date.format(formatter)) },
        leadingIcon = { Icon(MIcons.Calendar, contentDescription = null) },
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun OptionalDateChip(
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    emptyLabel: String,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        var open by rememberSaveable { mutableStateOf(false) }
        AssistChip(colors = glassAssistChipColors(),
            onClick = { open = true },
            label = { Text(emptyLabel) },
            leadingIcon = { Icon(MIcons.Calendar, contentDescription = null) },
            modifier = modifier,
        )
        if (open) {
            val state = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { open = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        open = false
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
            ) { DatePicker(state = state) }
        }
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateChip(date, { onDateChange(it) })
            TextButton(onClick = { onDateChange(null) }) { Text(stringResource(R.string.action_clear)) }
        }
    }
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    // A glass track with a thumb that glides (critically damped) to the chosen segment; offset mirrors in RTL.
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), Motion.spatial(reduce), label = "segment")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .glassInset(CircleShape)
            .selectableGroup()
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        Box(
            Modifier
                .offset { IntOffset((segment * position).roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val isSelected = i == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = {
                            if (!isSelected) haptics.tick()
                            onSelect(option)
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun <T> ChipChoice(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(colors = glassChipColors(),
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 1) },
                leadingIcon = leading?.let { { it(option) } },
            )
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(colors = glassFieldColors(), shape = MaterialTheme.shapes.medium,
            value = selected?.let { optionLabel(it) } ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
fun CurrencyField(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (!enabled) {
        OutlinedTextField(colors = glassFieldColors(), shape = MaterialTheme.shapes.medium, value = selected, onValueChange = {}, readOnly = true, enabled = false, label = { Text(stringResource(R.string.field_currency)) }, modifier = modifier.fillMaxWidth())
        return
    }
    DropdownField(
        label = stringResource(R.string.field_currency),
        options = Money.SUPPORTED_CURRENCIES,
        selected = selected,
        onSelect = onSelect,
        optionLabel = { it },
        modifier = modifier,
    )
}
