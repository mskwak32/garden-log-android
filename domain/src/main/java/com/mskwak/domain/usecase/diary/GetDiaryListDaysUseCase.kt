package com.mskwak.domain.usecase.diary

import com.mskwak.domain.model.DiaryListDay
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.repository.DiaryRepository
import com.mskwak.domain.repository.PlantTaskRepository
import com.mskwak.domain.repository.WateringLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.YearMonth

class GetDiaryListDaysUseCase(
    private val diaryRepository: DiaryRepository,
    private val plantTaskRepository: PlantTaskRepository,
    private val wateringLogRepository: WateringLogRepository
) {
    /**
     * 일기, 할일 완료 기록, 물주기 기록을 날짜별로 묶어 반환한다.
     * 같은 날 안의 표시 순서(일기, 할일, 물주기)는 UI에서 정한다.
     *
     * @param yearMonth 조회 기준 연월. [DiaryListPeriodUnit.YEAR]이면 연도만 사용한다.
     */
    operator fun invoke(
        periodUnit: DiaryListPeriodUnit,
        yearMonth: YearMonth,
        sortOrder: DiaryListSortOrder,
        plantId: Int? = null
    ): Flow<List<DiaryListDay>> {
        val (startDate, endDate) = diaryListPeriodRange(periodUnit, yearMonth)
        return combine(
            diaryRepository.getDiaries(startDate, endDate, plantId),
            plantTaskRepository.observeTaskLogsByRange(startDate, endDate, plantId),
            wateringLogRepository.observeWateringLogsByRange(startDate, endDate, plantId)
        ) { diaries, taskLogs, wateringLogs ->
            // 날짜별 그룹핑(그룹 내 순서는 조회 순서 유지). 기록이 없는 날짜는 키에 없으므로 만들어지지 않음
            val diariesByDate = diaries.groupBy { it.createdDate }
            val taskLogsByDate = taskLogs.groupBy { it.date }
            val wateringLogsByDate = wateringLogs.groupBy { it.date }
            val dates = diariesByDate.keys + taskLogsByDate.keys + wateringLogsByDate.keys
            val sortedDates = when (sortOrder) {
                DiaryListSortOrder.CREATED_LATEST -> dates.sortedDescending()
                DiaryListSortOrder.CREATED_EARLIEST -> dates.sorted()
            }
            sortedDates.map { date ->
                DiaryListDay(
                    date = date,
                    diaries = diariesByDate[date].orEmpty(),
                    taskLogs = taskLogsByDate[date].orEmpty(),
                    wateringLogs = wateringLogsByDate[date].orEmpty()
                )
            }
        }.flowOn(Dispatchers.IO)
    }
}
