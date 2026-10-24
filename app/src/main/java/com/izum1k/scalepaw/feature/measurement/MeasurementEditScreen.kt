package com.izum1k.scalepaw.feature.measurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.ui.component.ScaleFab
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.data.model.InputType
import com.izum1k.scalepaw.data.model.UnitType
import com.izum1k.scalepaw.ui.component.ConfirmDialog
import com.izum1k.scalepaw.ui.component.DateDialog
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.LoadingState
import com.izum1k.scalepaw.ui.component.MetricBadge
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.ScaleTextField
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.TimeDialog
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.izum1k.scalepaw.ui.component.epochMillisOf
import com.izum1k.scalepaw.ui.component.label
import com.izum1k.scalepaw.ui.component.toLocalDate
import com.izum1k.scalepaw.ui.component.toLocalTime
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.gpo.yoin.symbols.YoinSymbols

@Composable
fun MeasurementEditScreen(id: Long) {
    val vm = containerViewModel(key = "edit$id") { MeasurementEditViewModel(id, it.session, it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) {
        if (state.done) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            nav.pop()
        }
    }

    ScalePage(
        title = stringResource(if (state.isNew) R.string.measurement_new else R.string.measurement_edit),
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { confirmDelete = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(YoinSymbols.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        floatingAction = {
            if (state.loaded) {
                ScaleFab(
                    text = stringResource(R.string.action_save),
                    icon = YoinSymbols.Check,
                    onClick = { if (state.canSave) vm.save() },
                    containerColor = if (state.canSave) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        },
    ) {
        if (!state.loaded) {
            item { LoadingState() }
            return@ScalePage
        }
        item {
            Section {
                SettingRow(
                    title = stringResource(R.string.field_date),
                    value = Fmt.weekdayDate(state.timestamp),
                    icon = ScaleSymbols.Calendar,
                    onClick = { pickDate = true },
                )
                SettingRow(
                    title = stringResource(R.string.field_time),
                    value = Fmt.time(context, state.timestamp),
                    icon = ScaleSymbols.Clock,
                    onClick = { pickTime = true },
                )
            }
        }
        items(state.fields, key = { it.type.id }) { field ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricBadge(field.type.icon, field.type.color, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                ScaleTextField(
                    value = field.text,
                    onValueChange = { vm.setText(field.type.id, it) },
                    label = field.type.label(),
                    placeholder = field.hint,
                    suffix = if (field.type.unit == UnitType.ST) "st:lb" else field.type.unit.symbol,
                    isError = field.error,
                    singleLine = field.type.inputType != InputType.TEXT,
                    keyboardType = when (field.type.inputType) {
                        InputType.FLOAT -> if (field.type.unit == UnitType.ST) KeyboardType.Text else KeyboardType.Decimal
                        InputType.INT -> KeyboardType.Number
                        InputType.TEXT -> KeyboardType.Text
                    },
                )
            }
        }
        if (state.derived.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.measurement_derived_hint, state.derived.joinToString(", ") { it.label(context) }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (pickDate) {
        DateDialog(state.timestamp.toLocalDate(), onDismiss = { pickDate = false }) { d ->
            vm.setTimestamp(epochMillisOf(d, state.timestamp.toLocalTime()))
        }
    }
    if (pickTime) {
        TimeDialog(state.timestamp.toLocalTime(), onDismiss = { pickTime = false }) { t ->
            vm.setTimestamp(epochMillisOf(state.timestamp.toLocalDate(), t))
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.measurement_delete_title),
            text = stringResource(R.string.measurement_delete_body),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = vm::delete,
        )
    }
}
