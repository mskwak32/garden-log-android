package com.mskwak.plant.model

import androidx.compose.runtime.Immutable
import com.mskwak.domain.model.Diary
import java.time.LocalDate

@Immutable
data class DiarySummaryUiModel(
    val id: Int,
    val date: LocalDate,
    val content: String,
    val imagePath: String?,
    val plantName: String? = null,
    val isHarvested: Boolean = false
)

fun Diary.toDiarySummaryUiModel(
    plantName: String? = null,
    isHarvested: Boolean = false
): DiarySummaryUiModel {
    return DiarySummaryUiModel(
        id = id,
        date = createdDate,
        content = memo,
        imagePath = pictureList?.firstOrNull()?.path,
        plantName = plantName,
        isHarvested = isHarvested
    )
}