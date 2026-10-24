package com.izum1k.scalepaw.feature.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.izum1k.scalepaw.container

/**
 * Asks for the Bluetooth permissions, then for Bluetooth to be switched on, and calls [onDone] once
 * the user has answered; [onDone] runs whether or not access was granted.
 */
@Composable
fun rememberBluetoothAccess(onDone: () -> Unit): () -> Unit {
    val scales = LocalContext.current.container.scales
    val done by rememberUpdatedState(onDone)
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { done() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (scales.hasPermissions() && !scales.isBluetoothOn()) enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) else done()
    }
    return remember(scales) {
        {
            when {
                !scales.hasPermissions() -> permissions.launch(scales.requiredPermissions)
                !scales.isBluetoothOn() -> enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                else -> done()
            }
        }
    }
}
