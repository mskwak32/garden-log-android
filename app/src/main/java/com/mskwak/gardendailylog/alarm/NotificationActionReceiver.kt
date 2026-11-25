package com.mskwak.gardendailylog.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.mskwak.analytics.AnalyticsLogger
import com.mskwak.analytics.GardenEvent
import com.mskwak.analytics.WateringSource
import com.mskwak.domain.model.WateringValidationException
import com.mskwak.domain.repository.PlantRepository
import com.mskwak.domain.repository.PlantTaskRepository
import com.mskwak.domain.usecase.plant.GetPlantNameUseCase
import com.mskwak.domain.usecase.task.UpdatePlantTaskLogUseCase
import com.mskwak.domain.usecase.watering.WateringNowUseCase
import com.mskwak.domain.usecase.watering.WateringUpdateResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

// 알림의 "완료" 액션 버튼을 처리해 물주기/할일을 기록하고 알림을 닫음
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var plantRepository: PlantRepository

    @Inject
    lateinit var plantTaskRepository: PlantTaskRepository

    @Inject
    lateinit var getPlantNameUseCase: GetPlantNameUseCase

    @Inject
    lateinit var wateringNowUseCase: WateringNowUseCase

    @Inject
    lateinit var updatePlantTaskLogUseCase: UpdatePlantTaskLogUseCase

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE_WATERING -> {
                        val plantId = intent.getIntExtra(WateringAlarmReceiver.PLANT_ID_KEY, INVALID_ID)
                        if (plantId != INVALID_ID) completeWatering(appContext, plantId)
                    }

                    ACTION_COMPLETE_TASK -> {
                        val taskId = intent.getIntExtra(PlantTaskAlarmReceiver.TASK_ID_KEY, INVALID_ID)
                        if (taskId != INVALID_ID) completeTask(appContext, taskId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun completeWatering(context: Context, plantId: Int) {
        try {
            val plant = plantRepository.getPlant(plantId)
            // 삭제되었거나 수확 완료된 식물은 기록하지 않고 알림만 닫음
            if (plant == null || plant.isHarvested) {
                closeWateringNotification(context, plantId)
                return
            }

            val result = wateringNowUseCase(plantId)
            handleResult(result) {
                analyticsLogger.log(GardenEvent.WateringClick(WateringSource.NOTIFICATION))
            }
            closeWateringNotification(context, plantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WateringValidationException) {
            // 확인 뒤 수확되는 등 다시 시도해도 기록할 수 없는 경우라 알림만 닫음
            Timber.w(e, "알림에서 물주기 기록 불가 plantId=%d", plantId)
            closeWateringNotification(context, plantId)
        } catch (e: Exception) {
            Timber.e(e, "알림에서 물주기 기록 실패 plantId=%d", plantId)
            // 실패 알림용 조회도 실패할 수 있어 예외가 코루틴 밖으로 나가 크래시하지 않게 막음
            val plantName = runCatching { getPlantNameUseCase.getName(plantId) }.getOrNull()
            WateringAlarmReceiver.showFailureNotification(context, plantId, plantName)
        }
    }

    private suspend fun completeTask(context: Context, taskId: Int) {
        try {
            val task = plantTaskRepository.getTask(taskId)
            val plant = task?.let { plantRepository.getPlant(it.plantId) }
            // 삭제된 할일이거나 식물이 없거나 수확 완료된 경우 기록하지 않고 알림만 닫음
            if (task == null || plant == null || plant.isHarvested) {
                closeTaskNotification(context, taskId)
                return
            }

            val result = updatePlantTaskLogUseCase(taskId, LocalDate.now(), true)
            handleResult(result) {
                analyticsLogger.log(GardenEvent.CompleteTask(task.type.name.lowercase()))
            }
            closeTaskNotification(context, taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WateringValidationException) {
            // 확인 뒤 수확되는 등 다시 시도해도 기록할 수 없는 경우라 알림만 닫음
            Timber.w(e, "알림에서 할일 완료 기록 불가 taskId=%d", taskId)
            closeTaskNotification(context, taskId)
        } catch (e: Exception) {
            Timber.e(e, "알림에서 할일 완료 기록 실패 taskId=%d", taskId)
            // 실패 알림용 조회도 실패할 수 있어 예외가 코루틴 밖으로 나가 크래시하지 않게 막음
            val plantId = runCatching { plantTaskRepository.getTask(taskId)?.plantId }.getOrNull()
                ?: return
            val plantName = runCatching { getPlantNameUseCase.getName(plantId) }.getOrNull()
            PlantTaskAlarmReceiver.showFailureNotification(context, plantId, taskId, plantName)
        }
    }

    // UNCHANGED는 이미 같은 상태이므로 analytics 없이 닫기만 하고, ALARM_FAILED는 기록이 저장됐으므로 성공으로 취급함
    private inline fun handleResult(result: WateringUpdateResult, logEvent: () -> Unit) {
        when (result) {
            WateringUpdateResult.CHANGED -> logEvent()
            WateringUpdateResult.UNCHANGED -> Unit
            WateringUpdateResult.ALARM_FAILED -> {
                logEvent()
                Timber.e("알림에서 기록은 저장했으나 알람 재예약 실패")
            }
        }
    }

    // 마지막 알림을 닫으면 요약 알림도 함께 정리됨
    private fun closeWateringNotification(context: Context, plantId: Int) =
        WateringAlarmReceiver.closeNotification(context, plantId)

    private fun closeTaskNotification(context: Context, taskId: Int) =
        PlantTaskAlarmReceiver.closeNotification(context, taskId)

    companion object {
        internal const val ACTION_COMPLETE_WATERING = "com.mskwak.gardendailylog.action.COMPLETE_WATERING"
        internal const val ACTION_COMPLETE_TASK = "com.mskwak.gardendailylog.action.COMPLETE_TASK"
        private const val INVALID_ID = -1

        internal fun createWateringPendingIntent(context: Context, plantId: Int): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_COMPLETE_WATERING
                // 식물별로 고유한 data URI/requestCode를 써서 액션끼리 PendingIntent가 덮어써지지 않게 함
                data = "gardenlog://action/watering/$plantId".toUri()
                putExtra(WateringAlarmReceiver.PLANT_ID_KEY, plantId)
            }
            return PendingIntent.getBroadcast(
                context,
                plantId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        internal fun createTaskPendingIntent(context: Context, taskId: Int): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_COMPLETE_TASK
                data = "gardenlog://action/task/$taskId".toUri()
                putExtra(PlantTaskAlarmReceiver.TASK_ID_KEY, taskId)
            }
            return PendingIntent.getBroadcast(
                context,
                taskId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
