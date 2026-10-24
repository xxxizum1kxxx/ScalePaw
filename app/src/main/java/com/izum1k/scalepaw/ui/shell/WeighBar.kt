package com.izum1k.scalepaw.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.yoin.symbols.YoinSymbols
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.ScalePresence
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.YoinMotion

private val BarHeight = 56.dp
private val BlockGap = 3.dp
private val InnerCorner = CornerSize(6.dp)
private val OuterCorner = CornerSize(50)
private val BlockShadow = 3.dp
private const val PressScale = 0.94f

/** Bottom room the shell's lists leave for the bar. */
val WeighBarInset = BarHeight

private enum class StartMode { Start, Find, Searching }

/**
 * PiliPaw's mini bar for the weigh-in: connected blocks over the nav bar, the outer ends fully
 * round and the inner ones a small corner. + records by hand; Start opens the weigh-in, or
 * searches again while the active scale cannot be heard.
 */
@Composable
fun WeighBar(modifier: Modifier = Modifier) {
    val container = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by container.session.state.collectAsStateWithLifecycle()
    val presence by container.scales.presence.collectAsStateWithLifecycle()
    val hasScale = session.settings.scale.active != null
    val mode = when {
        !hasScale -> StartMode.Start
        presence == ScalePresence.SEARCHING -> StartMode.Searching
        presence == ScalePresence.MISSING -> StartMode.Find
        else -> StartMode.Start
    }
    val colors = MaterialTheme.colorScheme
    val startColor by animateColorAsState(
        if (mode == StartMode.Start) colors.primaryContainer else colors.surfaceContainerHighest,
        YoinMotion.defaultEffectsSpec(),
        label = "startBlock",
    )
    val startContent = if (mode == StartMode.Start) colors.onPrimaryContainer else colors.onSurfaceVariant

    Row(modifier.height(BarHeight), horizontalArrangement = Arrangement.spacedBy(BlockGap)) {
        Block(
            label = stringResource(R.string.action_add_measurement),
            shape = RoundedCornerShape(OuterCorner, InnerCorner, InnerCorner, OuterCorner),
            color = colors.secondaryContainer,
            modifier = Modifier.size(BarHeight),
            onClick = { nav.push(Route.MeasurementEdit()) },
        ) {
            Icon(YoinSymbols.Add, contentDescription = null, tint = colors.onSecondaryContainer)
        }
        val startLabel = stringResource(
            when (mode) {
                StartMode.Start -> R.string.weigh_start
                StartMode.Find -> R.string.weigh_find_scale
                StartMode.Searching -> R.string.weigh_searching
            },
        )
        Block(
            label = startLabel,
            shape = RoundedCornerShape(InnerCorner, OuterCorner, OuterCorner, InnerCorner),
            color = startColor,
            modifier = Modifier
                .widthIn(min = BarHeight * 16f / 9f)
                .animateContentSize(YoinMotion.defaultSpatialSpec()),
            onClick = {
                when {
                    !hasScale -> nav.push(Route.SettingsBluetooth)
                    mode == StartMode.Start -> nav.push(Route.Weigh)
                    else -> container.scales.refreshPresence()
                }
            },
        ) {
            StartContent(mode, startLabel, startContent)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StartContent(mode: StartMode, label: String, tint: Color) {
    val fadeInSpec = YoinMotion.defaultEffectsSpec<Float>()
    val scaleInSpec = YoinMotion.defaultSpatialSpec<Float>()
    val fadeOutSpec = YoinMotion.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = mode to label,
        transitionSpec = { (fadeIn(fadeInSpec) + scaleIn(scaleInSpec, initialScale = 0.9f)) togetherWith fadeOut(fadeOutSpec) },
        contentAlignment = Alignment.Center,
        label = "startContent",
    ) { (m, text) ->
        Row(
            Modifier.padding(start = 18.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (m) {
                StartMode.Start -> Icon(YoinSymbols.PlayArrow, contentDescription = null, tint = tint)
                StartMode.Find -> Icon(ScaleSymbols.Bluetooth, contentDescription = null, tint = tint)
                StartMode.Searching -> LoadingIndicator(Modifier.size(24.dp), color = tint)
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = tint)
        }
    }
}

@Composable
private fun Block(
    label: String,
    shape: Shape,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PressScale else 1f, YoinMotion.fastSpatialSpec(), label = "blockPress")
    Box(
        modifier
            .height(BarHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = shape
                clip = true
                shadowElevation = BlockShadow.toPx()
            }
            .background(color)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
