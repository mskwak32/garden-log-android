package com.mskwak.gardendailylog

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.mskwak.domain.usecase.picture.DeleteUnusedPicturesUseCase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class AppApplication : Application() {

    @Inject
    lateinit var deleteUnusedPicturesUseCase: DeleteUnusedPicturesUseCase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initTimber()
        initFirebase()
        cleanUpUnusedPictures()
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(TimberDebugTree())
        } else {
            Timber.plant(TimberReleaseTree())
        }
    }

    private fun initFirebase() {
        FirebaseApp.initializeApp(applicationContext)
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        Firebase.analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
    }

    // 삭제·교체·저장 실패 등으로 남은 사진 파일을 앱 시작 시 한 번 정리한다.
    private fun cleanUpUnusedPictures() {
        appScope.launch {
            try {
                deleteUnusedPicturesUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete unused pictures")
            }
        }
    }
}
