package com.mskwak.domain.usecase.diary

import com.mskwak.domain.model.Diary
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.repository.DiaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
        val (startDate, endDate) = diaryListPeriodRange(periodUnit, yearMonth)
        return diaryRepository.getDiaries(startDate, endDate, plantId).map { list ->
            list.applySort(sortOrder)
        }.flowOn(Dispatchers.IO)
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
