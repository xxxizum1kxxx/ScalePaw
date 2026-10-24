package com.gpo.scalepaw.feature.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.ui.component.ScaleFab
import com.gpo.scalepaw.R
import com.gpo.scalepaw.data.model.ActivityLevel
import com.gpo.scalepaw.data.model.AmputationPart
import com.gpo.scalepaw.data.model.Gender
import com.gpo.scalepaw.data.model.Limb
import com.gpo.scalepaw.data.model.UserAvatar
import com.gpo.scalepaw.ui.component.ChoiceRow
import com.gpo.scalepaw.ui.component.ConfirmDialog
import com.gpo.scalepaw.ui.component.ConnectedChoice
import com.gpo.scalepaw.ui.component.DateDialog
import com.gpo.scalepaw.ui.component.LoadingState
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.ScaleSymbols
import com.gpo.scalepaw.ui.component.ScaleTextField
import com.gpo.scalepaw.ui.component.Section
import com.gpo.scalepaw.ui.component.SettingRow
import com.gpo.scalepaw.ui.component.SwitchRow
import com.gpo.scalepaw.ui.component.UserBadge
import com.gpo.scalepaw.ui.component.containerViewModel
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.yoin.symbols.YoinSymbols
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun UserEditScreen(id: Long) {
    val vm = containerViewModel(key = "user$id") { UserEditViewModel(id, it.repository) }
    val s by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    var pickBirth by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(s.done) {
        if (s.done) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            nav.pop()
        }
    }

    ScalePage(
        title = stringResource(if (s.isNew) R.string.user_new else R.string.user_edit),
        actions = {
            if (s.canDelete) {
                IconButton(onClick = { confirmDelete = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(YoinSymbols.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        floatingAction = {
            if (s.loaded) {
                ScaleFab(
                    text = stringResource(R.string.action_save),
                    icon = YoinSymbols.Check,
                    onClick = vm::save,
                    containerColor = if (s.canSave) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        },
    ) {
        if (!s.loaded) {
            item { LoadingState() }
            return@ScalePage
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserAvatar.entries.forEach { a ->
                    Box(
                        Modifier.clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            vm.update { it.copy(avatar = a) }
                        },
                    ) { UserBadge(s.name.ifBlank { "?" }, a, size = 64.dp, selected = a == s.avatar) }
                }
            }
        }
        item {
            ScaleTextField(s.name, { v -> vm.update { it.copy(name = v) } }, stringResource(R.string.field_name))
        }
        item {
            ConnectedChoice(Gender.entries, s.gender, { stringResource(it.label) }, { g -> vm.update { it.copy(gender = g) } })
        }
        item {
            ScaleTextField(
                s.height,
                { v -> vm.update { it.copy(height = v) } },
                stringResource(R.string.field_height),
                suffix = "cm",
                isError = s.height.isNotBlank() && s.heightCm == null,
                keyboardType = KeyboardType.Decimal,
            )
        }
        item {
            Section {
                SettingRow(
                    title = stringResource(R.string.field_birth_date),
                    value = s.birthDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    icon = ScaleSymbols.Calendar,
                    onClick = { pickBirth = true },
                )
                ChoiceRow(
                    title = stringResource(R.string.field_activity),
                    options = ActivityLevel.entries,
                    selected = s.activity,
                    label = { stringResource(it.label) },
                    onSelect = { a -> vm.update { it.copy(activity = a) } },
                    icon = ScaleSymbols.Flame,
                )
                SwitchRow(
                    title = stringResource(R.string.field_assisted),
                    value = stringResource(R.string.field_assisted_summary),
                    checked = s.assisted,
                    onCheckedChange = { v -> vm.update { it.copy(assisted = v) } },
                )
            }
        }
        item {
            Section(title = stringResource(R.string.section_amputation)) {
                Limb.entries.forEach { limb ->
                    val isArm = limb == Limb.LEFT_ARM || limb == Limb.RIGHT_ARM
                    val parts = listOf<AmputationPart?>(null) + AmputationPart.entries.filter { it.arm == isArm }
                    ChoiceRow(
                        title = stringResource(limb.label),
                        options = parts,
                        selected = s.amputations[limb],
                        label = { p -> if (p == null) stringResource(R.string.amputation_none) else stringResource(p.label) },
                        onSelect = { p -> vm.setAmputation(limb, p) },
                    )
                }
            }
        }
    }

    if (pickBirth) {
        DateDialog(s.birthDate, onDismiss = { pickBirth = false }) { d -> vm.update { it.copy(birthDate = d) } }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.user_delete_title),
            text = stringResource(R.string.user_delete_body, s.name),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = vm::delete,
        )
    }
}
