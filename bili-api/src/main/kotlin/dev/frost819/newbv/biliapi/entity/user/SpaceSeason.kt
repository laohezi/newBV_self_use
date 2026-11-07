package dev.frost819.newbv.biliapi.entity.user

import dev.frost819.newbv.biliapi.http.entity.user.WebSpaceSeasonArchivesData
import dev.frost819.newbv.biliapi.http.entity.user.WebSpaceSeasonListData
import dev.frost819.newbv.biliapi.http.util.toSmartDate

/**
 * 用户空间合集（UGC 合集）。
 *
 * @param seasonId 合集 id
 * @param title 合集标题
 * @param cover 合集封面
 * @param description 合集简介
 * @param total 合集内视频总数
 * @param videos 合集内视频（列表接口仅返回前若干个，用于预览封面）
 */
data class SpaceSeason(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val description: String,
    val total: Int,
    val videos: List<SpaceVideo> = emptyList(),
) {
    companion object {
        /** 将 Web 合集列表项转换为领域模型。 */
        fun fromWebSeasonItem(item: WebSpaceSeasonListData.SeasonItem): SpaceSeason {
            val meta = item.meta
            return SpaceSeason(
                seasonId = meta?.seasonId ?: 0L,
                title = meta?.title ?: meta?.name ?: "",
                cover =
                    meta
                        ?.cover
                        ?.takeIf { it.isNotEmpty() }
                        ?: item.archives.firstOrNull()?.pic
                        ?: "",
                description = meta?.description ?: "",
                total = meta?.total ?: item.archives.size,
                videos = item.archives.map { toSpaceVideo(it) },
            )
        }
    }
}

/**
 * 合集列表分页信息。
 *
 * @param hasNext 是否还有下一页
 * @param nextPageNumber 下次请求页码
 * @param pageSize 每页数量
 * @param total 合集总数
 */
data class SpaceSeasonPage(
    val hasNext: Boolean = true,
    val nextPageNumber: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
)

/**
 * 合集列表数据。
 *
 * @param seasons 合集列表
 * @param page 分页信息
 */
data class SpaceSeasonListData(
    val seasons: List<SpaceSeason>,
    val page: SpaceSeasonPage,
) {
    companion object {
        /** 将 Web 合集列表响应转换为领域模型。 */
        fun fromWebSpaceSeasonListData(data: WebSpaceSeasonListData) =
            SpaceSeasonListData(
                seasons = data.itemsLists?.seasonsList?.map { SpaceSeason.fromWebSeasonItem(it) } ?: emptyList(),
                page = toSpaceSeasonPage(data.itemsLists?.page),
            )
    }
}

/**
 * 合集内视频列表数据。
 *
 * @param seasonId 合集 id
 * @param title 合集标题
 * @param cover 合集封面
 * @param total 合集内视频总数
 * @param videos 视频列表
 * @param page 分页信息
 */
data class SpaceSeasonArchivesData(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val total: Int,
    val videos: List<SpaceVideo>,
    val page: SpaceSeasonPage,
) {
    companion object {
        /** 将 Web 合集视频列表响应转换为领域模型。 */
        fun fromWebSpaceSeasonArchivesData(
            seasonId: Long,
            data: WebSpaceSeasonArchivesData,
        ) = SpaceSeasonArchivesData(
            seasonId = data.meta?.seasonId ?: seasonId,
            title = data.meta?.title ?: "",
            cover = data.meta?.cover ?: "",
            total = data.meta?.total ?: data.archives.size,
            videos = data.archives.map { toSpaceVideo(it) },
            page = toSpaceSeasonPage(data.page),
        )
    }
}

/** 将 Web 合集内视频条目转换为通用空间视频模型。 */
private fun toSpaceVideo(archive: WebSpaceSeasonListData.Archive): SpaceVideo =
    SpaceVideo(
        aid = archive.aid,
        bvid = archive.bvid,
        title = archive.title,
        cover = archive.pic,
        author = "",
        duration = archive.duration,
        play = archive.stat?.view ?: 0,
        danmaku = archive.stat?.danmaku ?: 0,
        pubTime = archive.pubdate.toSmartDate(),
    )

private fun toSpaceSeasonPage(page: WebSpaceSeasonListData.Page?): SpaceSeasonPage {
    if (page == null) return SpaceSeasonPage(hasNext = false, total = 0)
    return SpaceSeasonPage(
        hasNext = page.pageNum * page.pageSize < page.total,
        nextPageNumber = page.pageNum + 1,
        pageSize = if (page.pageSize > 0) page.pageSize else 20,
        total = page.total,
    )
}
