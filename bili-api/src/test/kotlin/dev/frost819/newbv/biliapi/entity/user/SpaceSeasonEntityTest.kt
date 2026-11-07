package dev.frost819.newbv.biliapi.entity.user

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.biliapi.http.entity.user.WebSpaceSeasonArchivesData
import dev.frost819.newbv.biliapi.http.entity.user.WebSpaceSeasonListData
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * [SpaceSeason]、[SpaceSeasonListData]、[SpaceSeasonArchivesData] 的单元测试。
 *
 * 覆盖 Web 合集接口 DTO 的 JSON 反序列化（字段命名映射）与 HTTP→Domain 转换，
 * 不依赖真实网络。
 */
class SpaceSeasonEntityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes seasons series list response`() {
        val raw =
            """
            {
                "items_lists": {
                  "page": { "page_num": 1, "page_size": 20, "total": 32 },
                  "seasons_list": [
                    {
                      "meta": {
                        "category": 1,
                        "cover": "https://i0.hdslb.com/cover.jpg",
                        "description": "简介",
                        "mid": 946974,
                        "name": "合集·样片日记",
                        "ptime": 1700000000,
                        "season_id": 2046621,
                        "total": 26,
                        "title": "样片日记"
                      },
                      "archives": [
                        { "aid": 238588630, "bvid": "BV1be41127ft", "duration": 974, "pic": "https://i0.hdslb.com/p1.jpg", "pubdate": 1704877200, "title": "样片1", "stat": { "view": 1000, "danmaku": 30 } }
                      ],
                      "recent_aids": [238588630]
                    }
                  ],
                  "series_list": []
                }
            }
            """.trimIndent()

        val data = json.decodeFromString<WebSpaceSeasonListData>(raw)
        val result = SpaceSeasonListData.fromWebSpaceSeasonListData(data)

        assertThat(result.seasons).hasSize(1)
        val season = result.seasons.first()
        assertThat(season.seasonId).isEqualTo(2046621L)
        assertThat(season.title).isEqualTo("样片日记")
        assertThat(season.description).isEqualTo("简介")
        assertThat(season.total).isEqualTo(26)
        assertThat(season.videos).hasSize(1)
        assertThat(season.videos.first().aid).isEqualTo(238588630L)
        assertThat(season.videos.first().play).isEqualTo(1000)
        assertThat(season.videos.first().danmaku).isEqualTo(30)
        assertThat(result.page.hasNext).isTrue()
        assertThat(result.page.nextPageNumber).isEqualTo(2)
        assertThat(result.page.total).isEqualTo(32)
    }

    @Test
    fun `deserializes season archives list response`() {
        val raw =
            """
            {
                "meta": { "cover": "https://i0.hdslb.com/cover.jpg", "season_id": 2046621, "title": "样片日记", "total": 26 },
                "page": { "page_num": 1, "page_size": 30, "total": 26 },
                "aids": [238588630],
                "archives": [
                  { "aid": 238588630, "bvid": "BV1be41127ft", "duration": 974, "pic": "https://i0.hdslb.com/p1.jpg", "pubdate": 1704877200, "title": "样片1" }
                ]
            }
            """.trimIndent()

        val data = json.decodeFromString<WebSpaceSeasonArchivesData>(raw)
        val result = SpaceSeasonArchivesData.fromWebSpaceSeasonArchivesData(seasonId = 1L, data = data)

        assertThat(result.seasonId).isEqualTo(2046621L)
        assertThat(result.title).isEqualTo("样片日记")
        assertThat(result.total).isEqualTo(26)
        assertThat(result.videos).hasSize(1)
        assertThat(result.videos.first().title).isEqualTo("样片1")
        assertThat(result.videos.first().duration).isEqualTo(974)
        assertThat(result.page.hasNext).isFalse()
    }

    @Test
    fun `fromWebSpaceSeasonListData with null itemsLists returns empty list`() {
        val result = SpaceSeasonListData.fromWebSpaceSeasonListData(WebSpaceSeasonListData())

        assertThat(result.seasons).isEmpty()
        assertThat(result.page.hasNext).isFalse()
        assertThat(result.page.total).isEqualTo(0)
    }

    @Test
    fun `fromWebSpaceSeasonListData computes hasNext from page arithmetic`() {
        val fullPage =
            WebSpaceSeasonListData(
                itemsLists =
                    WebSpaceSeasonListData.ItemsLists(
                        page = WebSpaceSeasonListData.Page(pageNum = 1, pageSize = 20, total = 40),
                    ),
            )
        val lastPage =
            WebSpaceSeasonListData(
                itemsLists =
                    WebSpaceSeasonListData.ItemsLists(
                        page = WebSpaceSeasonListData.Page(pageNum = 2, pageSize = 20, total = 40),
                    ),
            )

        assertThat(SpaceSeasonListData.fromWebSpaceSeasonListData(fullPage).page.hasNext).isTrue()
        assertThat(SpaceSeasonListData.fromWebSpaceSeasonListData(lastPage).page.hasNext).isFalse()
    }

    @Test
    fun `fromWebSeasonItem falls back to first archive cover when meta cover missing`() {
        val item =
            WebSpaceSeasonListData.SeasonItem(
                meta = WebSpaceSeasonListData.Meta(seasonId = 7L, total = 3),
                archives =
                    listOf(
                        WebSpaceSeasonListData.Archive(aid = 1L, pic = "https://example.com/a.jpg"),
                    ),
            )

        val season = SpaceSeason.fromWebSeasonItem(item)

        assertThat(season.cover).isEqualTo("https://example.com/a.jpg")
        assertThat(season.seasonId).isEqualTo(7L)
    }

    @Test
    fun `fromWebSeasonItem with null meta uses archive count as total`() {
        val item =
            WebSpaceSeasonListData.SeasonItem(
                meta = null,
                archives =
                    listOf(
                        WebSpaceSeasonListData.Archive(aid = 1L),
                        WebSpaceSeasonListData.Archive(aid = 2L),
                    ),
            )

        val season = SpaceSeason.fromWebSeasonItem(item)

        assertThat(season.seasonId).isEqualTo(0L)
        assertThat(season.total).isEqualTo(2)
    }

    @Test
    fun `fromWebSpaceSeasonArchivesData with null meta falls back to argument seasonId`() {
        val data =
            WebSpaceSeasonArchivesData(
                meta = null,
                page = WebSpaceSeasonListData.Page(pageNum = 1, pageSize = 30, total = 2),
                archives =
                    listOf(
                        WebSpaceSeasonListData.Archive(aid = 1L),
                        WebSpaceSeasonListData.Archive(aid = 2L),
                    ),
            )

        val result = SpaceSeasonArchivesData.fromWebSpaceSeasonArchivesData(seasonId = 99L, data = data)

        assertThat(result.seasonId).isEqualTo(99L)
        assertThat(result.total).isEqualTo(2)
        assertThat(result.videos).hasSize(2)
    }
}
