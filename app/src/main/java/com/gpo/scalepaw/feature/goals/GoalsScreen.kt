package com.gpo.scalepaw.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.UserGoal
import com.gpo.scalepaw.ui.component.ChoiceRow
import com.gpo.scalepaw.ui.component.DateDialog
import com.gpo.scalepaw.ui.component.EmptyState
import com.gpo.scalepaw.ui.component.Fmt
import com.gpo.scalepaw.ui.component.MetricBadge
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.ScaleSymbols
import com.gpo.scalepaw.ui.component.ScaleTextField
import com.gpo.scalepaw.ui.component.SettingRow
import com.gpo.scalepaw.ui.component.label
import com.gpo.scalepaw.ui.component.toLocalDate
import com.gpo.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.launch
import java.time.ZoneId

@Composable
fun GoalsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by context.container.session.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<UserGoal?>(null) }
    var adding by remember { mutableStateOf(false) }
    val user = state.user

    ScalePage(
        title = stringResource(R.string.settings_goals),
        floatingAction = {
            if (user != null) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(YoinSymbols.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.goal_add)) },
                )
            }
        },
    ) {
        if (state.goals.isEmpty()) {
            item { EmptyState(ScaleSymbols.Target, stringResource(R.string.goal_empty)) }
        }
        items(state.goals, key = { it.typeId }) { goal ->
            val type = state.type(goal.typeId) ?: return@items
            val current = state.measurements.firstNotNullOfOrNull { it.number(type) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(YoinContainerShapes.ListRow)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { editing = goal }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricBadge(type.icon, type.color)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.label(), style = MaterialTheme.typography.bodyLarge)
                    current?.let {
                        Text(
                            stringResource(R.string.goal_current, "${Fmt.value(it, type)} ${type.unit.symbol}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    goal.targetDate?.let {
                        Text(
                            stringResource(R.string.goal_target_date, Fmt.date(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("${Fmt.value(goal.goalValue, type)} ${type.unit.symbol}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (user != null && (adding || editing != null)) {
        val taken = state.goals.map { it.typeId }.toSet()
        val choices = state.numericTypes.filter { it.id !in taken || it.id == editing?.typeId }
        GoalSheet(
            initial = editing,
            choices = choices,
            onDismiss = {
                adding = false
                editing = null
            },
            onDelete = editing?.let { g -> { scope.launch { context.container.repository.deleteGoal(g.userId, g.typeId) } } },
            onSave = { type, value, target ->
                scope.launch {
                    context.container.repository.saveGoal(
                        UserGoal(user.id, type.id, value, editing?.startDate ?: System.currentTimeMillis(), target),
                    )
                }
            },
        )
    }
}

@Composable
private fun GoalSheet(
    initial: UserGoal?,
    choices: List<MetricType>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (MetricType, Float, Long?) -> Unit,
) {
    if (choices.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var type by remember { mutableStateOf(choices.firstOrNull { it.id == initial?.typeId } ?: choices.first()) }
    var text by remember { mutableStateOf(initial?.goalValue?.let { Fmt.number(it, 1) }.orEmpty()) }
    var target by remember { mutableStateOf(initial?.targetDate) }
    var pickDate by remember { mutableStateOf(false) }
    val value = text.replace(',', '.').toFloatOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (initial == null) R.string.goal_add else R.string.goal_edit), style = MaterialTheme.typography.titleLarge)
            if (initial == null) {
                ChoiceRow(stringResource(R.string.goal_metric), choices, type, { it.label() }, { type = it })
            }
            ScaleTextField(
                text,
                { text = it },
                stringResource(R.string.goal_value),
                suffix = type.unit.symbol,
                keyboardType = KeyboardType.Decimal,
                isError = text.isNotBlank() && value == null,
            )
            SettingRow(
                title = stringResource(R.string.goal_target_date_label),
                value = target?.let { Fmt.date(it) } ?: stringResource(R.string.goal_no_date),
                icon = ScaleSymbols.Calendar,
                onClick = { pickDate = true },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(
                    enabled = value != null,
                    onClick = {
                        value?.let { onSave(type, it, target) }
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
    if (pickDate) {
        DateDialog((target ?: System.currentTimeMillis()).toLocalDate(), onDismiss = { pickDate = false }) { d ->
            target = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
