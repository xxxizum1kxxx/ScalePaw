package com.gpo.scalepaw.feature.metrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gpo.scalepaw.ui.component.ScaleFab
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.model.InputType
import com.gpo.scalepaw.data.model.MetricIcon
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.Metrics
import com.gpo.scalepaw.data.model.UnitType
import com.gpo.scalepaw.ui.component.ChoiceRow
import com.gpo.scalepaw.ui.component.ConfirmDialog
import com.gpo.scalepaw.ui.component.LoadingState
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.ScaleTextField
import com.gpo.scalepaw.ui.component.Section
import com.gpo.scalepaw.ui.component.SettingRow
import com.gpo.scalepaw.ui.component.SwitchRow
import com.gpo.scalepaw.ui.component.label
import com.gpo.scalepaw.ui.component.vector
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.launch

private val ColorPresets = listOf(
    0xFF7E57C2, 0xFF42A5F5, 0xFF26A69A, 0xFF66BB6A, 0xFFD4E157, 0xFFFFCA28, 0xFFFFA726, 0xFFEF5350,
    0xFFEC407A, 0xFF8D6E63, 0xFF78909C, 0xFF5C6BC0,
).map { it.toInt() }

private val CustomUnits = listOf(UnitType.NONE, UnitType.KG, UnitType.PERCENT, UnitType.CM, UnitType.KCAL, UnitType.BPM)

@Composable
fun MetricEditScreen(id: Long) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val repository = context.container.repository
    val session = context.container.session
    val existing = remember(id) { session.state.value.type(id) }
    var draft by remember(id) {
        mutableStateOf(
            existing ?: MetricType(
                identity = "",
                customName = "",
                color = ColorPresets.first(),
                icon = MetricIcon.DEFAULT,
                unit = UnitType.NONE,
                inputType = InputType.FLOAT,
                displayOrder = 0,
                isDerived = false,
                isEnabled = true,
                isPinned = false,
                isOnRightAxis = false,
                isInternal = false,
            ),
        )
    }
    var name by rememberSaveable { mutableStateOf(existing?.customName.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) nav.pop() }

    val isNew = existing == null
    val nameEditable = isNew || existing?.isBuiltin == false
    val canSave = !nameEditable || name.isNotBlank()

    ScalePage(
        title = if (isNew) stringResource(R.string.metric_new) else draft.label(),
        actions = {
            if (existing?.isUserOwned == true || existing?.identity?.startsWith(Metrics.DEVICE) == true) {
                IconButton(onClick = { confirmDelete = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(YoinSymbols.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        floatingAction = {
            ScaleFab(
                text = stringResource(R.string.action_save),
                icon = YoinSymbols.Check,
                onClick = {
                    if (!canSave) return@ScaleFab
                    scope.launch {
                        val t = draft.copy(customName = if (nameEditable) name.trim() else draft.customName)
                        if (isNew) repository.addCustomType(t) else repository.updateType(t)
                        done = true
                    }
                },
                containerColor = if (canSave) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
    ) {
        if (!isNew && existing == null) {
            item { LoadingState() }
            return@ScalePage
        }
        if (nameEditable) {
            item { ScaleTextField(name, { name = it }, stringResource(R.string.field_name)) }
        }
        item {
            Section {
                if (isNew) {
                    ChoiceRow(
                        title = stringResource(R.string.metric_input_type),
                        options = InputType.entries,
                        selected = draft.inputType,
                        label = { stringResource(inputTypeLabel(it)) },
                        onSelect = { draft = draft.copy(inputType = it, unit = if (it == InputType.TEXT) UnitType.NONE else draft.unit) },
                    )
                }
                val units = when {
                    draft.inputType == InputType.TEXT -> emptyList()
                    isNew -> CustomUnits
                    else -> draft.spec?.units ?: draft.unit.convertible()
                }
                if (units.size > 1) {
                    ChoiceRow(
                        title = stringResource(R.string.metric_unit),
                        options = units,
                        selected = draft.unit,
                        label = { it.symbol.ifEmpty { stringResource(R.string.unit_none) } },
                        onSelect = { draft = draft.copy(unit = it) },
                    )
                }
                SwitchRow(stringResource(R.string.metric_enabled), draft.isEnabled, { draft = draft.copy(isEnabled = it) })
                SwitchRow(
                    stringResource(R.string.metric_pinned),
                    draft.isPinned,
                    { draft = draft.copy(isPinned = it) },
                    value = stringResource(R.string.metric_pinned_summary),
                )
                if (draft.inputType != InputType.TEXT) {
                    SwitchRow(stringResource(R.string.metric_right_axis), draft.isOnRightAxis, { draft = draft.copy(isOnRightAxis = it) })
                }
            }
        }
        item {
            Section(title = stringResource(R.string.metric_color)) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    (listOf(draft.color) + ColorPresets).distinct().forEach { c ->
                        val on = c == draft.color
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(if (on) MaterialShapes.Cookie7Sided.toShape() else MaterialShapes.Circle.toShape())
                                .background(Color(c))
                                .clickable { draft = draft.copy(color = c) },
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(YoinSymbols.Check, contentDescription = null, tint = Color.White) }
                    }
                }
            }
        }
        item {
            Section(title = stringResource(R.string.metric_icon)) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricIcon.entries.forEach { icon ->
                        val on = icon == draft.icon
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(MaterialShapes.Circle.toShape())
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { draft = draft.copy(icon = icon) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon.vector(), contentDescription = null) }
                    }
                }
            }
        }
        if (!isNew && draft.isDerived) {
            item { SettingRow(title = stringResource(R.string.metric_derived_note)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.metric_delete_title),
            text = stringResource(R.string.metric_delete_body),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                scope.launch {
                    repository.deleteType(id)
                    done = true
                }
            },
        )
    }
}

private fun inputTypeLabel(type: InputType): Int = when (type) {
    InputType.FLOAT -> R.string.input_decimal
    InputType.INT -> R.string.input_integer
    InputType.TEXT -> R.string.input_text
}
