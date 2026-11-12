package com.mskwak.plant.diary_list

import androidx.compose.runtime.Immutable
import com.mskwak.common_ui.ViewEffect
import com.mskwak.common_ui.ViewEvent
import com.mskwak.common_ui.ViewState
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.model.DiaryListViewOption
import com.mskwak.plant.model.DiaryListItemUiModel
import java.time.YearMonth

/**
 * @param viewOption DataStore에서 첫 값을 받기 전에는 null
 * @param currentYearMonth 조회 기준 연월. 연도별 보기에서는 연도만 사용하고, 월별로 돌아오면 이 월을 보여준다.
 */
@Immutable
data class DiaryListState(
    val viewOption: DiaryListViewOption? = null,
    val currentYearMonth: YearMonth = YearMonth.now(),
    val currentPlant: DiaryListPlantFilterUiModel? = null,
    val plantFilterList: List<DiaryListPlantFilterUiModel> = emptyList(),
    val diaries: List<DiaryListItemUiModel> = emptyList()
) : ViewState {
    val periodUnit: DiaryListPeriodUnit
        get() = viewOption?.periodUnit ?: DiaryListPeriodUnit.MONTH

    val sortOrder: DiaryListSortOrder
        get() = viewOption?.sortOrder ?: DiaryListSortOrder.CREATED_LATEST
}

sealed interface DiaryListEvent : ViewEvent {
    data class PlantFilterChanged(val plantFilter: DiaryListPlantFilterUiModel?) : DiaryListEvent
    data class SortOderChanged(val sortOder: DiaryListSortOrder) : DiaryListEvent
    data class PeriodUnitChanged(val periodUnit: DiaryListPeriodUnit) : DiaryListEvent
    data class YearMonthChanged(val yearMonth: YearMonth) : DiaryListEvent
    data object OnCurrentPeriodClicked : DiaryListEvent
    data object OnNextPeriodClicked : DiaryListEvent
    data object OnPreviousPeriodClicked : DiaryListEvent
    data object SelectPeriod : DiaryListEvent
    data class OnDiaryClicked(val diaryId: Int) : DiaryListEvent
}

sealed interface DiaryListEffect : ViewEffect {
    sealed interface Navigation : DiaryListEffect {
        data class GoToDiaryDetail(val diaryId: Int) : Navigation
    }

    data class ShowSelectYearMonth(
        val yearMonth: YearMonth,
        val showMonth: Boolean
    ) : DiaryListEffect
}
