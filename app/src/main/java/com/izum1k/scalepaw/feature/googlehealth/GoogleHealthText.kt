package com.izum1k.scalepaw.feature.googlehealth

import android.content.Context
import androidx.annotation.StringRes
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.data.googlehealth.GoogleHealthError
import com.izum1k.scalepaw.data.googlehealth.SyncResult

@get:StringRes
internal val GoogleHealthError.messageRes: Int
    get() = when (this) {
        GoogleHealthError.NOT_CONFIGURED -> R.string.google_health_error_not_configured
        GoogleHealthError.NOT_CONNECTED -> R.string.google_health_error_not_connected
        GoogleHealthError.CANCELLED -> R.string.google_health_error_cancelled
        GoogleHealthError.DENIED -> R.string.google_health_error_denied
        GoogleHealthError.TIMEOUT -> R.string.google_health_error_timeout
        GoogleHealthError.ACCOUNT_NOT_LINKED -> R.string.google_health_error_not_linked
        GoogleHealthError.REAUTH -> R.string.google_health_error_reauth
        GoogleHealthError.NETWORK -> R.string.google_health_error_network
        GoogleHealthError.RATE_LIMITED -> R.string.google_health_error_rate_limited
        GoogleHealthError.SERVER -> R.string.google_health_error_server
        GoogleHealthError.FAILED -> R.string.google_health_error_failed
    }

internal fun Context.googleHealthErrorText(error: GoogleHealthError, detail: String?): String {
    val base = getString(error.messageRes)
    val showDetail = detail != null && (error == GoogleHealthError.FAILED || error == GoogleHealthError.SERVER)
    return if (showDetail) "$base\n$detail" else base
}

internal fun Context.googleHealthResultText(result: SyncResult): String = when (result) {
    is SyncResult.Done -> with(result.outcome) {
        if (changed == 0) getString(R.string.google_health_sync_nothing) else getString(R.string.google_health_sync_result, created, updated, deleted)
    }
    is SyncResult.Failed -> googleHealthErrorText(result.error.error, result.error.message?.takeIf { it != result.error.error.name })
}
