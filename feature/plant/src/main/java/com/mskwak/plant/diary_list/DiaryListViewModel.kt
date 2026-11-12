package com.mskwak.plant.diary_list

import androidx.lifecycle.viewModelScope
import com.mskwak.analytics.AnalyticsLogger
import com.mskwak.analytics.GardenEvent
import com.mskwak.common_ui.ViewEvent
import com.mskwak.common_ui.base.BaseViewModel
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListViewOption
import com.mskwak.domain.model.PlantListSortOrder
import com.mskwak.domain.usecase.diary.GetDiariesUseCase
import com.mskwak.domain.usecase.plant.GetPlantsWithSortOrderUseCase
import com.mskwak.domain.usecase.preference.GetDiaryListViewOptionUseCase
import com.mskwak.domain.usecase.preference.SetDiaryListPeriodUnitUseCase
import com.mskwak.domain.usecase.preference.SetDiaryListSortOrderUseCase
import com.mskwak.plant.model.toDiaryListItemUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class DiaryListViewModel @Inject constructor(
    private val getDiariesUseCase: GetDiariesUseCase,
    private val getPlantsWithSortOrderUseCase: GetPlantsWithSortOrderUseCase,
    private val getDiaryListViewOptionUseCase: GetDiaryListViewOptionUseCase,
    private val setDiaryListPeriodUnitUseCase: SetDiaryListPeriodUnitUseCase,
    private val setDiaryListSortOrderUseCase: SetDiaryListSortOrderUseCase,
    private val analyticsLogger: AnalyticsLogger
) : BaseViewModel<DiaryListState, DiaryListEvent, DiaryListEffect>() {
    override fun setInitialState(): DiaryListState = DiaryListState()

    init {
        analyticsLogger.log(GardenEvent.ScreenView("diary_list"))
        observeViewOption()
        observePlants()
        observeDiaries()
    }

    // 단위·정렬은 DataStore가 유일한 출처. 변경 이벤트는 저장만 하고 상태는 여기서 갱신된다.
    private fun observeViewOption() {
        getDiaryListViewOptionUseCase()
            .onEach { option -> setState { copy(viewOption = option) } }
            .launchIn(viewModelScope)
    }

    private fun observePlants() {
        getPlantsWithSortOrderUseCase(PlantListSortOrder.CREATED_LATEST)
            .onEach { plants ->
                setState {
                    copy(plantFilterList = plants.map {
                        DiaryListPlantFilterUiModel(
                            plantId = it.id,
                            plantName = it.name,
                            isHarvested = it.isHarvested
                        )
                    })
                }
            }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDiaries() {
        // viewOption이 null이면 조회하지 않는다. 기본값(월별)으로 그렸다가 바뀌는 깜빡임 방지
        val diariesFlow = viewState
            .mapNotNull { state ->
                state.viewOption?.let { option ->
                    DiaryQuery(option, state.currentYearMonth, state.currentPlant?.plantId)
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                getDiariesUseCase(
                    periodUnit = query.viewOption.periodUnit,
                    yearMonth = query.yearMonth,
                    sortOrder = query.viewOption.sortOrder,
                    plantId = query.plantId
                )
            }

        // plantFilterList가 나중에 로드되더라도 식물 이름이 반영되도록 combine 사용
        combine(
            diariesFlow,
            viewState.map { it.plantFilterList }.distinctUntilChanged()
        ) { diaries, plantFilterList ->
            val plantMap = plantFilterList.associateBy { it.plantId }
            diaries.map { diary ->
                val plant = plantMap[diary.plantId]
                diary.toDiaryListItemUiModel(
                    plantName = plant?.plantName,
                    isHarvested = plant?.isHarvested ?: false
                )
            }
        }
            .onEach { mappedDiaries ->
                setState { copy(diaries = mappedDiaries) }
            }
            .launchIn(viewModelScope)
    }

    override fun handleEvents(viewEvent: ViewEvent) {
        val event = viewEvent as? DiaryListEvent ?: return

        when (event) {
            is DiaryListEvent.SelectPeriod -> {
                val state = viewState.value
                setEffect(
                    DiaryListEffect.ShowSelectYearMonth(
                        yearMonth = state.currentYearMonth,
                        showMonth = state.periodUnit == DiaryListPeriodUnit.MONTH
                    )
                )
            }

            is DiaryListEvent.YearMonthChanged -> {
                yearMonthChange(event.yearMonth)
            }

            is DiaryListEvent.OnPreviousPeriodClicked -> {
                movePeriod(-1)
            }

            is DiaryListEvent.OnNextPeriodClicked -> {
                movePeriod(1)
            }

            is DiaryListEvent.OnCurrentPeriodClicked -> {
                yearMonthChange(YearMonth.now())
            }

            is DiaryListEvent.PlantFilterChanged -> {
                setState { copy(currentPlant = event.plantFilter) }
            }

            is DiaryListEvent.SortOderChanged -> {
                viewModelScope.launch { setDiaryListSortOrderUseCase(event.sortOder) }
            }

            is DiaryListEvent.PeriodUnitChanged -> {
                viewModelScope.launch { setDiaryListPeriodUnitUseCase(event.periodUnit) }
            }

            is DiaryListEvent.OnDiaryClicked -> {
                setEffect(DiaryListEffect.Navigation.GoToDiaryDetail(event.diaryId))
            }
        }
    }

    private fun movePeriod(amount: Long) {
        val state = viewState.value
        val yearMonth = when (state.periodUnit) {
            DiaryListPeriodUnit.MONTH -> state.currentYearMonth.plusMonths(amount)
            DiaryListPeriodUnit.YEAR -> state.currentYearMonth.plusYears(amount)
        }
        yearMonthChange(yearMonth)
    }

    private fun yearMonthChange(yearMonth: YearMonth) {
        setState {
            copy(currentYearMonth = yearMonth)
        }
    }

    private data class DiaryQuery(
        val viewOption: DiaryListViewOption,
        val yearMonth: YearMonth,
        val plantId: Int?
    )
}
