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
import com.mskwak.domain.usecase.plant.GetPlantNameUseCase
import com.mskwak.domain.usecase.watering.SetWateringAlarmUseCase
import com.mskwak.gardendailylog.MainActivity
import com.mskwak.gardendailylog.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WateringAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var getPlantNameUseCase: GetPlantNameUseCase

    @Inject
    lateinit var setWateringAlarmUseCase: SetWateringAlarmUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val plantId = intent.getIntExtra(PLANT_ID_KEY, DEFAULT_PLANT_ID)

        createNotificationChannel(context)
        CoroutineScope(Dispatchers.Default).takeIf { plantId != DEFAULT_PLANT_ID }?.launch {
            val plantName = getPlantNameUseCase.getName(plantId)

            showNotification(context, plantName, plantId)
            setWateringAlarmUseCase(plantId, true)
        }
    }

    private fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.noti_watering_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )

        manager.createNotificationChannel(channel)
    }

    private fun showNotification(context: Context, plantName: String?, plantId: Int) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(context.getString(R.string.noti_watering_title))
                .setContentText(
                    plantName?.let {
                        context.getString(R.string.noti_watering_message, it)
                    } ?: ""
                )
                .setContentIntent(buildContentIntent(context, plantId))
                .addAction(
                    0,
                    context.getString(R.string.noti_action_watering_done),
                    NotificationActionReceiver.createWateringPendingIntent(context, plantId)
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setGroup(WATERING_GROUP_KEY)
                .build()

            with(NotificationManagerCompat.from(context)) {
                notify(plantId, notification)
                // 물주기 알림 ID(plantId)와 겹치지 않도록 요약 알림은 tag 사용
                notify(SUMMARY_TAG, SUMMARY_NOTIFICATION_CODE, getSummaryNotification(context))
            }
        }
    }

    // Group 알림
    private fun getSummaryNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_leaf)
            .setAutoCancel(true)
            .setGroup(WATERING_GROUP_KEY)
            .setOnlyAlertOnce(true)
            .setGroupSummary(true)
            .build()
    }

    companion object {
        internal const val PLANT_ID_KEY = "plantId"
        private const val CHANNEL_ID = "wateringNotification"
        private const val DEFAULT_PLANT_ID = 100000
        private const val SUMMARY_NOTIFICATION_CODE = 1000
        private const val SUMMARY_TAG = "wateringSummary"
        private const val WATERING_GROUP_KEY = "wateringNotificationGroup"

        internal fun closeNotification(context: Context, plantId: Int) {
            NotificationManagerCompat.from(context).cancel(plantId)
            cancelSummaryIfEmpty(context, plantId)
        }

        // 기록 실패 시 같은 알림 ID로 갱신해 버튼 없이 탭하면 앱에서 다시 시도하게 함
        internal fun showFailureNotification(context: Context, plantId: Int, plantName: String?) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(context.getString(R.string.noti_watering_title))
                .setContentText(context.getString(R.string.noti_record_failed))
                // 실패 문구로 바뀌어도 어느 식물인지 알 수 있게 이름을 표시함
                .setSubText(plantName)
                .setContentIntent(buildContentIntent(context, plantId))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setGroup(WATERING_GROUP_KEY)
                .build()

            NotificationManagerCompat.from(context).notify(plantId, notification)
        }

        // 그룹에 남은 알림이 없으면 요약 알림만 떠 있지 않도록 취소함
        // cancel은 비동기라 방금 닫은 알림이 아직 조회될 수 있어 직접 제외함
        private fun cancelSummaryIfEmpty(context: Context, closedPlantId: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val hasChild = manager.activeNotifications.any {
                !(it.tag == null && it.id == closedPlantId) &&
                    it.notification.group == WATERING_GROUP_KEY &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            if (!hasChild) {
                NotificationManagerCompat.from(context).cancel(SUMMARY_TAG, SUMMARY_NOTIFICATION_CODE)
            }
        }

        private fun buildContentIntent(context: Context, plantId: Int): PendingIntent {
            return PendingIntent.getActivity(
                context,
                plantId,
                // 식물별로 고유한 data URI/requestCode를 써서 알림끼리 PendingIntent가 덮어써지지 않게 함
                MainActivity.createPlantDetailIntent(
                    context,
                    plantId,
                    "gardenlog://notification/watering/$plantId".toUri()
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
