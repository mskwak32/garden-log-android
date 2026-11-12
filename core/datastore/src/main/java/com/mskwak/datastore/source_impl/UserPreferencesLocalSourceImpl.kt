package com.mskwak.datastore.source_impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mskwak.datastore.UserPreferencesLocalSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

internal class UserPreferencesLocalSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesLocalSource {

    // 파일 읽기 실패 시 기본값으로 동작하도록 빈 설정으로 대체
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val plantListSortOrder: Flow<String?> = observe(Keys.PLANT_LIST_SORT_ORDER)
    override val diaryListSortOrder: Flow<String?> = observe(Keys.DIARY_LIST_SORT_ORDER)
    override val diaryListPeriodUnit: Flow<String?> = observe(Keys.DIARY_LIST_PERIOD_UNIT)

    override suspend fun setPlantListSortOrder(value: String) = set(Keys.PLANT_LIST_SORT_ORDER, value)
    override suspend fun setDiaryListSortOrder(value: String) = set(Keys.DIARY_LIST_SORT_ORDER, value)
    override suspend fun setDiaryListPeriodUnit(value: String) = set(Keys.DIARY_LIST_PERIOD_UNIT, value)

    private fun observe(key: Preferences.Key<String>): Flow<String?> =
        preferences.map { it[key] }.distinctUntilChanged()

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val PLANT_LIST_SORT_ORDER = stringPreferencesKey("plant_list_sort_order")
        val DIARY_LIST_SORT_ORDER = stringPreferencesKey("diary_list_sort_order")
        val DIARY_LIST_PERIOD_UNIT = stringPreferencesKey("diary_list_period_unit")
    }
}
