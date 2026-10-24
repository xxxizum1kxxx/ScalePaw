package com.izum1k.scalepaw.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.izum1k.scalepaw.ui.component.LocalLayoutWidth
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion

private const val PANE_ROLE = "pane"
private const val ROLE_LIST = "list"
private const val ROLE_DETAIL = "detail"

/** Entry metadata marking a page that keeps showing while a detail opens beside it. */
val ListPane: Map<String, Any> = mapOf(PANE_ROLE to ROLE_LIST)

/** Entry metadata marking a page that opens beside a [ListPane] on wide windows. */
val DetailPane: Map<String, Any> = mapOf(PANE_ROLE to ROLE_DETAIL)

private val DetailWidth = 420.dp

/**
 * On wide windows, [DetailPane] entries stacked on a [ListPane] entry render beside it, the top one
 * showing. Back pops one detail at a time; the list pane stays put.
 */
class ListDetailSceneStrategy(private val wide: Boolean) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        if (!wide || entries.size < 2) return null
        val detail = entries.last()
        if (detail.metadata[PANE_ROLE] != ROLE_DETAIL) return null
        val list = entries.lastOrNull { it.metadata[PANE_ROLE] != ROLE_DETAIL } ?: return null
        if (list.metadata[PANE_ROLE] != ROLE_LIST) return null
        return ListDetailScene(list, detail, entries.dropLast(1))
    }
}

private class ListDetailScene(
    private val list: NavEntry<NavKey>,
    private val detail: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
) : Scene<NavKey> {
    override val key: Any = ListDetailScene::class to list.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(list, detail)

    override val content: @Composable () -> Unit = {
        val swap = YoinMotion.defaultEffectsSpec<Float>()
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            val listWidth = maxWidth - DetailWidth
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                    CompositionLocalProvider(LocalLayoutWidth provides listWidth) { list.Content() }
                }
                Box(
                    Modifier
                        .width(DetailWidth)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
                        .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                        .clip(YoinContainerShapes.Card)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    CompositionLocalProvider(LocalLayoutWidth provides DetailWidth) {
                        AnimatedContent(
                            targetState = detail,
                            contentKey = { it.contentKey },
                            transitionSpec = { fadeIn(swap) togetherWith fadeOut(swap) },
                            label = "detail",
                        ) { it.Content() }
                    }
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ListDetailScene && other.list == list && other.detail == detail && other.previousEntries == previousEntries

    override fun hashCode(): Int = (list.hashCode() * 31 + detail.hashCode()) * 31 + previousEntries.hashCode()
}
