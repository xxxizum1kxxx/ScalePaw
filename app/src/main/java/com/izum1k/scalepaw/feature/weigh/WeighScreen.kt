package com.izum1k.scalepaw.feature.weigh

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.ScaleInteraction
import com.izum1k.scalepaw.ble.ScaleStatus
import com.izum1k.scalepaw.ble.WeighState
import com.izum1k.scalepaw.ble.openscale.BluetoothEvent.UserInteractionType
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitConversion
import com.izum1k.scalepaw.data.model.User
import com.izum1k.scalepaw.data.repository.PendingReading
import com.izum1k.scalepaw.data.repository.SessionState
import com.izum1k.scalepaw.feature.bluetooth.rememberBluetoothAccess
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.MetricBadge
import com.izum1k.scalepaw.ui.component.ScaleFab
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.ScaleTextField
import com.izum1k.scalepaw.ui.component.UserBadge
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.izum1k.scalepaw.ui.component.label
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion

private enum class Stage { Connecting, Waiting, Measuring, Result, Stopped, Error }

private fun stageOf(status: ScaleStatus, weigh: WeighState): Stage = when {
    weigh.pending.isNotEmpty() -> Stage.Result
    status is ScaleStatus.Error -> Stage.Error
    weigh.liveKg != null || weigh.receiving -> Stage.Measuring
    status is ScaleStatus.Connected || status is ScaleStatus.Listening -> Stage.Waiting
    status is ScaleStatus.Scanning || status is ScaleStatus.Connecting -> Stage.Connecting
    else -> Stage.Stopped
}

/** One weigh-in: connects to the active scale while open, shows progress, stores readings on confirmation. */
@Composable
fun WeighScreen() {
    val context = LocalContext.current
    val container = context.container
    val vm = containerViewModel { WeighViewModel(it.scales) }
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val state by container.session.state.collectAsStateWithLifecycle()
    val status by container.scales.status.collectAsStateWithLifecycle()
    val weigh by container.scales.weigh.collectAsStateWithLifecycle()
    val interaction by container.scales.interaction.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val stage = stageOf(status, weigh)
    var reassigning by rememberSaveable { mutableStateOf<Int?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val access = rememberBluetoothAccess(onDone = vm::start)
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            access()
        }
    }
    LaunchedEffect(stage) {
        when (stage) {
            Stage.Result -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            Stage.Measuring -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            else -> Unit
        }
    }
    LaunchedEffect(saved) {
        if (saved) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            nav.pop()
        }
    }

    ScalePage(
        title = stringResource(R.string.weigh_title),
        floatingAction = {
            if (stage == Stage.Result) {
                val count = weigh.pending.size
                ScaleFab(
                    text = if (count > 1) pluralStringResource(R.plurals.weigh_save_all, count, count) else stringResource(R.string.action_save),
                    icon = YoinSymbols.Check,
                    onClick = vm::save,
                )
            }
        },
    ) {
        item {
            StatusCard(
                stage = stage,
                scaleName = state.settings.scale.active?.name,
                status = status,
                weigh = weigh,
                state = state,
                onRetry = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (stage == Stage.Error) access() else vm.retry()
                },
            )
        }
        itemsIndexed(weigh.pending, key = { index, p -> "${p.reading.timestamp}/$index" }) { index, pending ->
            ResultCard(pending, state, onPickUser = { reassigning = index })
        }
    }

    reassigning?.let { index ->
        val current = weigh.pending.getOrNull(index)
        if (current == null) {
            reassigning = null
        } else {
            UserPicker(state.users, current.userId, onDismiss = { reassigning = null }) { user ->
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                vm.reassign(index, user.id)
                reassigning = null
            }
        }
    }
    interaction?.let { prompt ->
        InteractionDialog(prompt, onDismiss = container.scales::dismissInteraction, onAnswer = container.scales::answer)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(
    stage: Stage,
    scaleName: String?,
    status: ScaleStatus,
    weigh: WeighState,
    state: SessionState,
    onRetry: () -> Unit,
) {
    val error = stage == Stage.Error
    val container by animateColorAsState(
        when (stage) {
            Stage.Error -> MaterialTheme.colorScheme.errorContainer
            Stage.Result -> MaterialTheme.colorScheme.tertiaryContainer
            Stage.Stopped -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        YoinMotion.defaultEffectsSpec(),
        label = "weighStatus",
    )
    val content = when (stage) {
        Stage.Error -> MaterialTheme.colorScheme.onErrorContainer
        Stage.Result -> MaterialTheme.colorScheme.onTertiaryContainer
        Stage.Stopped -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val title = when (stage) {
        Stage.Connecting -> stringResource(if (status is ScaleStatus.Scanning) R.string.ble_status_scanning else R.string.ble_status_connecting)
        Stage.Waiting -> stringResource(R.string.ble_step_on)
        Stage.Measuring -> stringResource(R.string.weigh_measuring)
        Stage.Result -> stringResource(R.string.weigh_done)
        Stage.Stopped -> stringResource(R.string.weigh_disconnected)
        Stage.Error -> stringResource(R.string.ble_status_error)
    }
    val detail = (status as? ScaleStatus.Error)?.message ?: weigh.hint
    val fadeInSpec = YoinMotion.defaultEffectsSpec<Float>()
    val scaleInSpec = YoinMotion.defaultSpatialSpec<Float>()
    val fadeOutSpec = YoinMotion.fastEffectsSpec<Float>()
    CompositionLocalProvider(LocalContentColor provides content) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(YoinContainerShapes.Card)
                .background(container)
                .padding(24.dp)
                .animateContentSize(YoinMotion.defaultSpatialSpec()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(fadeInSpec) + scaleIn(scaleInSpec, initialScale = 0.8f)) togetherWith fadeOut(fadeOutSpec)
                },
                label = "weighStage",
            ) { s ->
                val shape = when (s) {
                    Stage.Connecting, Stage.Waiting -> MaterialShapes.Cookie9Sided
                    Stage.Measuring -> MaterialShapes.Sunny
                    Stage.Result -> MaterialShapes.Clover4Leaf
                    Stage.Stopped -> MaterialShapes.Circle
                    Stage.Error -> MaterialShapes.Square
                }
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(shape.toShape())
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when (s) {
                        Stage.Connecting, Stage.Measuring -> LoadingIndicator(Modifier.size(64.dp))
                        Stage.Waiting -> Icon(ScaleSymbols.Scale, contentDescription = null, modifier = Modifier.size(40.dp))
                        Stage.Result -> Icon(YoinSymbols.Check, contentDescription = null, modifier = Modifier.size(40.dp))
                        Stage.Stopped -> Icon(ScaleSymbols.Bluetooth, contentDescription = null, modifier = Modifier.size(40.dp))
                        Stage.Error -> Icon(YoinSymbols.Error, contentDescription = null, modifier = Modifier.size(40.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            weigh.liveKg?.takeIf { stage == Stage.Measuring }?.let { LiveWeight(it, state) }
            Text(title, style = MaterialTheme.typography.titleLarge)
            listOfNotNull(scaleName, detail?.takeIf { stage != Stage.Result }).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f))
            }
            if (stage == Stage.Error || stage == Stage.Stopped || stage == Stage.Result) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onRetry) {
                    Icon(YoinSymbols.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (stage == Stage.Result) R.string.weigh_again else R.string.weigh_retry))
                }
            }
        }
    }
}

@Composable
private fun LiveWeight(kg: Float, state: SessionState) {
    val type = state.type(Metrics.WEIGHT)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            type?.let { Fmt.value(UnitConversion.fromKg(kg, it.unit), it) } ?: Fmt.number(kg, 1),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            type?.unit?.symbol ?: "kg",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun ResultCard(pending: PendingReading, state: SessionState, onPickUser: () -> Unit) {
    val context = LocalContext.current
    val user = state.users.firstOrNull { it.id == pending.userId }
    val shown = state.visibleTypes.filter { pending.values[it.id]?.isEmpty == false }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize(YoinMotion.defaultSpatialSpec()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickUser)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (user != null) UserBadge(user.name, user.avatar, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(user?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Fmt.weekdayDate(pending.reading.timestamp)}  ${Fmt.time(context, pending.reading.timestamp)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(YoinSymbols.UnfoldMore, contentDescription = stringResource(R.string.weigh_change_user), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            shown.forEach { type ->
                val v = pending.values.getValue(type.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(YoinContainerShapes.ListRow)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetricBadge(type.icon, type.color, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(type.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        v.number?.let { Fmt.value(it, type) } ?: v.text.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (type.unit.symbol.isNotEmpty()) {
                        Spacer(Modifier.width(3.dp))
                        Text(type.unit.symbol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPicker(users: List<User>, selectedId: Long, onDismiss: () -> Unit, onPick: (User) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(users, key = { it.id }) { user ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(user) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserBadge(user.name, user.avatar, size = 44.dp, selected = user.id == selectedId)
                    Spacer(Modifier.width(16.dp))
                    Text(user.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (user.id == selectedId) Icon(YoinSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun InteractionDialog(prompt: ScaleInteraction, onDismiss: () -> Unit, onAnswer: (Int) -> Unit) {
    var slot by rememberSaveable(prompt) { mutableIntStateOf(Int.MIN_VALUE) }
    var code by rememberSaveable(prompt) { mutableStateOf("") }
    val chooseUser = prompt.type == UserInteractionType.CHOOSE_USER
    val valid = if (chooseUser) slot != Int.MIN_VALUE else code.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (chooseUser) PiliSymbols.PersonAdd else YoinSymbols.Check, contentDescription = null) },
        title = { Text(stringResource(if (chooseUser) R.string.ble_choose_user else R.string.ble_enter_consent)) },
        text = {
            Column {
                Text(stringResource(if (chooseUser) R.string.ble_choose_user_text else R.string.ble_enter_consent_text))
                Spacer(Modifier.height(16.dp))
                if (chooseUser) {
                    if (prompt.choices.isEmpty()) Text(stringResource(R.string.ble_choose_user_empty))
                    prompt.choices.forEach { (label, index) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { slot = index }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = slot == index, onClick = { slot = index })
                            Text(label)
                        }
                    }
                } else {
                    ScaleTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter(Char::isDigit) },
                        label = stringResource(R.string.ble_consent_code),
                        keyboardType = KeyboardType.NumberPassword,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onAnswer(if (chooseUser) slot else code.toInt()) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
