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
                .setContentIntent(getPendingIntent(context, plantId))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setGroup(WATERING_GROUP_KEY)
                .build()

            with(NotificationManagerCompat.from(context)) {
                notify(plantId, notification)
                notify(SUMMARY_NOTIFICATION_CODE, getSummaryNotification(context))
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

    private fun getPendingIntent(context: Context, plantId: Int): PendingIntent {
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

    companion object {
        internal const val PLANT_ID_KEY = "plantId"
        private const val CHANNEL_ID = "wateringNotification"
        private const val DEFAULT_PLANT_ID = 100000
        private const val SUMMARY_NOTIFICATION_CODE = 1000
        private const val WATERING_GROUP_KEY = "wateringNotificationGroup"
    }
}