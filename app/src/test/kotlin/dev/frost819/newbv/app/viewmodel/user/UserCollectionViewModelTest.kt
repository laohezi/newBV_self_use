package dev.frost819.newbv.app.viewmodel.user

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.entity.user.SpaceSeasonArchivesData
import dev.frost819.newbv.biliapi.entity.user.SpaceSeasonPage
import dev.frost819.newbv.biliapi.entity.user.SpaceVideo
import dev.frost819.newbv.biliapi.repositories.UserRepository
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

/**
 * [UserCollectionViewModel] 的单元测试。
 *
 * 覆盖合集内视频分页加载，以及「进入合集时定位当前视频」的自动翻页逻辑。
 */
class UserCollectionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: UserCollectionViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UserCollectionViewModel = UserCollectionViewModel(userRepository = userRepository)

    private fun fakeVideo(aid: Long) =
        SpaceVideo(
            aid = aid,
            bvid = "BV$aid",
            title = "视频$aid",
            cover = "https://example.com/cover$aid.jpg",
            author = "测试UP",
            duration = 600,
            play = 10000,
            danmaku = 500,
            pubTime = "2024-01-01",
        )

    private fun fakeArchives(
        videos: List<SpaceVideo> = listOf(fakeVideo(1), fakeVideo(2)),
        hasNext: Boolean = true,
    ) = SpaceSeasonArchivesData(
        seasonId = 100L,
        title = "测试合集",
        cover = "",
        total = 2,
        videos = videos,
        page = SpaceSeasonPage(hasNext = hasNext, total = 2),
    )

    @Test
    fun `init loads collection videos`() {
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceSeasonArchives(any(), any(), any()) } returns fakeArchives()

            viewModel = createViewModel()
            viewModel.init(1L, 100L, "测试合集")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.videos).hasSize(2)
            assertThat(state.loading).isFalse()
            assertThat(state.error).isFalse()
        }
    }

    @Test
    fun `init with anchorAid pages until anchor video is found`() {
        runTest(testDispatcher) {
            var call = 0
            coEvery { userRepository.getSpaceSeasonArchives(any(), any(), any()) } coAnswers {
                call++
                if (call < 3) {
                    fakeArchives(videos = listOf(fakeVideo(call.toLong())), hasNext = true)
                } else {
                    fakeArchives(videos = listOf(fakeVideo(99)), hasNext = true)
                }
            }

            viewModel = createViewModel()
            viewModel.init(1L, 100L, "测试合集", anchorAid = 99L)
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.videos
                    .map { it.aid },
            ).contains(99L)
            coVerify(exactly = 3) { userRepository.getSpaceSeasonArchives(any(), any(), any()) }
        }
    }

    @Test
    fun `init with anchorAid stops paging once anchor is in the first page`() {
        runTest(testDispatcher) {
            coEvery { userRepository.getSpaceSeasonArchives(any(), any(), any()) } returns
                fakeArchives(videos = listOf(fakeVideo(1), fakeVideo(2)), hasNext = true)

            viewModel = createViewModel()
            viewModel.init(1L, 100L, "测试合集", anchorAid = 2L)
            advanceUntilIdle()

            coVerify(exactly = 1) { userRepository.getSpaceSeasonArchives(any(), any(), any()) }
        }
    }

    @Test
    fun `markAnchorLocated stops further auto paging`() {
        runTest(testDispatcher) {
            var call = 0
            coEvery { userRepository.getSpaceSeasonArchives(any(), any(), any()) } coAnswers {
                call++
                fakeArchives(videos = listOf(fakeVideo(call.toLong())), hasNext = true)
            }

            viewModel = createViewModel()
            viewModel.init(1L, 100L, "测试合集", anchorAid = 9999L)
            advanceUntilIdle()

            val callsBeforeMark = call
            viewModel.markAnchorLocated()
            viewModel.init(1L, 100L, "测试合集", anchorAid = 9999L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.anchorLocated).isTrue()
            assertThat(call).isEqualTo(callsBeforeMark)
        }
    }
}
