package dev.frost819.newbv.biliapi.http.entity.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户空间合集/系列列表（Web）。
 *
 * 对应接口：`GET /x/polymer/web-space/seasons_series_list`
 *
 * @param itemsLists 合集与系列列表容器
 */
@Serializable
data class WebSpaceSeasonListData(
    @SerialName("items_lists")
    val itemsLists: ItemsLists? = null,
) {
    /**
     * @param page 合集总数分页信息
     * @param seasonsList 合集列表
     * @param seriesList 系列列表
     */
    @Serializable
    data class ItemsLists(
        val page: Page? = null,
        @SerialName("seasons_list")
        val seasonsList: List<SeasonItem> = emptyList(),
        @SerialName("series_list")
        val seriesList: List<SeasonItem> = emptyList(),
    )

    /**
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @param total 合集/系列总数
     */
    @Serializable
    data class Page(
        @SerialName("page_num")
        val pageNum: Int = 0,
        @SerialName("page_size")
        val pageSize: Int = 0,
        val total: Int = 0,
    )

    /**
     * @param meta 合集元信息
     * @param archives 合集内视频（仅前若干个预览）
     */
    @Serializable
    data class SeasonItem(
        val meta: Meta? = null,
        val archives: List<Archive> = emptyList(),
    )

    /**
     * @param category 合集分类
     * @param cover 合集封面
     * @param description 合集简介
     * @param mid UP 主 mid
     * @param name 合集名称（如“合集·样片日记”）
     * @param ptime 合集创建时间
     * @param seasonId 合集 id
     * @param total 合集内视频总数
     * @param title 合集标题
     */
    @Serializable
    data class Meta(
        val category: Int = 0,
        val cover: String = "",
        val description: String = "",
        val mid: Long = 0,
        val name: String = "",
        val ptime: Long = 0,
        @SerialName("season_id")
        val seasonId: Long = 0,
        val total: Int = 0,
        val title: String = "",
    )

    /**
     * @param aid 稿件 avid
     * @param bvid 稿件 bvid
     * @param duration 时长（秒）
     * @param pic 封面
     * @param pubdate 发布时间戳
     * @param stat 播放/弹幕统计
     * @param title 标题
     */
    @Serializable
    data class Archive(
        val aid: Long = 0,
        val bvid: String = "",
        val ctime: Long = 0,
        val duration: Int = 0,
        val pic: String = "",
        val pubdate: Long = 0,
        val stat: Stat? = null,
        val title: String = "",
    )

    /**
     * @param view 播放数
     * @param danmaku 弹幕数
     */
    @Serializable
    data class Stat(
        val view: Int = 0,
        val danmaku: Int = 0,
        val vt: Int = 0,
    )
}

/**
 * 合集内视频列表（Web）。
 *
 * 对应接口：`GET /x/polymer/web-space/seasons_archives_list`
 *
 * @param meta 合集元信息
 * @param page 分页信息
 * @param archives 视频列表
 * @param aids 视频 avid 列表
 */
@Serializable
data class WebSpaceSeasonArchivesData(
    val meta: WebSpaceSeasonListData.Meta? = null,
    val page: WebSpaceSeasonListData.Page? = null,
    val archives: List<WebSpaceSeasonListData.Archive> = emptyList(),
    val aids: List<Long> = emptyList(),
)
