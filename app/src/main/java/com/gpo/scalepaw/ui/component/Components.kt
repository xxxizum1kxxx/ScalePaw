package com.gpo.scalepaw.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gpo.scalepaw.R
import com.gpo.scalepaw.data.model.MetricIcon
import com.gpo.scalepaw.data.model.UserAvatar
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.scalepaw.ui.theme.YoinMotion
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols

@Composable
fun BackButton(onClick: (() -> Unit)? = null) {
    val nav = LocalNavigator.current
    IconButton(onClick = { onClick?.invoke() ?: nav.pop() }, shapes = IconButtonDefaults.shapes()) {
        Icon(YoinSymbols.Back, contentDescription = stringResource(R.string.action_back))
    }
}

/** Expressive large flexible bar for pushed list pages; keeps the page colour under scroll. */
@Composable
fun ScaleLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (showBack) {
                Row {
                    BackButton()
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun ScaleTopBar(
    title: String,
    showBack: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (showBack) BackButton() },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** A colour block that groups rows (no dividers, no strokes). */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(YoinContainerShapes.Card)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            val alpha = if (enabled) 1f else 0.38f
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val toggle = { v: Boolean ->
        haptics.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onCheckedChange(v)
    }
    SettingRow(title = title, value = value, icon = icon, enabled = enabled, onClick = { toggle(!checked) }) {
        Switch(checked = checked, onCheckedChange = toggle, enabled = enabled)
    }
}

/** A row that opens a bottom sheet of single choices. */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title = title, value = label(selected), icon = icon, enabled = enabled, onClick = { open = true })
    if (open) {
        ChoiceSheet(title, options, selected, label, onDismiss = { open = false }) {
            onSelect(it)
            open = false
        }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(options) { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(option)
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(8.dp))
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

fun MetricIcon.vector(): ImageVector = when (this) {
    MetricIcon.WEIGHT -> ScaleSymbols.Scale
    MetricIcon.BMI -> ScaleSymbols.Ratio
    MetricIcon.FAT -> PiliSymbols.Fire
    MetricIcon.WATER -> ScaleSymbols.Drop
    MetricIcon.MUSCLE -> ScaleSymbols.Muscle
    MetricIcon.LBM -> YoinSymbols.Person
    MetricIcon.BONE -> ScaleSymbols.Bone
    MetricIcon.TAPE -> ScaleSymbols.Tape
    MetricIcon.RATIO -> ScaleSymbols.Ratio
    MetricIcon.VISCERAL -> ScaleSymbols.Organ
    MetricIcon.CALIPER -> ScaleSymbols.Caliper
    MetricIcon.ENERGY -> ScaleSymbols.Flame
    MetricIcon.HEART -> YoinSymbols.Favorite
    MetricIcon.IMPEDANCE -> ScaleSymbols.Bolt
    MetricIcon.PROTEIN -> ScaleSymbols.Protein
    MetricIcon.COMMENT -> ScaleSymbols.Comment
    MetricIcon.DEFAULT -> ScaleSymbols.Trend
}

/** A metric's glyph on a soft disc of its own colour. */
@Composable
fun MetricBadge(icon: MetricIcon, color: Int, size: Dp = 40.dp, shape: Shape = MaterialShapes.Circle.toShape()) {
    val tint = Color(color)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.vector(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun UserAvatar.shape(): Shape = when (this) {
    UserAvatar.CIRCLE -> MaterialShapes.Circle.toShape()
    UserAvatar.COOKIE -> MaterialShapes.Cookie9Sided.toShape()
    UserAvatar.CLOVER -> MaterialShapes.Clover4Leaf.toShape()
    UserAvatar.FLOWER -> MaterialShapes.Flower.toShape()
    UserAvatar.SUNNY -> MaterialShapes.Sunny.toShape()
    UserAvatar.HEART -> MaterialShapes.Heart.toShape()
}

/** Initial on an expressive shape: the user's avatar. */
@Composable
fun UserBadge(name: String, avatar: UserAvatar, size: Dp = 40.dp, selected: Boolean = true) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(size)
            .clip(avatar.shape())
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

/** Expands and collapses with the spatial spring; never a hard cut. */
@Composable
fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(YoinMotion.defaultEffectsSpec()) + expandVertically(YoinMotion.defaultSpatialSpec()),
        exit = fadeOut(YoinMotion.fastEffectsSpec()) + shrinkVertically(YoinMotion.defaultSpatialSpec()),
    ) { content() }
}

/** Bottom padding for pushed pages so the last row clears the navigation bar. */
@Composable
fun Modifier.bottomSafe(): Modifier = this.navigationBarsPadding()

val PillShape = RoundedCornerShape(50)
