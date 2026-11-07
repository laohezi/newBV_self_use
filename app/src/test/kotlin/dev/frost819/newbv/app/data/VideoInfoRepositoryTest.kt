package dev.frost819.newbv.app.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.biliapi.entity.ApiType
import dev.frost819.newbv.biliapi.entity.user.Author
import dev.frost819.newbv.biliapi.entity.video.Dimension
import dev.frost819.newbv.biliapi.entity.video.RelatedVideo
import dev.frost819.newbv.biliapi.entity.video.UserActions
import dev.frost819.newbv.biliapi.entity.video.VideoDetail
import dev.frost819.newbv.biliapi.entity.video.VideoPage
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.biliapi.entity.video.season.Section
import dev.frost819.newbv.biliapi.entity.video.season.UgcSeason
import dev.frost819.newbv.biliapi.repositories.VideoDetailRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * [VideoInfoRepository] 的单元测试。
 *
 * 验证 StateFlow 传播：视频列表、详情、相关视频、历史进度的更新与重置。
 */
class VideoInfoRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var videoDetailRepository: VideoDetailRepository
    private lateinit var repository: VideoInfoRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        videoDetailRepository = mockk()
        repository = VideoInfoRepository(videoDetailRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeVideoDetail(
        relatedVideos: List<RelatedVideo> = emptyList(),
        history: VideoDetail.History = VideoDetail.History(0, 0),
    ): VideoDetail =
        VideoDetail(
            bvid = "BV1xx",
            aid = 1L,
            cid = 10L,
            cover = "",
            title = "test",
            publishDate = Date(),
            description = "",
            stat = VideoDetail.Stat(0, 0, 0, 0, 0, 0, 0, 0),
            author = Author(0, "", ""),
            pages = emptyList(),
            ugcSeason = null,
            relatedVideos = relatedVideos,
            redirectToEp = false,
            epid = null,
            argueTip = null,
            tags = emptyList(),
            userActions = UserActions(),
            history = history,
        )

    private fun fakeRelatedVideo(aid: Long = 1L): RelatedVideo =
        RelatedVideo(
            aid = aid,
            cid = 10L,
            cover = "",
            title = "related",
            duration = 60,
            author = null,
            jumpToSeason = false,
            epid = null,
            view = 0,
            danmaku = 0,
        )

    private fun fakeVideoPage(cid: Long): VideoPage =
        VideoPage(
            cid = cid,
            index = 1,
            title = "P1",
            duration = 60,
            dimension = Dimension(1920, 1080),
        )

    private fun fakeEpisode(
        aid: Long,
        pageCount: Int,
    ): Episode =
        Episode(
            id = aid.toInt(),
            aid = aid,
            bvid = "BV$aid",
            cid = aid * 10,
            title = "ep$aid",
            longTitle = "ep$aid",
            cover = "",
            duration = 60,
            dimension = Dimension(1920, 1080),
            pages = (1..pageCount).map { fakeVideoPage(aid * 10 + it) },
        )

    /** 构造单分节合集，每个参数为「aid to 该视频的分 P 数」。 */
    private fun fakeUgcSeason(vararg episodes: Pair<Long, Int>): UgcSeason =
        fakeUgcSeasonWithSections(listOf(episodes.toList()))

    /** 构造多分节合集，每个参数为一个分节的 aid 列表（每集 1 个分 P）。 */
    private fun fakeUgcSeason(vararg sections: List<Long>): UgcSeason =
        fakeUgcSeasonWithSections(sections.map { aids -> aids.map { aid -> aid to 1 } })

    private fun fakeUgcSeasonWithSections(sections: List<List<Pair<Long, Int>>>): UgcSeason =
        UgcSeason(
            id = 1,
            title = "合集",
            cover = "",
            sections =
                sections.mapIndexed { index, episodes ->
                    Section(
                        id = index.toLong(),
                        title = "分节$index",
                        episodes = episodes.map { (aid, pageCount) -> fakeEpisode(aid, pageCount) },
                    )
                },
        )

    // ── initial state ────────────────────────────────────────────────

    @Test
    fun `initial state has empty videoList`() {
        assertThat(repository.videoList.value).isEmpty()
    }

    @Test
    fun `initial state has null videoDetail`() {
        assertThat(repository.videoDetail.value).isNull()
    }

    @Test
    fun `initial state has empty relatedVideos`() {
        assertThat(repository.relatedVideos.value).isEmpty()
    }

    @Test
    fun `initial state has null videoSharedState`() {
        assertThat(repository.videoSharedState.value).isNull()
    }

    // ── updateVideoList ──────────────────────────────────────────────

    @Test
    fun `updateVideoList propagates to StateFlow`() =
        runTest(testDispatcher) {
            val items =
                listOf(
                    VideoListItem(aid = 1, cid = 10, title = "视频1"),
                    VideoListItem(aid = 2, cid = 20, title = "视频2"),
                )

            repository.updateVideoList(items)
            advanceUntilIdle()

            assertThat(repository.videoList.value).hasSize(2)
            assertThat(repository.videoList.value[0].title).isEqualTo("视频1")
        }

    @Test
    fun `videoList StateFlow emits on update`() =
        runTest(testDispatcher) {
            repository.videoList.test {
                assertThat(awaitItem()).isEmpty()

                val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
                repository.updateVideoList(items)
                advanceUntilIdle()

                val emitted = awaitItem()
                assertThat(emitted).hasSize(1)
                assertThat(emitted[0].aid).isEqualTo(1)
            }
        }

    // ── updateVideoDetail ────────────────────────────────────────────

    @Test
    fun `updateVideoDetail sets videoDetail`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()

            repository.updateVideoDetail(detail)
            advanceUntilIdle()

            assertThat(repository.videoDetail.value).isEqualTo(detail)
        }

    @Test
    fun `updateVideoDetail sets relatedVideos from detail`() =
        runTest(testDispatcher) {
            val related = listOf(fakeRelatedVideo(1), fakeRelatedVideo(2))
            val detail = fakeVideoDetail(relatedVideos = related)

            repository.updateVideoDetail(detail)
            advanceUntilIdle()

            assertThat(repository.relatedVideos.value).hasSize(2)
            assertThat(repository.relatedVideos.value[0].aid).isEqualTo(1)
        }

    @Test
    fun `updateVideoDetail sets lastPlayedCid and lastPlayedTime in sharedState`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail(history = VideoDetail.History(progress = 300, lastPlayedCid = 999L))

            repository.updateVideoDetail(detail)
            advanceUntilIdle()

            val shared = repository.videoSharedState.value
            assertThat(shared?.lastPlayedCid).isEqualTo(999L)
            assertThat(shared?.lastPlayedTime).isEqualTo(300)
        }

    @Test
    fun `videoDetail StateFlow emits on updateVideoDetail`() =
        runTest(testDispatcher) {
            repository.videoDetail.test {
                assertThat(awaitItem()).isNull()

                val detail = fakeVideoDetail()
                repository.updateVideoDetail(detail)
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(detail)
            }
        }

    // ── loadVideoDetail ──────────────────────────────────────────────

    @Test
    fun `loadVideoDetail success updates all state`() =
        runTest(testDispatcher) {
            val related = listOf(fakeRelatedVideo(10))
            val detail = fakeVideoDetail(relatedVideos = related, history = VideoDetail.History(500, 50L))
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail

            repository.loadVideoDetail(aid = 1L, preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoDetail.value).isEqualTo(detail)
            assertThat(repository.relatedVideos.value).hasSize(1)
            assertThat(repository.videoSharedState.value?.lastPlayedCid).isEqualTo(50L)
            assertThat(repository.videoSharedState.value?.lastPlayedTime).isEqualTo(500)
        }

    @Test
    fun `loadVideoDetail failure does not crash and leaves state unchanged`() =
        runTest(testDispatcher) {
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } throws
                RuntimeException("network error")

            repository.loadVideoDetail(aid = 1L, preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoDetail.value).isNull()
            assertThat(repository.relatedVideos.value).isEmpty()
        }

    @Test
    fun `loadVideoDetail uses ApiType Web by default`() =
        runTest(testDispatcher) {
            val detail = fakeVideoDetail()
            coEvery { videoDetailRepository.getVideoDetail(any(), any(), any()) } returns detail

            repository.loadVideoDetail(aid = 1L, preferApiType = ApiType.Web)
            advanceUntilIdle()

            coVerify { videoDetailRepository.getVideoDetail(1L, ApiType.Web, "") }
        }

    // ── updateUgcPages ───────────────────────────────────────────────

    @Test
    fun `updateUgcPages adds pages to videos with multiple pages`() =
        runTest(testDispatcher) {
            val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
            repository.updateVideoList(items)
            advanceUntilIdle()

            val pages =
                listOf(
                    VideoPage(cid = 10L, index = 1, title = "P1", duration = 60, dimension = Dimension(1920, 1080)),
                    VideoPage(cid = 20L, index = 2, title = "P2", duration = 120, dimension = Dimension(1920, 1080)),
                )
            coEvery { videoDetailRepository.getUgcPages(any(), any()) } returns pages

            repository.updateUgcPages(preferApiType = ApiType.Web)
            advanceUntilIdle()

            val updated = repository.videoList.value
            assertThat(updated[0].ugcPages).isNotNull()
            assertThat(updated[0].ugcPages).hasSize(2)
        }

    @Test
    fun `updateUgcPages does not modify single-page videos`() =
        runTest(testDispatcher) {
            val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
            repository.updateVideoList(items)
            advanceUntilIdle()

            val singlePage =
                listOf(
                    VideoPage(cid = 10L, index = 1, title = "P1", duration = 60, dimension = Dimension(1920, 1080)),
                )
            coEvery { videoDetailRepository.getUgcPages(any(), any()) } returns singlePage

            repository.updateUgcPages(preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoList.value[0].ugcPages).isNull()
        }

    @Test
    fun `updateUgcPages failure leaves item unchanged`() =
        runTest(testDispatcher) {
            val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
            repository.updateVideoList(items)
            advanceUntilIdle()

            coEvery { videoDetailRepository.getUgcPages(any(), any()) } throws RuntimeException("error")

            repository.updateUgcPages(preferApiType = ApiType.Web)
            advanceUntilIdle()

            assertThat(repository.videoList.value[0].ugcPages).isNull()
            assertThat(repository.videoList.value[0].title).isEqualTo("视频1")
        }

    // ── buildSeasonPlaylist ──────────────────────────────────────────

    @Test
    fun `buildSeasonPlaylist returns section episodes for single-page video in collection`() {
        // Given: 单分 P 视频属于合集第一分节
        val detail =
            fakeVideoDetail().copy(
                aid = 11L,
                pages = listOf(fakeVideoPage(11L)),
                ugcSeason = fakeUgcSeason(listOf(11L, 12L), listOf(21L)),
            )

        // When
        val playlist = repository.buildSeasonPlaylist(detail)

        // Then: 只取当前视频所在分节的全部分集
        assertThat(playlist).isNotNull()
        assertThat(playlist!!.map { it.aid }).containsExactly(11L, 12L).inOrder()
    }

    @Test
    fun `buildSeasonPlaylist picks the section containing the video`() {
        // Given: 视频在第二个分节
        val detail =
            fakeVideoDetail().copy(
                aid = 21L,
                pages = listOf(fakeVideoPage(21L)),
                ugcSeason = fakeUgcSeason(listOf(11L, 12L), listOf(21L, 22L)),
            )

        // When
        val playlist = repository.buildSeasonPlaylist(detail)

        // Then
        assertThat(playlist?.map { it.aid }).containsExactly(21L, 22L).inOrder()
    }

    @Test
    fun `buildSeasonPlaylist keeps nested pages of collection episodes`() {
        // Given: 合集中 12 号视频自身有 2 个分 P
        val detail =
            fakeVideoDetail().copy(
                aid = 11L,
                pages = listOf(fakeVideoPage(11L)),
                ugcSeason = fakeUgcSeason(11L to 1, 12L to 2),
            )

        // When
        val playlist = repository.buildSeasonPlaylist(detail)

        // Then: 无分 P 的分集留空待补全，有分 P 的直接带出嵌套列表
        assertThat(playlist?.get(0)?.ugcPages).isNull()
        assertThat(playlist?.get(1)?.ugcPages).hasSize(2)
    }

    @Test
    fun `buildSeasonPlaylist returns null for multi-page video`() {
        // Given: 视频自身多分 P → 分 P 优先
        val detail =
            fakeVideoDetail().copy(
                aid = 11L,
                pages = listOf(fakeVideoPage(11L), fakeVideoPage(12L)),
                ugcSeason = fakeUgcSeason(11L to 2, 13L to 1),
            )

        // When / Then
        assertThat(repository.buildSeasonPlaylist(detail)).isNull()
    }

    @Test
    fun `buildSeasonPlaylist returns null when video misses the collection`() {
        // Given: 视频不在合集任何分节内
        val detail =
            fakeVideoDetail().copy(
                aid = 99L,
                pages = listOf(fakeVideoPage(99L)),
                ugcSeason = fakeUgcSeason(listOf(11L, 12L)),
            )

        // When / Then
        assertThat(repository.buildSeasonPlaylist(detail)).isNull()
    }

    @Test
    fun `buildSeasonPlaylist returns null without ugc season`() {
        // Given: 无合集
        val detail = fakeVideoDetail().copy(aid = 11L, pages = listOf(fakeVideoPage(11L)), ugcSeason = null)

        // When / Then
        assertThat(repository.buildSeasonPlaylist(detail)).isNull()
    }

    @Test
    fun `updateUgcPages skips items that already carry pages`() =
        runTest(testDispatcher) {
            // Given: 分集已由合集详情预填分 P
            val preset = listOf(VideoPage(20L, 2, "P2", 60, Dimension(1920, 1080)))
            repository.updateVideoList(
                listOf(VideoListItem(aid = 1L, cid = 10L, title = "视频1", ugcPages = preset)),
            )
            advanceUntilIdle()

            // When
            repository.updateUgcPages(preferApiType = ApiType.Web)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { videoDetailRepository.getUgcPages(any(), any()) }
            assertThat(repository.videoList.value[0].ugcPages).isEqualTo(preset)
        }

    // ── applySeasonPlaylist ──────────────────────────────────────────

    @Test
    fun `applySeasonPlaylist replaces single-item list with collection`() {
        // Given: 详情页「播放」入口只写了当前视频
        repository.updateVideoList(listOf(VideoListItem(aid = 11L, cid = 110L, title = "single")))
        val detail =
            fakeVideoDetail().copy(
                aid = 11L,
                pages = listOf(fakeVideoPage(11L)),
                ugcSeason = fakeUgcSeason(listOf(11L, 12L)),
            )

        // When
        repository.applySeasonPlaylist(detail)

        // Then
        assertThat(repository.videoList.value.map { it.aid }).containsExactly(11L, 12L).inOrder()
    }

    @Test
    fun `applySeasonPlaylist keeps resolved pages when list already matches`() {
        // Given: 列表已是同一合集，且已补全分 P
        val preset = listOf(VideoPage(20L, 2, "P2", 60, Dimension(1920, 1080)))
        repository.updateVideoList(
            listOf(
                VideoListItem(aid = 11L, cid = 110L, title = "ep11", ugcPages = preset),
                VideoListItem(aid = 12L, cid = 120L, title = "ep12"),
            ),
        )
        val detail =
            fakeVideoDetail().copy(
                aid = 12L,
                pages = listOf(fakeVideoPage(12L)),
                ugcSeason = fakeUgcSeason(listOf(11L, 12L)),
            )

        // When
        repository.applySeasonPlaylist(detail)

        // Then: 同构列表不重建，分 P 不丢
        assertThat(repository.videoList.value[0].ugcPages).isEqualTo(preset)
    }

    @Test
    fun `applySeasonPlaylist leaves list untouched for multi-page video`() {
        // Given
        val origin = listOf(VideoListItem(aid = 11L, cid = 110L, title = "origin"))
        repository.updateVideoList(origin)
        val detail =
            fakeVideoDetail().copy(
                aid = 11L,
                pages = listOf(fakeVideoPage(11L), fakeVideoPage(12L)),
                ugcSeason = fakeUgcSeason(listOf(11L, 12L)),
            )

        // When
        repository.applySeasonPlaylist(detail)

        // Then
        assertThat(repository.videoList.value).isEqualTo(origin)
    }

    // ── updateHistory ────────────────────────────────────────────────

    @Test
    fun `updateHistory sets lastPlayedCid and lastPlayedTime in sharedState`() =
        runTest(testDispatcher) {
            repository.updateHistory(progress = 600, lastPlayedCid = 200L)
            advanceUntilIdle()

            assertThat(repository.videoSharedState.value?.lastPlayedCid).isEqualTo(200L)
            assertThat(repository.videoSharedState.value?.lastPlayedTime).isEqualTo(600)
        }

    @Test
    fun `updateHistory with negative progress sets negative value`() =
        runTest(testDispatcher) {
            repository.updateHistory(progress = -1, lastPlayedCid = 0L)
            advanceUntilIdle()

            assertThat(repository.videoSharedState.value?.lastPlayedTime).isEqualTo(-1)
        }

    @Test
    fun `videoSharedState emits lastPlayedCid on updateHistory`() =
        runTest(testDispatcher) {
            repository.videoSharedState.test {
                assertThat(awaitItem()).isNull()

                repository.updateHistory(progress = 100, lastPlayedCid = 42L)
                advanceUntilIdle()

                val shared = awaitItem()
                assertThat(shared?.lastPlayedCid).isEqualTo(42L)
            }
        }

    // ── reset ────────────────────────────────────────────────────────

    @Test
    fun `reset clears all state`() =
        runTest(testDispatcher) {
            val items = listOf(VideoListItem(aid = 1, cid = 10, title = "视频1"))
            repository.updateVideoList(items)
            repository.updateVideoDetail(fakeVideoDetail())
            repository.updateHistory(progress = 100, lastPlayedCid = 50L)
            advanceUntilIdle()

            assertThat(repository.videoList.value).isNotEmpty()
            assertThat(repository.videoDetail.value).isNotNull()

            repository.reset()
            advanceUntilIdle()

            assertThat(repository.videoList.value).isEmpty()
            assertThat(repository.videoDetail.value).isNull()
            assertThat(repository.relatedVideos.value).isEmpty()
            assertThat(repository.videoSharedState.value).isNull()
        }
}
