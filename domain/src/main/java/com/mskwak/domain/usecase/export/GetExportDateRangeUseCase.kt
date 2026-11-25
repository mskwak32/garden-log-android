package com.mskwak.domain.usecase.export

import com.mskwak.domain.repository.DiaryRepository
import com.mskwak.domain.repository.PlantTaskRepository
import com.mskwak.domain.repository.WateringLogRepository
import java.time.LocalDate

class GetExportDateRangeUseCase(
    private val diaryRepository: DiaryRepository,
    private val wateringLogRepository: WateringLogRepository,
    private val plantTaskRepository: PlantTaskRepository
) {
    /**
     * 일기·물주기·할일 완료 기록 전체의 처음~마지막 날짜. 기록이 하나도 없으면 null.
     */
    suspend operator fun invoke(plantId: Int): Pair<LocalDate, LocalDate>? {
        val ranges = listOfNotNull(
            diaryRepository.getDiaryDateRange(plantId),
            wateringLogRepository.getWateringDateRange(plantId),
            plantTaskRepository.getTaskLogDateRange(plantId)
        )
        if (ranges.isEmpty()) return null
        return ranges.minOf { it.first } to ranges.maxOf { it.second }
    }
}
