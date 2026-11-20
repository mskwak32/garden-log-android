package com.mskwak.plant.plant_detail

import android.app.Application
import com.mskwak.analytics.AnalyticsLogger
import com.mskwak.analytics.GardenEvent
import com.mskwak.domain.model.Alarm
import com.mskwak.domain.model.PlantTask
import com.mskwak.domain.model.PlantTaskType
import com.mskwak.domain.usecase.task.AddPlantTaskUseCase
import com.mskwak.domain.usecase.task.DeletePlantTaskUseCase
import com.mskwak.domain.usecase.task.GetNextDueDateUseCase
import com.mskwak.domain.usecase.task.GetPlantTaskLogsUseCase
import com.mskwak.domain.usecase.task.GetPlantTasksUseCase
import com.mskwak.domain.usecase.task.GetTaskLogDatesUseCase
import com.mskwak.domain.usecase.task.GetTaskRemainDaysUseCase
import com.mskwak.domain.usecase.task.UpdatePlantTaskAlarmActivationUseCase
import com.mskwak.domain.usecase.task.UpdatePlantTaskLogUseCase
import com.mskwak.domain.usecase.task.UpdatePlantTaskUseCase
import com.mskwak.domain.usecase.watering.WateringUpdateResult
import com.mskwak.plant.R
import com.mskwak.plant.model.toPlantTaskUiModel
import com.mskwak.plant.util.canScheduleExactAlarms
import com.mskwak.plant.util.taskErrorResource
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/** 할일 처리기가 ViewModel 상태를 읽고 바꾸는 통로 */
interface PlantDetailStore {
    val state: PlantDetailState
    val scope: CoroutineScope
    fun setState(reducer: PlantDetailState.() -> PlantDetailState)
    fun setEffect(effect: PlantDetailEffect)
    fun closeCalendar()
}

/** 식물 상세의 할일 다이얼로그·완료 기록·알림 처리 */
class PlantDetailTaskHandler @AssistedInject constructor(
    @Assisted private val plantId: Int,
    @Assisted private val store: PlantDetailStore,
    private val application: Application,
    private val getPlantTasksUseCase: GetPlantTasksUseCase,
    private val getNextDueDateUseCase: GetNextDueDateUseCase,
    private val getTaskRemainDaysUseCase: GetTaskRemainDaysUseCase,
    private val addPlantTaskUseCase: AddPlantTaskUseCase,
    private val updatePlantTaskUseCase: UpdatePlantTaskUseCase,
    private val deletePlantTaskUseCase: DeletePlantTaskUseCase,
    private val updatePlantTaskLogUseCase: UpdatePlantTaskLogUseCase,
    private val updatePlantTaskAlarmActivationUseCase: UpdatePlantTaskAlarmActivationUseCase,
    private val getTaskLogDatesUseCase: GetTaskLogDatesUseCase,
    private val getPlantTaskLogsUseCase: GetPlantTaskLogsUseCase,
    private val analyticsLogger: AnalyticsLogger
) {
    private var taskLogObserveJob: Job? = null
    // 수정 시 UI 모델에 없는 생성일·알람 활성 상태를 유지하기 위해 원본을 보관한다.
    private var domainTasks: Map<Int, PlantTask> = emptyMap()

    private val state: PlantDetailState get() = store.state

    fun start() {
        observeTasks()
        observePlantTaskLogs()
    }

    fun handle(event: PlantDetailEvent.TaskEvent) {
        when (event) {
            PlantDetailEvent.OnAddTaskClicked -> {
                if (!state.isHarvested) {
                    store.setState { copy(taskDialog = PlantTaskDialogState(date = LocalDate.now())) }
                }
            }
            is PlantDetailEvent.OnTaskMoreClicked -> openTaskEditDialog(event.taskId)
            PlantDetailEvent.OnTaskDialogDismissed -> {
                if (!state.isTaskSaving) store.setState { copy(taskDialog = null) }
            }
            is PlantDetailEvent.OnTaskTitleChanged -> updateTaskDialog {
                copy(title = event.title.take(PlantTaskDialogState.TITLE_MAX_LENGTH))
            }
            is PlantDetailEvent.OnTaskContentChanged -> updateTaskDialog { copy(content = event.content) }
            is PlantDetailEvent.OnTaskTypeChanged -> updateTaskDialog {
                // 종류는 생성 시에만 바꿀 수 있다.
                if (isEdit) this else copy(type = event.type)
            }
            is PlantDetailEvent.OnTaskDateChanged -> updateTaskDialog { copy(date = event.date) }
            is PlantDetailEvent.OnTaskPeriodChanged -> updateTaskDialog { copy(periodDays = event.periodDays) }
            is PlantDetailEvent.OnTaskAlarmTimeChanged -> updateTaskDialog { copy(alarmTime = event.time) }
            PlantDetailEvent.OnTaskSaveClicked -> saveTask()
            PlantDetailEvent.OnTaskDeleteClicked -> {
                state.taskDialog?.taskId?.let {
                    store.setEffect(PlantDetailEffect.ShowTaskDeleteConfirmDialog(it))
                }
            }
            is PlantDetailEvent.OnTaskDeleteConfirmed -> deleteTask(event.taskId)
            is PlantDetailEvent.OnTaskCompleteClicked -> completeTask(event.taskId)
            is PlantDetailEvent.OnOneTimeTaskCompleteConfirmed -> {
                // 할일 달력에서 확인한 완료는 저장 후 달력도 닫는다.
                val fromCalendar = (state.calendarTarget as? CalendarTarget.Task)
                    ?.taskId == event.taskId
                saveTaskLog(event.taskId, event.date, isDone = true, fromCalendar = fromCalendar)
            }
            is PlantDetailEvent.OnTaskCancelClicked -> cancelTask(event.taskId)
            is PlantDetailEvent.ToggleTaskAlarm -> toggleTaskAlarm(event.taskId, event.isActive)
        }
    }

    fun observeTaskLogDates(taskId: Int) {
        taskLogObserveJob?.cancel()
        store.setState { copy(isTaskLogLoading = true, isTaskLogLoadFailed = false) }
        taskLogObserveJob = getTaskLogDatesUseCase(taskId)
            .onEach { dates ->
                store.setState { copy(taskLogDates = dates.toSet(), isTaskLogLoading = false) }
            }
            .catch {
                store.setState { copy(isTaskLogLoading = false, isTaskLogLoadFailed = true) }
            }
            .launchIn(store.scope)
    }

    fun stopObservingTaskLogDates() {
        taskLogObserveJob?.cancel()
        taskLogObserveJob = null
    }

    fun saveCalendarTaskLog(target: CalendarTarget.Task) {
        val state = store.state
        // 조회 전·실패 상태의 빈 이력을 미완료로 해석하지 않는다.
        if (state.isCalendarSaving || state.isTaskLogLoading || state.isTaskLogLoadFailed) return
        val date = state.calendarSelectedDate
        val isDone = date !in state.taskLogDates
        if (isDone && !state.isInRecordRange(date)) return
        // 1회성은 완료하면 할일이 사라지므로 확인 후 저장한다.
        if (isDone && target.type == PlantTaskType.ONE_TIME) {
            store.setEffect(PlantDetailEffect.ShowOneTimeTaskCompleteDialog(target.taskId, date))
        } else {
            saveTaskLog(target.taskId, date, isDone, fromCalendar = true)
        }
    }

    private fun observeTasks() {
        getPlantTasksUseCase(plantId)
            .onEach { tasks ->
                domainTasks = tasks.associateBy { it.id }
                val today = LocalDate.now()
                store.setState {
                    copy(
                        tasks = tasks.map {
                            it.toPlantTaskUiModel(
                                dueDate = getNextDueDateUseCase(it),
                                remainDays = getTaskRemainDaysUseCase(it, today)
                            )
                        }
                    )
                }
            }
            .catch { Timber.e(it, "Failed to observe plant tasks") }
            .launchIn(store.scope)
    }

    private fun observePlantTaskLogs() {
        getPlantTaskLogsUseCase(plantId)
            .onEach { logs ->
                store.setState { copy(plantTaskLogs = logs.groupBy({ it.date }, { it.title })) }
            }
            .catch { Timber.e(it, "Failed to observe plant task logs") }
            .launchIn(store.scope)
    }

    private fun saveTaskLog(taskId: Int, date: LocalDate, isDone: Boolean, fromCalendar: Boolean) {
        val state = store.state
        if (state.isCalendarSaving || state.isHarvested) return
        if (isDone && !state.isInRecordRange(date)) return
        val type = domainTasks[taskId]?.type ?: return
        store.setState { copy(isCalendarSaving = true, calendarError = null) }
        store.scope.launch {
            try {
                val result = updatePlantTaskLogUseCase(taskId, date, isDone)
                if (result != WateringUpdateResult.UNCHANGED) {
                    analyticsLogger.log(
                        if (isDone) GardenEvent.CompleteTask(type.name.lowercase())
                        else GardenEvent.CancelTask
                    )
                }
                if (fromCalendar) store.closeCalendar()
                if (result == WateringUpdateResult.ALARM_FAILED) {
                    store.setEffect(PlantDetailEffect.ShowSnackbar(R.string.message_task_alarm_failed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (fromCalendar) {
                    store.setState { copy(calendarError = e.taskErrorResource()) }
                } else {
                    store.setEffect(PlantDetailEffect.ShowSnackbar(e.taskErrorResource()))
                }
            } finally {
                store.setState { copy(isCalendarSaving = false) }
            }
        }
    }

    private fun completeTask(taskId: Int) {
        val state = store.state
        if (state.isHarvested) return
        val task = state.tasks.find { it.id == taskId } ?: return
        val today = LocalDate.now()
        when (task.type) {
            PlantTaskType.ONE_TIME ->
                store.setEffect(PlantDetailEffect.ShowOneTimeTaskCompleteDialog(taskId, today))
            PlantTaskType.REPEAT ->
                if (!task.isDoneToday) saveTaskLog(taskId, today, isDone = true, fromCalendar = false)
        }
    }

    private fun cancelTask(taskId: Int) {
        val task = state.tasks.find { it.id == taskId } ?: return
        if (task.type == PlantTaskType.REPEAT && task.isDoneToday) {
            saveTaskLog(taskId, LocalDate.now(), isDone = false, fromCalendar = false)
        }
    }

    private fun openTaskEditDialog(taskId: Int) {
        val state = store.state
        if (state.isHarvested) return
        val task = state.tasks.find { it.id == taskId } ?: return
        store.setState {
            copy(
                taskDialog = PlantTaskDialogState(
                    taskId = task.id,
                    title = task.title,
                    content = task.content.orEmpty(),
                    type = task.type,
                    // 반복성은 기준일 대신 현재 다음 예정일을 보여준다.
                    date = task.dueDate,
                    initialDate = task.dueDate,
                    periodDays = task.periodDays ?: PlantTaskDialogState.DEFAULT_TASK_PERIOD_DAYS,
                    alarmTime = task.alarmTime
                )
            )
        }
    }

    private fun updateTaskDialog(update: PlantTaskDialogState.() -> PlantTaskDialogState) {
        if (state.isTaskSaving) return
        store.setState { copy(taskDialog = taskDialog?.update()) }
    }

    private fun saveTask() {
        val state = store.state
        val dialog = state.taskDialog ?: return
        if (state.isTaskSaving || state.isHarvested || !dialog.canSave) return
        val periodDays = dialog.periodDays.takeIf { dialog.type == PlantTaskType.REPEAT }
        val title = dialog.title.trim()
        val content = dialog.content.trim().ifBlank { null }
        store.setState { copy(isTaskSaving = true) }
        store.scope.launch {
            try {
                val result = if (dialog.taskId == null) {
                    addPlantTaskUseCase(
                        PlantTask(
                            plantId = plantId,
                            title = title,
                            content = content,
                            type = dialog.type,
                            date = dialog.date,
                            periodDays = periodDays,
                            // 생성 시 알림은 꺼진 상태로 시작한다.
                            alarm = Alarm(dialog.alarmTime, false),
                            createdAt = LocalDateTime.now()
                        )
                    ).also {
                        analyticsLogger.log(GardenEvent.AddTask(dialog.type.name.lowercase(), false))
                    }
                } else {
                    val origin = domainTasks[dialog.taskId]
                    if (origin == null) {
                        store.setState { copy(taskDialog = null) }
                        return@launch
                    }
                    updatePlantTaskUseCase(
                        origin.copy(
                            title = title,
                            content = content,
                            date = dialog.date,
                            periodDays = periodDays,
                            alarm = origin.alarm.copy(time = dialog.alarmTime)
                        ),
                        isDateChanged = dialog.isDateChanged
                    )
                }
                store.setState { copy(taskDialog = null) }
                if (result == WateringUpdateResult.ALARM_FAILED) {
                    store.setEffect(PlantDetailEffect.ShowSnackbar(R.string.message_task_alarm_failed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.setEffect(PlantDetailEffect.ShowSnackbar(e.taskErrorResource()))
            } finally {
                store.setState { copy(isTaskSaving = false) }
            }
        }
    }

    private fun deleteTask(taskId: Int) {
        if (state.isTaskSaving) return
        store.setState { copy(isTaskSaving = true) }
        store.scope.launch {
            try {
                deletePlantTaskUseCase(taskId)
                analyticsLogger.log(GardenEvent.DeleteTask)
                store.setState { copy(taskDialog = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.setEffect(PlantDetailEffect.ShowSnackbar(e.taskErrorResource()))
            } finally {
                store.setState { copy(isTaskSaving = false) }
            }
        }
    }

    private fun toggleTaskAlarm(taskId: Int, isActive: Boolean) {
        val state = store.state
        if (state.isHarvested || state.isTaskSaving) return
        if (isActive && !canScheduleExactAlarms(application)) {
            store.setEffect(PlantDetailEffect.ShowExactAlarmPermissionDialog)
            return
        }
        store.setState { copy(isTaskSaving = true) }
        store.scope.launch {
            try {
                updatePlantTaskAlarmActivationUseCase(taskId, isActive)
                analyticsLogger.log(GardenEvent.TaskAlarmToggle(isActive))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.setEffect(PlantDetailEffect.ShowSnackbar(e.taskErrorResource()))
            } finally {
                store.setState { copy(isTaskSaving = false) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(plantId: Int, store: PlantDetailStore): PlantDetailTaskHandler
    }
}
