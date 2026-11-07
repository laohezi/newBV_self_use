package dev.frost819.newbv.app.ui.component.videocard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.biliapi.entity.user.SpaceSeason
import dev.frost819.newbv.core.focus.touchClickable

/**
 * UGC 合集卡片。
 *
 * 16:9 封面 + 标题 + 视频数量，焦点选中时显示主题边框。
 *
 * @param season 合集数据。
 * @param onClick 点击回调。
 * @param modifier Modifier。
 */
@Composable
fun CollectionCard(
    season: SpaceSeason,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.touchClickable(onClick = onClick),
        onClick = onClick,
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                pressedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.large),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(width = 3.dp, color = MaterialTheme.colorScheme.border),
                        shape = MaterialTheme.shapes.large,
                    ),
            ),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large),
                contentAlignment = Alignment.BottomEnd,
            ) {
                AsyncImage(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    model = season.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = "${season.total} 个视频",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = season.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (season.description.isNotEmpty()) {
                    Text(
                        text = season.description,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
