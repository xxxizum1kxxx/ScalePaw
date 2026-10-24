package com.izum1k.scalepaw.feature.googlehealth

import android.app.Activity
import android.os.Bundle

/** Target of the post-consent redirect: finishing reveals the task underneath, which is the settings page. */
class GoogleHealthReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
