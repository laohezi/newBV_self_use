package dev.frost819.newbv.app.ui.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.LocateAnchorEffect
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.UserCollectionRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.user.UserCollectionViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** 网格中视频项之前的全宽头部项数量（合集标题块）。 */
private const val COLLECTION_HEADER_ITEMS = 1

/** 视频卡片在 FocusSaver 中的 key 前缀，实际 key 为 `前缀 + 索引`。 */
private const val COLLECTION_ITEM_KEY_PREFIX = "user_collection_"

/**
 * 用户合集详情页。
 *
 * 展示合集标题与合集内视频网格，支持分页加载。
 * 点击视频卡片按「视频详情页」偏好进入详情或直接播放。
 */
fun NavGraphBuilder.userCollectionScreen(navController: NavController) {
    composable<UserCollectionRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<UserCollectionRoute>()
        val viewModel: UserCollectionViewModel = hiltViewModel()
        UserCollectionScreen(
            route = route,
            viewModel = viewModel,
            navController = navController,
        )
    }
}

@Composable
private fun UserCollectionScreen(
    route: UserCollectionRoute,
    viewModel: UserCollectionViewModel,
    navController: NavController,
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val focusSaver = rememberFocusSaver()
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()

    CollectWatchLaterEffects(watchLaterViewModel)

    LaunchedEffect(route.seasonId, route.anchorAid) {
        viewModel.init(
            mid = route.mid,
            seasonId = route.seasonId,
            title = route.title,
            anchorAid = route.anchorAid,
        )
    }

    focusSaver.RestoreFocus()

    LocateAnchorEffect(
        gridState = gridState,
        aids = state.videos.map { it.aid },
        anchorAid = state.anchorAid,
        headerItemCount = COLLECTION_HEADER_ITEMS,
        focusSaver = focusSaver,
        focusKeyPrefix = COLLECTION_ITEM_KEY_PREFIX,
        onLocated = { viewModel.markAnchorLocated() },
    )

    LaunchedEffect(gridState, state.videos.size) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .filter { index ->
                index != null && index >= state.videos.size - 20
            }.collect {
                viewModel.loadVideos()
            }
    }

    TvLazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
            ) {
                Text(
                    text = state.title.ifEmpty { route.title },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.total > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.total} 个视频",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        itemsIndexed(
            items = state.videos,
            key = { _, item -> item.aid },
        ) { index, video ->
            val cardData =
                remember(video) {
                    val durationMs = video.duration * 1000L
                    val progressRatio =
                        if (video.playbackPosition > 0) {
                            video.playbackPosition / 100f
                        } else {
                            null
                        }
                    VideoCardData(
                        avid = video.aid,
                        bvid = video.bvid,
                        title = video.title,
                        cover = video.cover,
                        playString = video.play.takeIf { it != -1 }.toWanString(),
                        danmakuString = video.danmaku.takeIf { it != -1 }.toWanString(),
                        timeString = durationMs.formatHourMinSec(),
                        upName = route.name,
                        upMid = route.mid,
                        pubTime = video.pubTime,
                        progress = progressRatio,
                    )
                }
            SmallVideoCard(
                modifier = Modifier.focusSaverItem(focusSaver, "$COLLECTION_ITEM_KEY_PREFIX$index"),
                data = cardData,
                onClick = { navController.navigateFromVideoCard(cardData) },
                onGoToDetailPage = {
                    navController.navigateFromVideoCard(cardData, forceDetail = true)
                },
                onGoToUpPage = {},
                onAddWatchLater = { watchLaterViewModel.addToView(aid = video.aid) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ListFooterTip(
                isLoading = state.loading,
                isError = state.error,
                hasMore = state.hasMore,
                itemsIsEmpty = state.videos.isEmpty(),
            )
        }
    }
}
