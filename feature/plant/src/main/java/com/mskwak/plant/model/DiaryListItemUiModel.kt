package com.mskwak.plant.model

import androidx.compose.runtime.Immutable
import com.mskwak.domain.model.DiaryListDay
import java.time.LocalDate

@Immutable
sealed interface DiaryListItemUiModel {
    val key: String

    data class Diary(
        val id: Int,
        val imagePaths: List<String>,
        val content: String,
        val plantName: String?,
        val isHarvested: Boolean
    ) : DiaryListItemUiModel {
        override val key: String get() = "diary_$id"
    }

    data class Task(
        val logId: Int,
        val title: String,
        val content: String?,
        val plantName: String?,
        val isHarvested: Boolean
    ) : DiaryListItemUiModel {
        override val key: String get() = "task_$logId"
    }

    data class Watering(
        val plantId: Int,
        val date: LocalDate,
        val plantName: String?,
        val isHarvested: Boolean
    ) : DiaryListItemUiModel {
        override val key: String get() = "water_${plantId}_$date"
    }
}

@Immutable
data class DiaryListDayUiModel(
    val date: LocalDate,
    val items: List<DiaryListItemUiModel>
)

/**
 * @param plantInfo plantId -> (식물 이름, 수확 여부). 없으면 이름 없이 표시한다.
 */
fun DiaryListDay.toUiModel(
    plantInfo: (plantId: Int) -> Pair<String, Boolean>?
): DiaryListDayUiModel {
    // 같은 날 안 순서는 일기 -> 할일 -> 물주기
    val items = diaries.map {
        val plant = plantInfo(it.plantId)
        DiaryListItemUiModel.Diary(
            id = it.id,
            imagePaths = it.pictureList.orEmpty().map { picture -> picture.path },
            content = it.memo,
            plantName = plant?.first,
            isHarvested = plant?.second ?: false
        )
    } + taskLogs.map {
        val plant = plantInfo(it.plantId)
        DiaryListItemUiModel.Task(
            logId = it.id,
            title = it.title,
            content = it.content,
            plantName = plant?.first,
            isHarvested = plant?.second ?: false
        )
    } + wateringLogs.map {
        val plant = plantInfo(it.plantId)
        DiaryListItemUiModel.Watering(
            plantId = it.plantId,
            date = it.date,
            plantName = plant?.first,
            isHarvested = plant?.second ?: false
        )
    }
    return DiaryListDayUiModel(date = date, items = items)
}
