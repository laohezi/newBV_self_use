package dev.frost819.newbv.app.viewmodel.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.biliapi.entity.user.SpaceSeasonArchivesData
import dev.frost819.newbv.biliapi.entity.user.SpaceSeasonPage
import dev.frost819.newbv.biliapi.entity.user.SpaceVideo
import dev.frost819.newbv.biliapi.repositories.UserRepository
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val COLLECTION_LOAD_TIMEOUT_MS = 10_000L

/** 定位锚点视频时最多自动翻页的页数，避免为深层视频无限请求。 */
private const val MAX_ANCHOR_PAGES = 10

/**
 * 用户合集详情页 UiState。
 *
 * @property mid 合集所属用户 MID
 * @property seasonId 合集 id
 * @property title 合集标题
 * @property total 合集内视频总数
 * @property videos 合集内视频列表
 * @property loading 是否加载中
 * @property error 是否加载失败
 * @property hasMore 是否还有下一页
 * @property anchorAid 进入后需要定位的视频 aid，0 表示不定位
 * @property anchorLocated 是否已完成定位（UI 滚动+聚焦后置位）
 */
data class UserCollectionUiState(
    val mid: Long = 0L,
    val seasonId: Long = 0L,
    val title: String = "",
    val total: Int = 0,
    val videos: List<SpaceVideo> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val hasMore: Boolean = true,
    val anchorAid: Long = 0L,
    val anchorLocated: Boolean = false,
)

/**
 * 用户合集详情页 ViewModel。
 *
 * 管理合集内视频列表分页，数据来自 Web 合集接口（无 App/gRPC 等价接口）。
 *
 * @param userRepository 用户数据仓库。
 */
@HiltViewModel
class UserCollectionViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("UserCollectionViewModel")

        private val _uiState = MutableStateFlow(UserCollectionUiState())
        val uiState: StateFlow<UserCollectionUiState> = _uiState.asStateFlow()

        private var page = SpaceSeasonPage(pageSize = 30)

        private var anchorPagesLoaded = 0

        /**
         * 初始化合集信息，仅在首次进入或切换合集时调用。
         *
         * 传入 [anchorAid] 时，若目标视频不在首屏数据中会持续翻页直到找到
         * （上限 [MAX_ANCHOR_PAGES] 页），由 UI 侧滚动并聚焦到该视频。
         *
         * @param mid 合集所属用户 MID
         * @param seasonId 合集 id
         * @param title 路由传入的合集标题（用于首屏占位）
         * @param anchorAid 需要定位的视频 aid，0 表示不定位
         */
        fun init(
            mid: Long,
            seasonId: Long,
            title: String = "",
            anchorAid: Long = 0L,
        ) {
            val sameSeason = _uiState.value.seasonId == seasonId && _uiState.value.videos.isNotEmpty()
            anchorPagesLoaded = 0
            if (!sameSeason) {
                page = SpaceSeasonPage(pageSize = 30)
                _uiState.update {
                    it.copy(
                        mid = mid,
                        seasonId = seasonId,
                        title = title,
                        total = 0,
                        videos = emptyList(),
                        hasMore = true,
                        error = false,
                    )
                }
            }
            val anchorChanged = _uiState.value.anchorAid != anchorAid
            _uiState.update {
                it.copy(
                    anchorAid = anchorAid,
                    anchorLocated = if (anchorChanged) false else it.anchorLocated,
                )
            }
            if (sameSeason) {
                locateAnchorIfNeeded()
            } else {
                loadVideos()
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

        /** 若锚点视频尚未出现在已加载列表中，继续翻页查找。 */
        private fun locateAnchorIfNeeded() {
            val state = _uiState.value
            if (state.anchorAid == 0L || state.anchorLocated) return
            if (state.videos.any { it.aid == state.anchorAid }) return
            if (!state.hasMore || anchorPagesLoaded >= MAX_ANCHOR_PAGES) return
            anchorPagesLoaded++
            loadVideos()
        }

        /** 重新加载合集视频列表。 */
        fun refresh() {
            val state = _uiState.value
            if (state.seasonId == 0L) return
            page = SpaceSeasonPage(pageSize = 30)
            anchorPagesLoaded = 0
            _uiState.update {
                it.copy(videos = emptyList(), hasMore = true, error = false, loading = false)
            }
            loadVideos()
        }

        /** 加载下一页合集视频。 */
        fun loadVideos() {
            val state = _uiState.value
            if (state.loading || !state.hasMore || state.seasonId == 0L) return

            _uiState.update { it.copy(loading = true, error = false) }

            viewModelScope.launch {
                runCatching {
                    withTimeout(COLLECTION_LOAD_TIMEOUT_MS) {
                        userRepository.getSpaceSeasonArchives(
                            mid = state.mid,
                            seasonId = state.seasonId,
                            page = page,
                        )
                    }
                }.onSuccess { data: SpaceSeasonArchivesData ->
                    _uiState.update {
                        it.copy(
                            title = data.title.ifEmpty { it.title },
                            total = data.total,
                            videos = it.videos + data.videos,
                            loading = false,
                            hasMore = data.page.hasNext,
                        )
                    }
                    page = data.page
                    locateAnchorIfNeeded()
                }.onFailure { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) {
                        throw error
                    }
                    logger.error(error) { "Failed to load collection archives" }
                    _uiState.update { it.copy(loading = false, error = true) }
                }
            }
        }
    }
