package com.gpo.scalepaw.feature.users

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.scalepaw.ui.component.ScaleFab
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.ui.component.Fmt
import com.gpo.scalepaw.ui.component.ScalePage
import com.gpo.scalepaw.ui.component.UserBadge
import com.gpo.scalepaw.ui.navigation.LocalNavigator
import com.gpo.scalepaw.ui.navigation.Route
import com.gpo.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols

@Composable
fun UserSettingsScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val state by context.container.session.state.collectAsStateWithLifecycle()

    ScalePage(
        title = stringResource(R.string.settings_users),
        floatingAction = {
            ScaleFab(stringResource(R.string.user_add), PiliSymbols.PersonAdd, onClick = { nav.push(Route.UserEdit()) })
        },
    ) {
        items(state.users, key = { it.id }) { user ->
            val now = System.currentTimeMillis()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(YoinContainerShapes.ListRow)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { nav.push(Route.UserEdit(user.id)) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserBadge(user.name, user.avatar, size = 48.dp, selected = user.id == state.user?.id)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.user_summary,
                            stringResource(user.gender.label),
                            user.ageOn(now),
                            Fmt.number(user.heightCm, 0),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (user.id == state.user?.id) Icon(YoinSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
