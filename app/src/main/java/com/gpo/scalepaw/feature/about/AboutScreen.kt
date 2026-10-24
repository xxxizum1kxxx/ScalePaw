package com.gpo.scalepaw.feature.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpo.scalepaw.BuildConfig
import com.gpo.scalepaw.R
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.ScaleSymbols
import com.gpo.scalepaw.ui.component.Section
import com.gpo.scalepaw.ui.component.SettingRow
import com.gpo.yoin.symbols.YoinSymbols

private data class Credit(val name: String, val license: String, val url: String, val role: Int)

private val Credits = listOf(
    Credit("openScale", "GPL-3.0", "https://github.com/oliexdev/openScale", R.string.credit_openscale),
    Credit("Yoin", "Apache-2.0", "https://github.com/p2o51/Yoin", R.string.credit_yoin),
    Credit("PiliPaw", "GPL-3.0", "https://github.com/p2o51/PiliPaw", R.string.credit_pilipaw),
    Credit("material-color-utilities", "Apache-2.0", "https://github.com/material-foundation/material-color-utilities", R.string.credit_mcu),
    Credit("Google Sans Flex", "OFL-1.1", "https://fonts.google.com/specimen/Google+Sans+Flex", R.string.credit_font),
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    ScalePage(title = stringResource(R.string.about)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ScaleSymbols.Scale, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        item {
            Section {
                SettingRow(
                    title = stringResource(R.string.about_license),
                    value = "GPL-3.0-or-later",
                    icon = YoinSymbols.Info,
                    onClick = { open("https://www.gnu.org/licenses/gpl-3.0.html") },
                )
            }
        }
        item {
            Section(title = stringResource(R.string.about_credits)) {
                Credits.forEach { c ->
                    SettingRow(
                        title = c.name,
                        value = "${stringResource(c.role)}\n${c.license}",
                        icon = YoinSymbols.Launch,
                        onClick = { open(c.url) },
                    )
                }
            }
        }
    }
}
