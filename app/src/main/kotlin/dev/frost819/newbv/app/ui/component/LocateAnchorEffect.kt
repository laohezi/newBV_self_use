package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** 聚焦前置延迟：等待 `scrollToItem` 后目标项完成组合。 */
private const val LOCATE_FOCUS_DELAY_MS = 50L

/**
 * 网格列表定位到指定视频的效果。
 *
 * 当 [aids] 中出现 [anchorAid] 时，滚动到对应网格项并请求焦点，
 * 确保从播放器/详情页跳转过来时列表直接停在当前视频上。
 *
 * 仅生效一次（按 [anchorAid] 记忆），后续分页追加数据不会重复跳动。
 * [aids] 中暂时没有目标时不做处理，由调用方的 ViewModel 继续翻页，
 * 数据到位后本效果会再次触发。
 *
 * @param gridState 网格滚动状态
 * @param aids 当前已加载列表的 aid，顺序与网格项一致
 * @param anchorAid 需要定位的 aid，0 表示不定位
 * @param headerItemCount 列表项之前的头部项数量（头部/标题等）
 * @param focusSaver 焦点恢复器，用于取到目标项的 `FocusRequester`
 * @param focusKeyPrefix 目标项在 [focusSaver] 中的 key 前缀，实际 key 为 `前缀 + 索引`
 * @param enabled 是否启用（如仅在「投稿」Tab 生效）
 * @param onLocated 定位完成回调（通常用于通知 ViewModel 不再自动翻页）
 */
@Composable
fun LocateAnchorEffect(
    gridState: LazyGridState,
    aids: List<Long>,
    anchorAid: Long,
    headerItemCount: Int,
    focusSaver: FocusSaver,
    focusKeyPrefix: String,
    enabled: Boolean = true,
    onLocated: () -> Unit = {},
) {
    var located by remember(anchorAid) { mutableStateOf(false) }

    LaunchedEffect(anchorAid, aids, enabled) {
        if (located || !enabled || anchorAid == 0L) return@LaunchedEffect
        val index = aids.indexOf(anchorAid)
        if (index < 0) return@LaunchedEffect

        gridState.scrollToItem(headerItemCount + index)
        delay(LOCATE_FOCUS_DELAY_MS)
        runCatching { focusSaver.focusRequesterFor("$focusKeyPrefix$index").requestFocus() }
        located = true
        onLocated()
    }
}
