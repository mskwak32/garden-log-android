package com.mskwak.gardendailylog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import com.mskwak.common_ui.theme.GardenLogTheme
import com.mskwak.gardendailylog.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 최근 앱 목록에서 재실행된 경우 이전 알림의 상세 화면을 다시 열지 않음
        val launchedFromHistory =
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val initialPlantId = intent.getIntExtra(EXTRA_PLANT_ID, -1)
            .takeIf { it != -1 && !launchedFromHistory }
        setContent {
            GardenLogTheme {
                MainScreen(
                    initialPlantId = initialPlantId,
                    openAppSetting = ::openAppSetting
                )
            }
        }
    }

    private fun openAppSetting() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also {
            startActivity(it)
        }
    }

    companion object {
        const val EXTRA_PLANT_ID = "extra_plant_id"

        // data URI는 알림마다 고유해야 PendingIntent가 서로 덮어써지지 않음
        fun createPlantDetailIntent(context: Context, plantId: Int, data: Uri): Intent {
            return Intent(context, MainActivity::class.java).apply {
                this.data = data
                putExtra(EXTRA_PLANT_ID, plantId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }
}