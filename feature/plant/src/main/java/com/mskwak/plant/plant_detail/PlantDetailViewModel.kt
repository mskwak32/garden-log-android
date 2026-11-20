package com.mskwak.plant.plant_detail

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mskwak.analytics.AnalyticsLogger
import com.mskwak.analytics.GardenEvent
import com.mskwak.analytics.WateringSource
import com.mskwak.common_ui.ViewEvent
import com.mskwak.common_ui.base.BaseViewModel
import com.mskwak.domain.usecase.diary.GetDiariesByPlantIdUseCase
import com.mskwak.domain.usecase.plant.DeletePlantUseCase
import com.mskwak.domain.usecase.plant.GetPlantUseCase
import com.mskwak.domain.usecase.plant.HarvestPlantUseCase
import com.mskwak.domain.usecase.watering.GetWateringDatesUseCase
import com.mskwak.domain.usecase.watering.GetWateringDaysUseCase
import com.mskwak.domain.usecase.watering.UpdateWateringAlarmActivationUseCase
import com.mskwak.domain.usecase.watering.UpdateWateringLogUseCase
import com.mskwak.domain.usecase.watering.WateringUpdateResult
import com.mskwak.plant.R
import com.mskwak.plant.model.toDiarySummaryUiModel
import com.mskwak.plant.model.toPlantListItemUiModel
import com.mskwak.plant.util.canScheduleExactAlarms
import com.mskwak.plant.util.wateringErrorResource
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlantDetailViewModel.Factory::class)
class PlantDetailViewModel @AssistedInject constructor(
    @Assisted navKey: PlantDetailNavKey,
    private val application: Application,
    private val getPlantUseCase: GetPlantUseCase,
    private val getWateringDaysUseCase: GetWateringDaysUseCase,
    private val getDiariesByPlantIdUseCase: GetDiariesByPlantIdUseCase,
    private val updateWateringAlarmActivationUseCase: UpdateWateringAlarmActivationUseCase,
    private val deletePlantUseCase: DeletePlantUseCase,
    private val harvestPlantUseCase: HarvestPlantUseCase,
    private val getWateringDatesUseCase: GetWateringDatesUseCase,
    private val updateWateringLogUseCase: UpdateWateringLogUseCase,
    private val analyticsLogger: AnalyticsLogger,
    taskHandlerFactory: PlantDetailTaskHandler.Factory
) : BaseViewModel<PlantDetailState, PlantDetailEvent, PlantDetailEffect>() {

    private val plantId: Int = navKey.plantId
    private var wateringObserveJob: Job? = null

    private val store = object : PlantDetailStore {
        override val state: PlantDetailState get() = viewState.value
        override val scope: CoroutineScope get() = viewModelScope
        override fun setState(reducer: PlantDetailState.() -> PlantDetailState) =
            this@PlantDetailViewModel.setState(reducer)
        override fun setEffect(effect: PlantDetailEffect) =
            this@PlantDetailViewModel.setEffect(effect)
        override fun closeCalendar() = this@PlantDetailViewModel.closeCalendar()
    }
    private val taskHandler = taskHandlerFactory.create(plantId, store)

    init {
        analyticsLogger.log(GardenEvent.ScreenView("plant_detail"))
        checkPlantExists()
        observePlant()
        observeWateringDates()
        taskHandler.start()
    }

    override fun setInitialState(): PlantDetailState = PlantDetailState()

    // 알림 이후 삭제된 식물로 진입한 경우 목록으로 되돌린다.
    // 첫 값만 확인해 상세 화면에서 직접 삭제할 때의 흐름과 겹치지 않게 한다.
    private fun checkPlantExists() {
        viewModelScope.launch {
            if (getPlantUseCase(plantId).first() == null) {
                setEffect(PlantDetailEffect.ShowSnackbar(R.string.message_plant_not_found))
                setEffect(PlantDetailEffect.Navigation.Back)
            }
        }
    }

    private fun observePlant(): Job {
        return combine(
            getPlantUseCase(plantId).filterNotNull(),
            getDiariesByPlantIdUseCase(plantId)
        ) { plant, diaries ->
            plant to diaries
        }.onEach { (plant, diaries) ->
            // 완료 상태와 최근 날짜는 Repository가 실제 이력으로 계산한 값을 사용한다.
            val uiModel = plant.toPlantListItemUiModel {
                getWateringDaysUseCase(it)
            }
            setState {
                copy(
                    today = LocalDate.now(),
                    plantImagePath = uiModel.imagePath,
                    plantName = uiModel.name,
                    createdAt = uiModel.createdAt,
                    dDays = uiModel.dDay,
                    wateringStatus = uiModel.status,
                    lastWateringDate = plant.latestWateringDate,
                    wateringAlarmTime = if (plant.waterPeriod == 0) null else plant.wateringAlarm.time,
                    isWateringActive = plant.wateringAlarm.isActive,
                    memo = plant.memo,
                    diaries = diaries.map { it.toDiarySummaryUiModel() },
                    harvestDate = plant.harvestDate,
                    harvestMemo = plant.harvestMemo
                )
            }
        }
            .launchIn(viewModelScope)
    }

    override fun handleEvents(viewEvent: ViewEvent) {
        val event = viewEvent as? PlantDetailEvent ?: return

        when (event) {
            is PlantDetailEvent.TaskEvent -> taskHandler.handle(event)

            // 달력
            PlantDetailEvent.OnWateringCalendarClicked -> {
                // 수확 식물은 수확일에서 조회를 시작하고 나머지는 오늘을 기본 선택한다.
                openCalendar(CalendarTarget.Watering, viewState.value.harvestDate ?: LocalDate.now())
            }
            is PlantDetailEvent.OnTaskCalendarClicked -> openTaskCalendar(event.taskId)
            PlantDetailEvent.OnCalendarClosed -> {
                if (!viewState.value.isCalendarSaving) closeCalendar()
            }
            is PlantDetailEvent.OnCalendarMonthChanged -> {
                if (!viewState.value.isCalendarSaving) {
                    setState { copy(calendarMonth = event.month) }
                }
            }
            is PlantDetailEvent.OnCalendarDateSelected -> {
                if (!viewState.value.isCalendarSaving) {
                    setState { copy(calendarSelectedDate = event.date, calendarError = null) }
                }
            }
            PlantDetailEvent.OnCalendarSaveClicked -> saveCalendarDate()
            PlantDetailEvent.OnCalendarRetry -> {
                when (val target = viewState.value.calendarTarget) {
                    is CalendarTarget.Task -> taskHandler.observeTaskLogDates(target.taskId)
                    else -> observeWateringDates()
                }
            }

            // 물주기
            is PlantDetailEvent.ToggleWateringAlarmActive -> toggleWateringAlarm(event.isActive)
            PlantDetailEvent.OnWateringClicked -> waterPlant()
            PlantDetailEvent.OnCancelWateringClicked -> cancelWatering()

            // 수확
            PlantDetailEvent.OnHarvestSectionToggled -> {
                setState { copy(isHarvestSectionExpanded = !isHarvestSectionExpanded) }
            }
            is PlantDetailEvent.OnHarvestMemoChanged -> setState { copy(harvestMemoInput = event.memo) }
            PlantDetailEvent.OnHarvestClicked -> setEffect(PlantDetailEffect.ShowHarvestConfirmDialog)
            is PlantDetailEvent.OnHarvestConfirmed -> harvestPlant(event.date)
            PlantDetailEvent.OnCancelHarvestClicked -> cancelHarvest()

            // 식물 관리·화면 이동
            PlantDetailEvent.OnDeletePlantClicked -> setEffect(PlantDetailEffect.ShowDeleteConfirmDialog)
            PlantDetailEvent.OnDeleteConfirmClicked -> deletePlant()
            PlantDetailEvent.OnBackClicked -> setEffect(PlantDetailEffect.Navigation.Back)
            PlantDetailEvent.OnEditPlantClicked -> setEffect(PlantDetailEffect.Navigation.ToEditPlant)
            PlantDetailEvent.OnExportClicked -> setEffect(PlantDetailEffect.Navigation.ToExportDiary)
            is PlantDetailEvent.OnDiaryClicked -> {
                setEffect(PlantDetailEffect.Navigation.ToDiaryDetail(event.diaryId))
            }
            PlantDetailEvent.OnNewDiaryClicked -> setEffect(PlantDetailEffect.Navigation.ToNewDiary)
            PlantDetailEvent.OnMoreDiaryClicked -> setEffect(PlantDetailEffect.Navigation.ToMoreDiaries)
        }
    }

    private fun observeWateringDates() {
        // 재시도 시 기존 구독을 교체해 같은 이력을 여러 번 수신하지 않는다.
        wateringObserveJob?.cancel()
        setState { copy(isWateringLoading = true, isWateringLoadFailed = false) }
        // 최신 물주기 날짜가 그대로인 과거 기록 변경도 달력에는 즉시 반영한다.
        wateringObserveJob = getWateringDatesUseCase(plantId)
            .onEach { dates ->
                setState { copy(wateringDates = dates.toSet(), isWateringLoading = false) }
            }
            .catch {
                setState { copy(isWateringLoading = false, isWateringLoadFailed = true) }
            }
            .launchIn(viewModelScope)
    }

    private fun openCalendar(target: CalendarTarget, date: LocalDate) {
        // 이전 할일 달력의 이력이 다른 대상에 남지 않도록 구독과 값을 정리한다.
        taskHandler.stopObservingTaskLogDates()
        setState {
            copy(
                calendarTarget = target,
                calendarSelectedDate = date,
                calendarMonth = YearMonth.from(date),
                calendarError = null,
                taskLogDates = emptySet(),
                isTaskLogLoading = false,
                isTaskLogLoadFailed = false
            )
        }
    }

    private fun openTaskCalendar(taskId: Int) {
        val state = viewState.value
        if (state.isHarvested) return
        val task = state.tasks.find { it.id == taskId } ?: return
        openCalendar(CalendarTarget.Task(task.id, task.title, task.type), LocalDate.now())
        taskHandler.observeTaskLogDates(taskId)
    }

    private fun closeCalendar() {
        taskHandler.stopObservingTaskLogDates()
        setState {
            copy(
                calendarTarget = null,
                taskLogDates = emptySet(),
                isTaskLogLoading = false,
                isTaskLogLoadFailed = false
            )
        }
    }

    private fun saveCalendarDate() {
        when (val target = viewState.value.calendarTarget) {
            CalendarTarget.Watering -> saveWateringDate()
            is CalendarTarget.Task -> taskHandler.saveCalendarTaskLog(target)
            null -> Unit
        }
    }

    private fun saveWateringDate(
        date: LocalDate = viewState.value.calendarSelectedDate,
        closeDialog: Boolean = true,
        isWatered: Boolean = date !in viewState.value.wateringDates
    ) {
        val state = viewState.value
        // 조회 실패를 빈 이력으로 해석하지 않으며 저장 중 중복 실행도 차단한다.
        if (state.isCalendarSaving || state.isWateringLoading || state.isHarvested ||
            state.isWateringLoadFailed
        ) return
        val add = isWatered
        // 날짜 범위는 추가에만 적용해 기존 범위 밖 기록의 취소는 허용한다.
        if (add && !state.isInRecordRange(date)) return
        setState { copy(isCalendarSaving = true, calendarError = null) }
        viewModelScope.launch {
            try {
                val result = updateWateringLogUseCase(plantId, date, add)
                // 알람 실패도 DB 저장은 완료된 결과이므로 다이얼로그를 닫는다.
                if (closeDialog) closeCalendar()
                if (result == WateringUpdateResult.ALARM_FAILED) {
                    setEffect(PlantDetailEffect.ShowSnackbar(R.string.message_watering_alarm_failed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // DB 저장 실패는 선택 날짜를 유지해 같은 작업을 다시 시도하게 한다.
                if (closeDialog) {
                    setState { copy(calendarError = e.wateringErrorResource()) }
                } else {
                    setEffect(PlantDetailEffect.ShowSnackbar(e.wateringErrorResource()))
                }
            } finally {
                setState { copy(isCalendarSaving = false) }
            }
        }
    }

    private fun toggleWateringAlarm(isActive: Boolean) {
        if (isActive && !canScheduleExactAlarms(application)) {
            setEffect(PlantDetailEffect.ShowExactAlarmPermissionDialog)
            return
        }

        viewModelScope.launch {
            updateWateringAlarmActivationUseCase(plantId, isActive)
            analyticsLogger.log(GardenEvent.WateringAlarmToggle(isActive))
        }
    }

    private fun waterPlant() {
        analyticsLogger.log(GardenEvent.WateringClick(WateringSource.DETAIL))
        if (LocalDate.now() !in viewState.value.wateringDates) {
            saveWateringDate(LocalDate.now(), false, true)
        }
    }

    private fun cancelWatering() {
        if (LocalDate.now() in viewState.value.wateringDates) {
            saveWateringDate(LocalDate.now(), false, false)
            analyticsLogger.log(GardenEvent.CancelWatering)
        }
    }

    private fun deletePlant() {
        viewModelScope.launch {
            if (deletePlantUseCase(plantId)) setEffect(PlantDetailEffect.Navigation.Back)
        }
    }

    private fun harvestPlant(harvestDate: LocalDate) {
        viewModelScope.launch {
            harvestPlantUseCase.harvest(
                plantId = plantId,
                harvestMemo = viewState.value.harvestMemoInput.ifBlank { null },
                harvestDate = harvestDate
            )
            analyticsLogger.log(GardenEvent.Harvest)
            setState { copy(isHarvestSectionExpanded = false, harvestMemoInput = "") }
        }
    }

    private fun cancelHarvest() {
        viewModelScope.launch {
            harvestPlantUseCase.cancelHarvest(plantId)
            analyticsLogger.log(GardenEvent.CancelHarvest)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: PlantDetailNavKey): PlantDetailViewModel
    }
}
