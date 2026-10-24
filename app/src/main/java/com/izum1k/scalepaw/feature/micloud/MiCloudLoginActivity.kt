package com.izum1k.scalepaw.feature.micloud

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.izum1k.scalepaw.BuildConfig
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.data.micloud.MiCloudClient

/**
 * Xiaomi's own sign-in page in a plain View hierarchy (a WebView inside Compose stays black here).
 * It handles captchas and verification itself; we only hand back the `account.xiaomi.com` cookies.
 */
class MiCloudLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.micloud_web_title)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val cookies = CookieManager.getInstance()
        if (savedInstanceState == null) cookies.removeAllCookies(null)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    capture(cookies) || request.url.host == MiCloudClient.STS_HOST

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    capture(cookies)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    capture(cookies)
                }
            }
        }
        val frame = FrameLayout(this).apply { addView(webView) }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        setContentView(frame)
        if (savedInstanceState == null) webView.loadUrl(MiCloudClient.WEB_LOGIN_URL) else webView.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun capture(cookies: CookieManager): Boolean {
        if (done) return true
        val account = parseCookies(cookies.getCookie(MiCloudClient.ACCOUNT_URL))
        if (account["passToken"].isNullOrEmpty()) return false
        done = true
        webView.stopLoading()
        cookies.removeAllCookies(null)
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_COOKIES, HashMap(account)))
        finish()
        return true
    }

    /** Launches the sign-in page; yields the account cookies, or null when the user backs out. */
    class Contract : ActivityResultContract<Unit, Map<String, String>?>() {
        override fun createIntent(context: Context, input: Unit) = Intent(context, MiCloudLoginActivity::class.java)

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        override fun parseResult(resultCode: Int, intent: Intent?): Map<String, String>? =
            if (resultCode == Activity.RESULT_OK) intent?.getSerializableExtra(EXTRA_COOKIES) as? HashMap<String, String> else null
    }

    private companion object {
        const val EXTRA_COOKIES = "cookies"

        fun parseCookies(raw: String?): Map<String, String> = raw.orEmpty()
            .split(';')
            .mapNotNull { part -> part.trim().split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() } }
            .associate { it[0] to it[1] }
    }
}
