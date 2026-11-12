package com.mskwak.domain.repository

import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.model.DiaryListViewOption
import com.mskwak.domain.model.PlantListSortOrder
import kotlinx.coroutines.flow.Flow

interface UserPreferenceRepository {
    val plantListSortOrder: Flow<PlantListSortOrder>
    val diaryListViewOption: Flow<DiaryListViewOption>

    suspend fun setPlantListSortOrder(order: PlantListSortOrder)
    suspend fun setDiaryListSortOrder(order: DiaryListSortOrder)
    suspend fun setDiaryListPeriodUnit(unit: DiaryListPeriodUnit)
}
