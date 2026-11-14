package com.mskwak.plant.diary_list

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mskwak.common_ui.IconPack
import com.mskwak.common_ui.icon.DiaryBook
import com.mskwak.common_ui.icon.Gardening
import com.mskwak.common_ui.icon.WaterDropBlue
import com.mskwak.common_ui.theme.GardenLogTheme
import com.mskwak.common_ui.ui_component.PagerDotIndicator
import com.mskwak.common_ui.ui_component.TextBadge
import com.mskwak.plant.R
import com.mskwak.plant.model.DiaryListItemUiModel
import java.time.LocalDate

@Composable
fun DiaryListItem(
    item: DiaryListItemUiModel,
    onEvent: (DiaryListEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when (item) {
        is DiaryListItemUiModel.Diary -> DiaryCard(item, onEvent, modifier)
        is DiaryListItemUiModel.Task -> TaskCard(item, onEvent, modifier)
        is DiaryListItemUiModel.Watering -> WateringCard(item, modifier)
    }
}

// 카드 공통 컨테이너. 클릭 가능한 카드만 onClick을 넘기며 리플은 쓰지 않는다.
@Composable
private fun Modifier.cardContainer(onClick: (() -> Unit)?): Modifier {
    val shape = RoundedCornerShape(12.dp)
    val base = this
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    return if (onClick == null) {
        base
    } else {
        base.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }
}

@Composable
private fun DiaryCard(
    item: DiaryListItemUiModel.Diary,
    onEvent: (DiaryListEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasImage = item.imagePaths.isNotEmpty()
    Column(
        modifier = modifier
            .cardContainer { onEvent(DiaryListEvent.OnDiaryClicked(item.id)) }
            .padding(
                if (hasImage) {
                    PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)
                } else {
                    PaddingValues(12.dp)
                }
            )
    ) {
        if (hasImage) {
            DiaryImages(imagePaths = item.imagePaths)
            Spacer(Modifier.height(9.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = IconPack.DiaryBook,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            PlantNameRow(item.plantName, item.isHarvested)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiaryImages(imagePaths: List<String>) {
    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(10.dp))

    if (imagePaths.size == 1) {
        DiaryImage(path = imagePaths.first(), modifier = imageModifier)
    } else {
        val pagerState = rememberPagerState(pageCount = { imagePaths.size })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(state = pagerState) { page ->
                DiaryImage(path = imagePaths[page], modifier = imageModifier)
            }
            Spacer(Modifier.height(7.dp))
            PagerDotIndicator(pagerState = pagerState)
        }
    }
}

@Composable
private fun DiaryImage(path: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = path,
        contentDescription = null,
        placeholder = painterResource(R.drawable.ic_flower_pot),
        error = painterResource(R.drawable.ic_flower_pot),
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun TaskCard(
    item: DiaryListItemUiModel.Task,
    onEvent: (DiaryListEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val content = item.content?.takeIf { it.isNotBlank() }
    Column(
        modifier = modifier
            .cardContainer(
                // 내용이 있을 때만 클릭 가능
                onClick = content?.let { { onEvent(DiaryListEvent.OnTaskClicked(item.title, it)) } }
            )
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = if (content != null) 12.dp else 10.dp
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = IconPack.Gardening,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            PlantNameRow(item.plantName, item.isHarvested)
        }
        if (content != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WateringCard(
    item: DiaryListItemUiModel.Watering,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .cardContainer(onClick = null)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = IconPack.WaterDropBlue,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.watering),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        PlantNameRow(item.plantName, item.isHarvested)
    }
}

@Composable
private fun PlantNameRow(plantName: String?, isHarvested: Boolean) {
    if (plantName == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_leaf),
            contentDescription = null,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = plantName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isHarvested) {
            Spacer(Modifier.width(6.dp))
            TextBadge(text = stringResource(R.string.harvested))
        }
    }
}

@Preview(showBackground = true, name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiaryListItemPreview() {
    GardenLogTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiaryListItem(
                item = DiaryListItemUiModel.Diary(
                    id = 1,
                    imagePaths = emptyList(),
                    content = "오늘 몬스테라에게 물을 주었다. 새 잎이 나오려고 하는 것 같아서 기분이 좋다. 햇빛을 더 많이 받게 해줘야겠다.",
                    plantName = "몬스테라",
                    isHarvested = false
                ),
                onEvent = {}
            )
            DiaryListItem(
                item = DiaryListItemUiModel.Task(
                    logId = 1,
                    title = "분갈이",
                    content = "새 화분으로 옮겨 심었다. 뿌리가 많이 자라 있었다.",
                    plantName = "스투키",
                    isHarvested = false
                ),
                onEvent = {}
            )
            DiaryListItem(
                item = DiaryListItemUiModel.Task(
                    logId = 2,
                    title = "영양제 주기",
                    content = null,
                    plantName = "방울토마토",
                    isHarvested = true
                ),
                onEvent = {}
            )
            DiaryListItem(
                item = DiaryListItemUiModel.Watering(
                    plantId = 1,
                    date = LocalDate.of(2023, 10, 5),
                    plantName = "몬스테라",
                    isHarvested = false
                ),
                onEvent = {}
            )
        }
    }
}
