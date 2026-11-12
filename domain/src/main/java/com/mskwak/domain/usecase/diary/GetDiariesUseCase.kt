package com.mskwak.domain.usecase.diary

import com.mskwak.domain.model.Diary
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.repository.DiaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

class GetDiariesUseCase(
    private val diaryRepository: DiaryRepository
) {
    /**
     * @param yearMonth 조회 기준 연월. [DiaryListPeriodUnit.YEAR]이면 연도만 사용한다.
     */
    operator fun invoke(
        periodUnit: DiaryListPeriodUnit,
        yearMonth: YearMonth,
        sortOrder: DiaryListSortOrder,
        plantId: Int? = null
    ): Flow<List<Diary>> {
        val (startDate, endDate) = periodRange(periodUnit, yearMonth)
        return diaryRepository.getDiaries(startDate, endDate, plantId).map { list ->
            list.applySort(sortOrder)
        }.flowOn(Dispatchers.IO)
    }

    private fun periodRange(
        periodUnit: DiaryListPeriodUnit,
        yearMonth: YearMonth
    ): Pair<LocalDate, LocalDate> {
        return when (periodUnit) {
            DiaryListPeriodUnit.MONTH -> yearMonth.atDay(1) to yearMonth.atEndOfMonth()
            DiaryListPeriodUnit.YEAR -> {
                LocalDate.of(yearMonth.year, 1, 1) to LocalDate.of(yearMonth.year, 12, 31)
            }
        }
    }

    private fun List<Diary>.applySort(sortOrder: DiaryListSortOrder): List<Diary> {
        return when (sortOrder) {
            DiaryListSortOrder.CREATED_LATEST -> {
                sortedByDescending { it.createdDate }
            }

            DiaryListSortOrder.CREATED_EARLIEST -> {
                sortedBy { it.createdDate }
            }
        }
    }
}
