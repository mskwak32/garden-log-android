package com.mskwak.data.repository

import com.mskwak.datastore.UserPreferencesLocalSource
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.model.DiaryListViewOption
import com.mskwak.domain.model.PlantListSortOrder
import com.mskwak.domain.repository.UserPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class UserPreferenceRepositoryImpl @Inject constructor(
    private val localSource: UserPreferencesLocalSource
) : UserPreferenceRepository {

    override val plantListSortOrder: Flow<PlantListSortOrder> = localSource.plantListSortOrder
        .map { it.toEnumOrDefault(PlantListSortOrder.CREATED_LATEST) }
        .distinctUntilChanged()

    override val diaryListViewOption: Flow<DiaryListViewOption> = combine(
        localSource.diaryListPeriodUnit.map { it.toEnumOrDefault(DiaryListPeriodUnit.MONTH) },
        localSource.diaryListSortOrder.map { it.toEnumOrDefault(DiaryListSortOrder.CREATED_LATEST) },
        ::DiaryListViewOption
    ).distinctUntilChanged()

    override suspend fun setPlantListSortOrder(order: PlantListSortOrder) {
        localSource.setPlantListSortOrder(order.name)
    }

    override suspend fun setDiaryListSortOrder(order: DiaryListSortOrder) {
        localSource.setDiaryListSortOrder(order.name)
    }

    override suspend fun setDiaryListPeriodUnit(unit: DiaryListPeriodUnit) {
        localSource.setDiaryListPeriodUnit(unit.name)
    }
}

// 저장값이 없거나 enum 이름이 바뀌어 매칭되지 않으면 기본값
internal inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
