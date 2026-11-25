package com.mskwak.gardendailylog.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.mskwak.domain.repository.PlantTaskRepository
import com.mskwak.domain.usecase.plant.GetPlantNameUseCase
import com.mskwak.domain.usecase.task.SetPlantTaskAlarmUseCase
import com.mskwak.gardendailylog.MainActivity
import com.mskwak.gardendailylog.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlantTaskAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var plantTaskRepository: PlantTaskRepository

    @Inject
    lateinit var getPlantNameUseCase: GetPlantNameUseCase

    @Inject
    lateinit var setPlantTaskAlarmUseCase: SetPlantTaskAlarmUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TASK_ID_KEY, DEFAULT_TASK_ID)

        createNotificationChannel(context)
        CoroutineScope(Dispatchers.Default).takeIf { taskId != DEFAULT_TASK_ID }?.launch {
            // 알람 이후 삭제된 할일은 알림 없이 종료
            val task = plantTaskRepository.getTask(taskId) ?: return@launch
            val plantName = getPlantNameUseCase.getName(task.plantId)

            showNotification(context, plantName, task.title, taskId, task.plantId)
            setPlantTaskAlarmUseCase(taskId, true)
        }
    }

    private fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.noti_task_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )

        manager.createNotificationChannel(channel)
    }

    private fun showNotification(
        context: Context,
        plantName: String?,
        taskTitle: String,
        taskId: Int,
        plantId: Int
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(context.getString(R.string.noti_task_title, plantName.orEmpty()))
                .setContentText(taskTitle)
                .setContentIntent(buildContentIntent(context, plantId, taskId))
                .addAction(
                    0,
                    context.getString(R.string.noti_action_task_done),
                    NotificationActionReceiver.createTaskPendingIntent(context, taskId)
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setGroup(TASK_GROUP_KEY)
                .build()

            with(NotificationManagerCompat.from(context)) {
                // 물주기 알림 ID(plantId)와 겹치지 않도록 tag 사용
                notify(NOTIFICATION_TAG, taskId, notification)
                // 물주기 알림 ID(plantId)와 겹치지 않도록 요약 알림에도 tag 사용
                notify(SUMMARY_TAG, SUMMARY_NOTIFICATION_CODE, getSummaryNotification(context))
            }
        }
    }

    // Group 알림
    private fun getSummaryNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_leaf)
            .setAutoCancel(true)
            .setGroup(TASK_GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setGroupSummary(true)
            .build()
    }

    companion object {
        internal const val TASK_ID_KEY = "taskId"
        private const val CHANNEL_ID = "plantTaskNotification"
        private const val DEFAULT_TASK_ID = -1
        private const val NOTIFICATION_TAG = "plantTask"
        private const val SUMMARY_TAG = "plantTaskSummary"
        private const val SUMMARY_NOTIFICATION_CODE = 2000
        private const val TASK_GROUP_KEY = "plantTaskNotificationGroup"

        internal fun closeNotification(context: Context, taskId: Int) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, taskId)
            cancelSummaryIfEmpty(context, taskId)
        }

        // 기록 실패 시 같은 tag/ID로 갱신해 버튼 없이 탭하면 앱에서 다시 시도하게 함
        internal fun showFailureNotification(
            context: Context,
            plantId: Int,
            taskId: Int,
            plantName: String?
        ) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(context.getString(R.string.noti_task_title, plantName.orEmpty()))
                .setContentText(context.getString(R.string.noti_record_failed))
                .setContentIntent(buildContentIntent(context, plantId, taskId))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setGroup(TASK_GROUP_KEY)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, taskId, notification)
        }

        // 그룹에 남은 알림이 없으면 요약 알림만 떠 있지 않도록 취소함
        // cancel은 비동기라 방금 닫은 알림이 아직 조회될 수 있어 직접 제외함
        private fun cancelSummaryIfEmpty(context: Context, closedTaskId: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val hasChild = manager.activeNotifications.any {
                !(it.tag == NOTIFICATION_TAG && it.id == closedTaskId) &&
                    it.notification.group == TASK_GROUP_KEY &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            if (!hasChild) {
                NotificationManagerCompat.from(context).cancel(SUMMARY_TAG, SUMMARY_NOTIFICATION_CODE)
            }
        }

        private fun buildContentIntent(context: Context, plantId: Int, taskId: Int): PendingIntent {
            return PendingIntent.getActivity(
                context,
                taskId,
                // 할일별로 고유한 data URI/requestCode를 써서 알림끼리 PendingIntent가 덮어써지지 않게 함
                MainActivity.createPlantDetailIntent(
                    context,
                    plantId,
                    "gardenlog://notification/task/$taskId".toUri()
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
