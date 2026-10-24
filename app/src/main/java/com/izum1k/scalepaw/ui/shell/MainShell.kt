package com.izum1k.scalepaw.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.model.User
import com.izum1k.scalepaw.feature.chart.ChartScreen
import com.izum1k.scalepaw.feature.insights.InsightsScreen
import com.izum1k.scalepaw.feature.overview.OverviewScreen
import com.izum1k.scalepaw.feature.statistics.StatisticsScreen
import com.izum1k.scalepaw.feature.table.TableScreen
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.LoadingState
import com.izum1k.scalepaw.ui.component.LocalBottomBarInset
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.SheetAction
import com.izum1k.scalepaw.ui.component.UserBadge
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.YoinMotion
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.launch

/** The five main tabs under one floating bar; settings and detail pages are pushed. */
@Composable
fun MainShell() {
    val context = LocalContext.current
    val state by context.container.session.state.collectAsState()
    when {
        !state.loaded -> LoadingState()
        state.users.isEmpty() -> Welcome()
        else -> Tabs()
    }
}

@Composable
private fun Tabs() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val container = LocalContext.current.container
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(container, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { container.scales.watchPresence() }
    }
    val tabs = listOf(
        NavTab(stringResource(R.string.tab_overview), YoinSymbols.Home, YoinSymbols.HomeFilled),
        NavTab(stringResource(R.string.tab_chart), ScaleSymbols.Chart),
        NavTab(stringResource(R.string.tab_table), ScaleSymbols.Table),
        NavTab(stringResource(R.string.tab_statistics), ScaleSymbols.Statistics),
        NavTab(stringResource(R.string.tab_insights), YoinSymbols.Insights),
    )
    val fadeInSpec = YoinMotion.defaultEffectsSpec<Float>()
    val scaleInSpec = YoinMotion.defaultSpatialSpec<Float>()
    val fadeOutSpec = YoinMotion.fastEffectsSpec<Float>()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomBarInset provides FloatingBarInset + WeighBarInset + 8.dp) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(fadeInSpec) + scaleIn(scaleInSpec, initialScale = 0.98f)) togetherWith fadeOut(fadeOutSpec)
                },
                label = "tab",
            ) { index ->
                when (index) {
                    0 -> OverviewScreen()
                    1 -> ChartScreen()
                    2 -> TableScreen()
                    3 -> StatisticsScreen()
                    else -> InsightsScreen()
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            WeighBar()
            FloatingNavBar(tabs = tabs, selected = tab, onSelect = { tab = it })
        }
    }
}

/** Page header of every tab: title, the person being shown, scale status and settings; [showSync] adds Google Health sync. */
@Composable
fun ShellHeader(title: String, modifier: Modifier = Modifier, showSync: Boolean = false) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val state by context.container.session.state.collectAsState()
    var pickUser by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (showSync) GoogleHealthSyncButton()
        ScalePresenceButton()
        state.user?.let { user ->
            IconButton(onClick = { pickUser = true }, shapes = IconButtonDefaults.shapes()) {
                UserBadge(user.name, user.avatar, size = 32.dp)
            }
        }
        IconButton(onClick = { nav.push(Route.Settings) }, shapes = IconButtonDefaults.shapes()) {
            Icon(YoinSymbols.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
    if (pickUser) {
        UserSwitcherSheet(state.users, state.user?.id, onDismiss = { pickUser = false })
    }
}

@Composable
private fun UserSwitcherSheet(users: List<User>, selectedId: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(users, key = { it.id }) { user ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            scope.launch { context.container.repository.selectUser(user.id) }
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserBadge(user.name, user.avatar, size = 44.dp, selected = user.id == selectedId)
                    Spacer(Modifier.width(16.dp))
                    Text(user.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (user.id == selectedId) Icon(YoinSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                SheetAction(YoinSymbols.Person, stringResource(R.string.settings_users), onClick = {
                    onDismiss()
                    nav.push(Route.SettingsUsers)
                })
            }
        }
    }
}

@Composable
private fun Welcome() {
    val nav = LocalNavigator.current
    Box(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyState(icon = ScaleSymbols.Scale, title = stringResource(R.string.app_name))
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.size(16.dp))
            Button(onClick = { nav.push(Route.UserEdit()) }) { Text(stringResource(R.string.welcome_create_profile)) }
        }
    }
}
