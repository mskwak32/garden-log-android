package com.mskwak.datastore

import kotlinx.coroutines.flow.Flow

/**
 * 사용자 설정 저장소. 값은 enum 이름 문자열로 저장하며, 저장값이 없으면 null을 방출한다.
 */
interface UserPreferencesLocalSource {
    val plantListSortOrder: Flow<String?>
    val diaryListSortOrder: Flow<String?>
    val diaryListPeriodUnit: Flow<String?>

    suspend fun setPlantListSortOrder(value: String)
    suspend fun setDiaryListSortOrder(value: String)
    suspend fun setDiaryListPeriodUnit(value: String)
}
