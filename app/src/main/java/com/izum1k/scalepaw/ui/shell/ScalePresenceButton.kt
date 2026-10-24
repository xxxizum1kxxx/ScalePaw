package com.izum1k.scalepaw.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.ScalePresence
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.feature.bluetooth.rememberBluetoothAccess
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.YoinMotion

/**
 * Whether the active scale is within reach. Tap searches again (or asks for Bluetooth access);
 * long-press opens the Bluetooth page, as does a tap while no scale is active.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScalePresenceButton() {
    val scales = LocalContext.current.container.scales
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val presence by scales.presence.collectAsStateWithLifecycle()
    val access = rememberBluetoothAccess(onDone = scales::refreshPresence)

    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (presence) {
            ScalePresence.FOUND -> colors.primary
            ScalePresence.SEARCHING -> colors.secondaryContainer
            ScalePresence.MISSING -> colors.surfaceContainerHighest
            ScalePresence.UNAVAILABLE -> colors.errorContainer
            ScalePresence.NONE, ScalePresence.UNKNOWN -> Color.Transparent
        },
        YoinMotion.defaultEffectsSpec(),
        label = "presenceContainer",
    )
    val content = when (presence) {
        ScalePresence.FOUND -> colors.onPrimary
        ScalePresence.SEARCHING -> colors.onSecondaryContainer
        ScalePresence.UNAVAILABLE -> colors.onErrorContainer
        else -> colors.onSurfaceVariant
    }
    val label = stringResource(
        when (presence) {
            ScalePresence.NONE -> R.string.presence_none
            ScalePresence.UNAVAILABLE -> R.string.presence_unavailable
            ScalePresence.UNKNOWN -> R.string.presence_unknown
            ScalePresence.SEARCHING -> R.string.presence_searching
            ScalePresence.FOUND -> R.string.presence_found
            ScalePresence.MISSING -> R.string.presence_missing
        },
    )
    val fadeInSpec = YoinMotion.defaultEffectsSpec<Float>()
    val scaleInSpec = YoinMotion.defaultSpatialSpec<Float>()
    val fadeOutSpec = YoinMotion.fastEffectsSpec<Float>()

    Box(
        Modifier
            .size(48.dp)
            .clip(MaterialShapes.Circle.toShape())
            .semantics { contentDescription = label }
            .combinedClickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Button,
                onClickLabel = label,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    nav.push(Route.SettingsBluetooth)
                },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    when (presence) {
                        ScalePresence.NONE -> nav.push(Route.SettingsBluetooth)
                        ScalePresence.UNAVAILABLE -> access()
                        else -> scales.refreshPresence()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = presence,
            transitionSpec = { (fadeIn(fadeInSpec) + scaleIn(scaleInSpec, initialScale = 0.6f)) togetherWith fadeOut(fadeOutSpec) },
            label = "presence",
        ) { p ->
            Box(
                Modifier
                    .size(40.dp)
                    .then(
                        if (p == ScalePresence.FOUND) {
                            Modifier.border(2.dp, colors.primary.copy(alpha = 0.35f), CircleShape).padding(4.dp)
                        } else {
                            Modifier.padding(4.dp)
                        },
                    )
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                when (p) {
                    ScalePresence.SEARCHING -> LoadingIndicator(Modifier.size(28.dp), color = content)
                    ScalePresence.UNAVAILABLE -> Icon(ScaleSymbols.BluetoothOff, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    else -> Icon(ScaleSymbols.Bluetooth, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
