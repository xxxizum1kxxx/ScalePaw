package com.izum1k.scalepaw.feature.bluetooth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.ScaleInteraction
import com.izum1k.scalepaw.ble.openscale.BluetoothEvent.UserInteractionType
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.ui.component.ScaleTextField
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols

/** App-wide scale feedback: driver prompts as dialogs, connection notes as snackbars. */
@Composable
fun ScaleOverlay(modifier: Modifier = Modifier) {
    val scales = LocalContext.current.container.scales
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val interaction by scales.interaction.collectAsStateWithLifecycle()

    LaunchedEffect(scales) {
        scales.messages.collect { message ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            snackbar.showSnackbar(message)
        }
    }
    SnackbarHost(snackbar, modifier.navigationBarsPadding().padding(bottom = 88.dp))

    interaction?.let { prompt ->
        InteractionDialog(prompt, onDismiss = scales::dismissInteraction, onAnswer = scales::answer)
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
