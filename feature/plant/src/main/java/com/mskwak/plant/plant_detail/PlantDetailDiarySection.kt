package com.mskwak.plant.plant_detail

import android.content.res.Configuration
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mskwak.common_ui.theme.GardenLogTheme
import com.mskwak.plant.R
import com.mskwak.plant.model.DiarySummaryUiModel
import java.time.LocalDate

@Composable
internal fun DiarySection(
    modifier: Modifier = Modifier,
    plantName: String,
    diaryList: List<DiarySummaryUiModel>,
    onEvent: (PlantDetailEvent) -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(15.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(15.dp),
                ).padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.plant_diary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Button(
                onClick = { onEvent(PlantDetailEvent.OnNewDiaryClicked) },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_diary),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (diaryList.isEmpty()) {
            DiaryEmpty(plantName)
        } else {
            diaryList.forEach { diary ->
                PlantDetailDiaryItem(
                    diary = diary,
                    onClick = { onEvent(PlantDetailEvent.OnDiaryClicked(diary.id)) },
                )
            }
            Button(
                onClick = { onEvent(PlantDetailEvent.OnMoreDiaryClicked) },
                shape = RoundedCornerShape(10.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                        ),
            ) {
                Text(
                    text = stringResource(R.string.view_more),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DiaryEmpty(plantName: String) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_notebook))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier =
                Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.diary_list_empty_with_plantName, plantName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDiarySection() {
    val now = LocalDate.now()
    val diaryList =
        listOf(
            DiarySummaryUiModel(
                id = 1,
                date = now,
                content = "오늘은 물을 주었습니다. 새 싹이 나기 시작했어요.",
                imagePath = null,
            ),
            DiarySummaryUiModel(
                id = 2,
                date = now.minusDays(4),
                content = "햇빛이 잘 드는 곳으로 옮겨주었습니다.",
                imagePath = null,
            ),
        )
    GardenLogTheme {
        DiarySection(
            diaryList = diaryList,
            plantName = "식물이름",
            onEvent = {},
        )
    }
}
