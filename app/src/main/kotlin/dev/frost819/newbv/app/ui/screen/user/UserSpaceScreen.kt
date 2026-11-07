package dev.frost819.newbv.app.ui.screen.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.LocateAnchorEffect
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.videocard.CollectionCard
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.UserCollectionRoute
import dev.frost819.newbv.app.ui.navigation.UserSpaceRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.user.UserSpaceTab
import dev.frost819.newbv.app.viewmodel.user.UserSpaceViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** 网格中视频项之前的全宽头部项数量（用户信息 + Tab 行）。 */
private const val USER_SPACE_HEADER_ITEMS = 2

/** 视频卡片在 FocusSaver 中的 key 前缀，实际 key 为 `前缀 + 索引`。 */
private const val USER_SPACE_ITEM_KEY_PREFIX = "user_space_"

/**
 * 用户空间页。
 *
 * 展示用户信息（头像、昵称），并提供「投稿」「合集」两个 Tab：
 * 投稿为视频网格，合集为 UGC 合集卡片网格，点击合集进入合集详情页。
 * 用户名和头像由路由参数传入，不单独调用 API 获取（与原版 BV 行为一致）。
 */
fun NavGraphBuilder.userSpaceScreen(navController: NavController) {
    composable<UserSpaceRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<UserSpaceRoute>()
        val viewModel: UserSpaceViewModel = hiltViewModel()
        UserSpaceScreen(
            mid = route.mid,
            name = route.name ?: "",
            face = route.face,
            anchorAid = route.anchorAid,
            viewModel = viewModel,
            navController = navController,
        )
    }
}

@Composable
private fun UserSpaceScreen(
    mid: Long,
    name: String,
    face: String?,
    anchorAid: Long,
    viewModel: UserSpaceViewModel,
    navController: NavController,
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val focusSaver = rememberFocusSaver()
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()

    CollectWatchLaterEffects(watchLaterViewModel)

    LaunchedEffect(mid, anchorAid) {
        viewModel.init(mid, name, face, anchorAid)
    }

    focusSaver.RestoreFocus()

    // 仅在「投稿」Tab 定位锚点视频；网格前有头部 + Tab 行两个 span 全宽项
    LocateAnchorEffect(
        gridState = gridState,
        aids = state.videos.map { it.aid },
        anchorAid = state.anchorAid,
        headerItemCount = USER_SPACE_HEADER_ITEMS,
        focusSaver = focusSaver,
        focusKeyPrefix = USER_SPACE_ITEM_KEY_PREFIX,
        enabled = state.selectedTab == UserSpaceTab.Uploads,
        onLocated = { viewModel.markAnchorLocated() },
    )

    LaunchedEffect(gridState, state.selectedTab, state.videos.size, state.seasons.size) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .filter { index ->
                val count =
                    when (state.selectedTab) {
                        UserSpaceTab.Uploads -> state.videos.size
                        UserSpaceTab.Seasons -> state.seasons.size
                    }
                index != null && index >= count - 20
            }.collect {
                when (state.selectedTab) {
                    UserSpaceTab.Uploads -> viewModel.loadVideos(mid)
                    UserSpaceTab.Seasons -> viewModel.loadSeasons(mid)
                }
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
            UserSpaceHeader(state = state)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            UserSpaceTabRow(
                selectedTab = state.selectedTab,
                focusSaver = focusSaver,
                onSelect = { tab -> viewModel.selectTab(mid, tab) },
            )
        }

        when (state.selectedTab) {
            UserSpaceTab.Seasons -> {
                itemsIndexed(
                    items = state.seasons,
                    key = { _, item -> item.seasonId },
                ) { index, season ->
                    CollectionCard(
                        modifier = Modifier.focusSaverItem(focusSaver, "user_season_$index"),
                        season = season,
                        onClick = {
                            navController.navigate(
                                UserCollectionRoute(
                                    mid = mid,
                                    seasonId = season.seasonId,
                                    title = season.title,
                                    name = state.name,
                                ),
                            )
                        },
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListFooterTip(
                        isLoading = state.seasonsLoading,
                        isError = state.seasonsError,
                        hasMore = state.seasonsHasMore,
                        itemsIsEmpty = state.seasons.isEmpty(),
                    )
                }
            }

            UserSpaceTab.Uploads -> {
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
                                upName = video.author,
                                upMid = mid,
                                pubTime = video.pubTime,
                                progress = progressRatio,
                            )
                        }
                    SmallVideoCard(
                        modifier = Modifier.focusSaverItem(focusSaver, "$USER_SPACE_ITEM_KEY_PREFIX$index"),
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
    }
}

@Composable
private fun UserSpaceHeader(state: dev.frost819.newbv.app.viewmodel.user.UserSpaceUiState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.face.isNotEmpty()) {
            AsyncImage(
                model = state.face,
                contentDescription = state.name,
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UserSpaceTabRow(
    selectedTab: UserSpaceTab,
    focusSaver: FocusSaver,
    onSelect: (UserSpaceTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        UserSpaceTabItem(
            text = "投稿",
            selected = selectedTab == UserSpaceTab.Uploads,
            modifier = Modifier.focusSaverItem(focusSaver, "user_space_tab_uploads"),
            onClick = { onSelect(UserSpaceTab.Uploads) },
        )
        UserSpaceTabItem(
            text = "合集",
            selected = selectedTab == UserSpaceTab.Seasons,
            modifier = Modifier.focusSaverItem(focusSaver, "user_space_tab_seasons"),
            onClick = { onSelect(UserSpaceTab.Seasons) },
        )
    }
}

@Composable
private fun UserSpaceTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                focusedContainerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                pressedContainerColor = MaterialTheme.colorScheme.primary,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.border),
                        shape = MaterialTheme.shapes.medium,
                    ),
            ),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
