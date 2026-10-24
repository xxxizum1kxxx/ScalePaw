package com.gpo.scalepaw.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.R
import com.gpo.scalepaw.data.model.BodyFatFormula
import com.gpo.scalepaw.data.model.BodyWaterFormula
import com.gpo.scalepaw.data.model.ColorStyle
import com.gpo.scalepaw.data.model.LbmFormula
import com.gpo.scalepaw.data.model.ThemeMode
import com.gpo.scalepaw.ui.component.ChoiceRow
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.Section
import com.gpo.scalepaw.ui.component.SettingRow
import com.gpo.scalepaw.ui.component.SwitchRow
import com.gpo.scalepaw.ui.component.containerViewModel
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols

private val SeedPresets = listOf(
    0xFF2E9C8F, 0xFF4A6CF7, 0xFF7E57C2, 0xFFE0567A, 0xFFFF8A65, 0xFFF2B33D, 0xFF5AA454, 0xFF607D8B,
).map { it.toInt() }

@Composable
fun GeneralSettingsScreen() {
    val vm = containerViewModel { SettingsViewModel(it.session, it.settings, it.repository) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScalePage(title = stringResource(R.string.settings_general)) {
        item {
            Section(title = stringResource(R.string.section_appearance)) {
                ChoiceRow(
                    title = stringResource(R.string.setting_theme),
                    options = ThemeMode.entries,
                    selected = s.appearance.themeMode,
                    label = { stringResource(it.label) },
                    onSelect = { m -> vm.appearance { it.copy(themeMode = m) } },
                    icon = PiliSymbols.DarkMode,
                )
                SwitchRow(
                    title = stringResource(R.string.setting_dynamic_color),
                    value = stringResource(R.string.setting_dynamic_color_summary),
                    checked = s.appearance.dynamicColor,
                    onCheckedChange = { v -> vm.appearance { it.copy(dynamicColor = v) } },
                    icon = PiliSymbols.Palette,
                )
                if (s.appearance.dynamicColor) {
                    ChoiceRow(
                        title = stringResource(R.string.setting_color_style),
                        options = ColorStyle.entries,
                        selected = s.appearance.colorStyle,
                        label = { stringResource(it.label) },
                        onSelect = { c -> vm.appearance { it.copy(colorStyle = c) } },
                    )
                } else {
                    SettingRow(title = stringResource(R.string.setting_seed_color))
                    SeedRow(s.appearance.seedColor) { c -> vm.appearance { it.copy(seedColor = c) } }
                    SettingRow(title = stringResource(R.string.setting_accent_color))
                    SeedRow(s.appearance.accentColor) { c -> vm.appearance { it.copy(accentColor = c) } }
                }
                SettingRow(
                    title = stringResource(R.string.setting_language),
                    value = stringResource(R.string.setting_language_summary),
                    icon = YoinSymbols.Translate,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
                SwitchRow(
                    title = stringResource(R.string.setting_haptics),
                    checked = s.hapticsEnabled,
                    onCheckedChange = vm::haptics,
                )
            }
        }
        item {
            Section(title = stringResource(R.string.section_formulas)) {
                ChoiceRow(
                    title = stringResource(R.string.setting_bf_formula),
                    options = BodyFatFormula.entries,
                    selected = s.formulas.bodyFat,
                    label = { stringResource(it.label) },
                    onSelect = { f -> vm.formulas { it.copy(bodyFat = f) } },
                )
                ChoiceRow(
                    title = stringResource(R.string.setting_bw_formula),
                    options = BodyWaterFormula.entries,
                    selected = s.formulas.bodyWater,
                    label = { stringResource(it.label) },
                    onSelect = { f -> vm.formulas { it.copy(bodyWater = f) } },
                )
                ChoiceRow(
                    title = stringResource(R.string.setting_lbm_formula),
                    options = LbmFormula.entries,
                    selected = s.formulas.lbm,
                    label = { stringResource(it.label) },
                    onSelect = { f -> vm.formulas { it.copy(lbm = f) } },
                )
                SwitchRow(
                    title = stringResource(R.string.setting_carry_over),
                    value = stringResource(R.string.setting_carry_over_summary),
                    checked = s.formulas.carryOverValues,
                    onCheckedChange = { v -> vm.formulas { it.copy(carryOverValues = v) } },
                )
            }
        }
        item {
            Section(title = stringResource(R.string.section_assignment)) {
                SwitchRow(
                    title = stringResource(R.string.setting_smart_assignment),
                    value = stringResource(R.string.setting_smart_assignment_summary),
                    checked = s.assignment.smartAssignment,
                    onCheckedChange = { v -> vm.assignment { it.copy(smartAssignment = v) } },
                )
                if (s.assignment.smartAssignment) {
                    SettingRow(
                        title = stringResource(R.string.setting_tolerance),
                        value = stringResource(R.string.percent_value, s.assignment.tolerancePercent),
                    )
                    Slider(
                        value = s.assignment.tolerancePercent.toFloat(),
                        onValueChange = { v -> vm.assignment { it.copy(tolerancePercent = v.toInt()) } },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SwitchRow(
                        title = stringResource(R.string.setting_ignore_outside),
                        checked = s.assignment.ignoreOutsideTolerance,
                        onCheckedChange = { v -> vm.assignment { it.copy(ignoreOutsideTolerance = v) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeedPresets.forEach { c ->
            val on = c == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(if (on) MaterialShapes.Cookie7Sided.toShape() else MaterialShapes.Circle.toShape())
                    .background(Color(c))
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(YoinSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
            }
        }
    }
}
