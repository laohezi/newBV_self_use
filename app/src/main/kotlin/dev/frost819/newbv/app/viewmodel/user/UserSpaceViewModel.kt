package dev.frost819.newbv.app.viewmodel.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.biliapi.entity.user.SpaceSeason
import dev.frost819.newbv.biliapi.entity.user.SpaceSeasonPage
import dev.frost819.newbv.biliapi.entity.user.SpaceVideo
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoOrder
import dev.frost819.newbv.biliapi.entity.user.SpaceVideoPage
import dev.frost819.newbv.biliapi.repositories.UserRepository
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import dev.frost819.newbv.biliapi.entity.ApiType as BiliApiType
import dev.frost819.newbv.data.datastore.ApiType as DataApiType

private const val LOAD_TIMEOUT_MS = 10_000L

/** 定位锚点视频时最多自动翻页的页数，避免为深层视频无限请求。 */
private const val MAX_ANCHOR_PAGES = 10

/**
 * 用户空间页内容 Tab。
 */
enum class UserSpaceTab {
    /** 投稿视频。 */
    Uploads,

    /** 合集。 */
    Seasons,
}

/**
 * 用户空间页 UiState。
 *
 * 与原版 BV 一致：用户名和头像由来源页通过路由传入，不单独调用 API 获取。
 *
 * @property anchorAid 进入后需要定位的视频 aid，0 表示不定位
 * @property anchorLocated 是否已完成定位（UI 滚动+聚焦后置位），已定位则不再自动翻页
 */
data class UserSpaceUiState(
    val mid: Long = 0L,
    val name: String = "",
    val face: String = "",
    val videos: List<SpaceVideo> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val hasMore: Boolean = true,
    val selectedTab: UserSpaceTab = UserSpaceTab.Uploads,
    val seasons: List<SpaceSeason> = emptyList(),
    val seasonsLoading: Boolean = false,
    val seasonsError: Boolean = false,
    val seasonsHasMore: Boolean = true,
    val anchorAid: Long = 0L,
    val anchorLocated: Boolean = false,
)

/**
 * 用户空间页 ViewModel。
 *
 * 管理用户投稿视频列表分页。
 * 用户名和头像由 [init] 从路由参数传入，不调用 getUserInfo API（与原版 BV 行为一致）。
 *
 * @param userRepository 用户数据仓库。
 */
@HiltViewModel
class UserSpaceViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("UserSpaceViewModel")

        private val _uiState = MutableStateFlow(UserSpaceUiState())
        val uiState: StateFlow<UserSpaceUiState> = _uiState.asStateFlow()

        private var videoPage = SpaceVideoPage()

        private var seasonPage = SpaceSeasonPage()

        private var anchorPagesLoaded = 0

        private fun prefApiType(): BiliApiType =
            when (Prefs.apiType) {
                DataApiType.Web -> BiliApiType.Web
                DataApiType.App -> BiliApiType.App
            }

        /**
         * 初始化：设置用户信息（由路由传入），加载投稿视频列表。
         *
         * 传入 [anchorAid] 时，若目标视频不在首屏数据中会持续翻页直到找到
         * （上限 [MAX_ANCHOR_PAGES] 页），由 UI 侧滚动并聚焦到该视频。
         *
         * @param mid 用户 MID
         * @param name 用户名（从详情页/播放器/卡片传入）
         * @param face 头像 URL（可选）
         * @param anchorAid 需要定位的视频 aid，0 表示不定位
         */
        fun init(
            mid: Long,
            name: String = "",
            face: String? = null,
            anchorAid: Long = 0L,
        ) {
            val previous = _uiState.value
            val sameUser = previous.mid == mid && previous.videos.isNotEmpty()
            val anchorChanged = previous.anchorAid != anchorAid
            _uiState.update {
                it.copy(
                    mid = mid,
                    name = name,
                    face = face ?: "",
                    anchorAid = anchorAid,
                    anchorLocated = if (anchorChanged) false else it.anchorLocated,
                )
            }
            if (sameUser) {
                // 同一 UP 主重复进入：数据已就绪，只需确保锚点视频被加载
                anchorPagesLoaded = 0
                locateAnchorIfNeeded(mid)
            } else {
                anchorPagesLoaded = 0
                loadVideos(mid)
            }
        }

        /**
         * 标记锚点定位完成，停止为定位而自动翻页。
         *
         * 由 UI 在滚动并聚焦到目标视频后调用。
         */
        fun markAnchorLocated() {
            _uiState.update { it.copy(anchorLocated = true) }
        }

        /**
         * 若锚点视频尚未出现在已加载列表中，继续翻页查找。
         *
         * @param mid 用户 MID
         */
        private fun locateAnchorIfNeeded(mid: Long) {
            val state = _uiState.value
            if (state.anchorAid == 0L || state.anchorLocated) return
            if (state.videos.any { it.aid == state.anchorAid }) return
            if (!state.hasMore || anchorPagesLoaded >= MAX_ANCHOR_PAGES) return
            anchorPagesLoaded++
            loadVideos(mid)
        }

        /**
         * 切换内容 Tab，首次切到合集时触发加载。
         *
         * @param mid 用户 MID
         * @param tab 目标 Tab
         */
        fun selectTab(
            mid: Long,
            tab: UserSpaceTab,
        ) {
            if (_uiState.value.selectedTab == tab) return
            _uiState.update { it.copy(selectedTab = tab) }
            if (tab == UserSpaceTab.Seasons && _uiState.value.seasons.isEmpty()) {
                loadSeasons(mid)
            }
        }

        /** 重新加载当前 Tab 的数据。 */
        fun refreshCurrentTab(mid: Long) {
            when (_uiState.value.selectedTab) {
                UserSpaceTab.Uploads -> refresh(mid)
                UserSpaceTab.Seasons -> refreshSeasons(mid)
            }
        }

        fun refresh(mid: Long) {
            videoPage = SpaceVideoPage()
            anchorPagesLoaded = 0
            _uiState.update {
                it.copy(
                    videos = emptyList(),
                    hasMore = true,
                    error = false,
                )
            }
            loadVideos(mid)
        }

        fun loadVideos(mid: Long) {
            if (_uiState.value.loading || !_uiState.value.hasMore) return

            _uiState.update { it.copy(loading = true, error = false) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        userRepository.getSpaceVideos(
                            mid = mid,
                            order = SpaceVideoOrder.PubDate,
                            page = videoPage,
                            preferApiType = prefApiType(),
                        )
                    }
                }.onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            videos = it.videos + data.videos,
                            loading = false,
                            hasMore = data.page.hasNext,
                        )
                    }
                    videoPage = data.page
                    locateAnchorIfNeeded(mid)
                }.onFailure { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) {
                        throw error
                    }
                    logger.error(error) { "Failed to load space videos" }
                    _uiState.update { it.copy(loading = false, error = true) }
                }
            }
        }

        fun refreshSeasons(mid: Long) {
            seasonPage = SpaceSeasonPage()
            _uiState.update {
                it.copy(
                    seasons = emptyList(),
                    seasonsHasMore = true,
                    seasonsError = false,
                )
            }
            loadSeasons(mid)
        }

        /**
         * 加载合集列表（分页）。
         *
         * @param mid 用户 MID
         */
        fun loadSeasons(mid: Long) {
            val state = _uiState.value
            if (state.seasonsLoading || !state.seasonsHasMore) return

            _uiState.update { it.copy(seasonsLoading = true, seasonsError = false) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(LOAD_TIMEOUT_MS) {
                        userRepository.getSpaceSeasons(mid = mid, page = seasonPage)
                    }
                }.onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            seasons = it.seasons + data.seasons,
                            seasonsLoading = false,
                            seasonsHasMore = data.page.hasNext,
                        )
                    }
                    seasonPage = data.page
                }.onFailure { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) {
                        throw error
                    }
                    logger.error(error) { "Failed to load space seasons" }
                    _uiState.update { it.copy(seasonsLoading = false, seasonsError = true) }
                }
            }
        }
    }
