package com.gpo.scalepaw.ble.openscale.util

import android.util.Log
import com.gpo.scalepaw.BuildConfig

/** Logcat sink for the ported scale drivers; debug and verbose lines only in debug builds. */
object LogManager {
    private const val PREFIX = "ScalePaw/"

    fun v(tag: String?, message: String) {
        if (BuildConfig.DEBUG) Log.v(PREFIX + tag, message)
    }

    fun d(tag: String?, message: String) {
        if (BuildConfig.DEBUG) Log.d(PREFIX + tag, message)
    }

    fun i(tag: String?, message: String) {
        Log.i(PREFIX + tag, message)
    }

    fun w(tag: String?, message: String, throwable: Throwable? = null) {
        Log.w(PREFIX + tag, message, throwable)
    }

    fun e(tag: String?, message: String, throwable: Throwable? = null) {
        Log.e(PREFIX + tag, message, throwable)
    }
}
