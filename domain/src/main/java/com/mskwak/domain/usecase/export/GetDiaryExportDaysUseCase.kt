package com.mskwak.domain.usecase.export

import com.mskwak.domain.model.DiaryEntryExport
import com.mskwak.domain.model.DiaryExport
import com.mskwak.domain.model.TaskExport
import com.mskwak.domain.repository.DiaryRepository
import com.mskwak.domain.repository.PlantTaskRepository
import com.mskwak.domain.repository.WateringLogRepository
import java.time.LocalDate

class GetDiaryExportDaysUseCase(
    private val diaryRepository: DiaryRepository,
    private val wateringLogRepository: WateringLogRepository,
    private val plantTaskRepository: PlantTaskRepository
) {
    /**
     * 기간 내 일기, 물주기, 할일 완료 기록을 날짜별로 묶어 날짜 오름차순으로 반환한다.
     * 세 기록이 모두 없으면 빈 목록.
     */
    suspend operator fun invoke(
        plantId: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DiaryExport> {
        val diariesByDate = diaryRepository
            .getDiariesByPlantIdAndDateRange(plantId, startDate, endDate)
            .groupBy { it.createdDate }
        val wateringDates = wateringLogRepository
            .getWateringDatesByRange(plantId, startDate, endDate)
            .toSet()
        val taskLogsByDate = plantTaskRepository
            .getPlantTaskLogsByRange(plantId, startDate, endDate)
            .groupBy { it.date }

        val dates = diariesByDate.keys + wateringDates + taskLogsByDate.keys
        return dates.sorted().map { date ->
            DiaryExport(
                date = date,
                entries = diariesByDate[date].orEmpty().map { diary ->
                    DiaryEntryExport(
                        content = diary.memo,
                        imagePaths = diary.pictureList?.map { it.path }.orEmpty()
                    )
                },
                hasWateringRecord = date in wateringDates,
                tasks = taskLogsByDate[date].orEmpty().map { TaskExport(it.title, it.content) }
            )
        }
    }
}
