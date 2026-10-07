package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaSource
import com.rockbyte.vicu.repo.WorksLibraryState
import com.rockbyte.vicu.ui.theme.VicuTheme
import kotlinx.coroutines.launch

/** Both media entry points share source switching and independent grid positions. */
@Composable
internal fun MediaSourcePager(
    items: List<MediaItem>,
    loading: Boolean,
    hasAccess: Boolean?,
    works: WorksLibraryState,
    onMediaClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    initialSource: MediaSource = MediaSource.SYSTEM,
    permissionPrompt: @Composable () -> Unit,
) {
    val pager = rememberPagerState(initialPage = initialSource.ordinal, pageCount = { 2 })
    val systemGrid = rememberLazyGridState()
    val worksGrid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onRefresh)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = VicuTheme.dimensions.screenGutter)) {
            MediaSource.entries.forEach { source ->
                val selected = pager.currentPage == source.ordinal
                Column(Modifier.weight(1f).selectable(selected, role = Role.Tab,
                    onClick = { scope.launch { pager.animateScrollToPage(source.ordinal) } })
                    .semantics(mergeDescendants = true) {}) {
                    Box(Modifier.fillMaxWidth().heightIn(min = VicuTheme.dimensions.navigationTouchSize),
                        contentAlignment = Alignment.Center) {
                        BasicText(stringResource(if (source == MediaSource.SYSTEM) R.string.media_source_system else R.string.media_source_works),
                            style = VicuTheme.typography.button.copy(color = if (selected) VicuTheme.colors.onSurface else VicuTheme.colors.onSurfaceVariant))
                    }
                    Box(Modifier.fillMaxWidth().height(VicuTheme.dimensions.cardBorderWidth * 2)
                        .background(if (selected) VicuTheme.colors.primary else VicuTheme.colors.outlineVariant))
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            if (page == MediaSource.SYSTEM.ordinal) {
                when (hasAccess) {
                    true -> MediaGrid(items, loading, onMediaClick, gridState = systemGrid)
                    false -> permissionPrompt()
                    null -> Unit
                }
            } else if (works.failed) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PrimaryButton(stringResource(R.string.works_load_retry), onRefresh)
                }
            } else {
                MediaGrid(works.items, works.loading, onMediaClick,
                    emptyText = stringResource(R.string.works_empty), gridState = worksGrid)
            }
        }
    }
}
