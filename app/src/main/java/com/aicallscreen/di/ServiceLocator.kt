package com.aicallscreen.di

import android.content.Context
import androidx.room.Room
import com.aicallscreen.BuildConfig
import com.aicallscreen.audio.CallAudioCapture
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.data.local.AppDatabase
import com.aicallscreen.data.local.CallLogDao
import com.aicallscreen.data.repository.CallLogRepository
import com.aicallscreen.llm.LLMProvider
import com.aicallscreen.stt.WhisperCppEngine
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Lightweight service locator for components that must be shared across the screening pipeline.
 * Replace with Hilt/Koin when the module graph grows.
 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    val screeningScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    val audioFocusManager: CallAudioFocusManager by lazy {
        CallAudioFocusManager(appContext)
    }

    val callAudioCapture: CallAudioCapture by lazy {
        CallAudioCapture(appContext)
    }

    val ttsEngine: OfflineCallTtsEngine by lazy {
        OfflineCallTtsEngine(appContext)
    }

    val speechToTextEngine: WhisperCppEngine by lazy {
        WhisperCppEngine()
    }

    val llmProvider: LLMProvider by lazy {
        LLMProvider(
            context = appContext,
            useCloud = BuildConfig.USE_CLOUD_LLM,
            cloudBaseUrl = BuildConfig.CLOUD_LLM_BASE_URL,
        )
    }

    val callLogDao: CallLogDao by lazy {
        database.callLogDao()
    }

    val callLogRepository: CallLogRepository by lazy {
        CallLogRepository(callLogDao)
    }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            initialized = true
        }
    }

    fun requireContext(): Context {
        check(initialized) { "ServiceLocator.init() must be called before use." }
        return appContext
    }
}
