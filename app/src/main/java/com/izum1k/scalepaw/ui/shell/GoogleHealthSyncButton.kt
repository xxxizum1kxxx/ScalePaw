package com.izum1k.scalepaw.ui.shell

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.googlehealth.SyncResult
import com.izum1k.scalepaw.feature.googlehealth.googleHealthResultText
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.YoinMotion
import kotlinx.coroutines.delay

private enum class SyncIcon { IDLE, RUNNING, DONE, FAILED, REAUTH }

/**
 * Pushes new measurements to Google Health; hidden until an account is connected.
 * Tap syncs, long-press opens the Google Health settings, as does a tap once the sign-in has expired.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoogleHealthSyncButton() {
    val context = LocalContext.current
    val health = context.container.googleHealth
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val state by health.store.state.collectAsStateWithLifecycle()
    val syncing by health.syncing.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<SyncResult?>(null) }

    LaunchedEffect(health) {
        health.results.collect { result ->
            last = result
            haptics.performHapticFeedback(if (result is SyncResult.Done) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            Toast.makeText(context, context.googleHealthResultText(result), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(last) {
        if (last is SyncResult.Done) {
            delay(2_000)
            last = null
        }
    }

    if (!state.connected) return
    val icon = when {
        state.needsReauth -> SyncIcon.REAUTH
        syncing -> SyncIcon.RUNNING
        last is SyncResult.Done -> SyncIcon.DONE
        last is SyncResult.Failed -> SyncIcon.FAILED
        else -> SyncIcon.IDLE
    }
    val label = stringResource(if (icon == SyncIcon.REAUTH) R.string.google_health_reconnect else R.string.google_health_sync_now)
    val colors = MaterialTheme.colorScheme
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
                    nav.push(Route.SettingsGoogleHealth)
                },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (icon == SyncIcon.REAUTH) nav.push(Route.SettingsGoogleHealth) else health.requestSync()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = { (fadeIn(fadeInSpec) + scaleIn(scaleInSpec, initialScale = 0.6f)) togetherWith fadeOut(fadeOutSpec) },
            label = "googleHealthSync",
        ) { i ->
            when (i) {
                SyncIcon.RUNNING -> LoadingIndicator(Modifier.size(28.dp))
                SyncIcon.DONE -> Icon(YoinSymbols.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                SyncIcon.FAILED, SyncIcon.REAUTH -> Icon(YoinSymbols.CloudOffline, contentDescription = null, tint = colors.error, modifier = Modifier.size(22.dp))
                SyncIcon.IDLE -> Icon(YoinSymbols.Cloud, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
    }
}
