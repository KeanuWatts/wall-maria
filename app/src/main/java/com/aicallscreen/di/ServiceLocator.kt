package com.aicallscreen.di

import android.content.Context
import android.telephony.TelephonyManager
import androidx.room.Room
import com.aicallscreen.BuildConfig
import com.aicallscreen.audio.CallAudioCapture
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.contacts.ContactResolver
import com.aicallscreen.data.local.AppDatabase
import com.aicallscreen.data.local.CallLogDao
import com.aicallscreen.data.repository.CallLogRepository
import com.aicallscreen.escalation.UserEscalationManager
import com.aicallscreen.llm.LLMProvider
import com.aicallscreen.routing.CallRoutingPolicy
import com.aicallscreen.rules.ScreeningRulesRepository
import com.aicallscreen.screening.AssistantHandoffController
import com.aicallscreen.screening.ConversationPipeline
import com.aicallscreen.screening.KnownContactAssistantOrchestrator
import com.aicallscreen.screening.UnknownCallerConversationOrchestrator
import com.aicallscreen.screening.UserTakeoverHandler
import com.aicallscreen.stt.WhisperCppEngine
import com.aicallscreen.telecom.CallControlCoordinator
import com.aicallscreen.telecom.KnownContactCallWatcher
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    val contactResolver: ContactResolver by lazy {
        ContactResolver(appContext)
    }

    val screeningRulesRepository: ScreeningRulesRepository by lazy {
        ScreeningRulesRepository(appContext)
    }

    val callRoutingPolicy: CallRoutingPolicy by lazy {
        CallRoutingPolicy(contactResolver, screeningRulesRepository)
    }

    val callControl: CallControlCoordinator by lazy {
        CallControlCoordinator(appContext)
    }

    val userEscalationManager: UserEscalationManager by lazy {
        UserEscalationManager(appContext)
    }

    val userTakeoverHandler: UserTakeoverHandler by lazy {
        UserTakeoverHandler(
            context = appContext,
            scope = screeningScope,
            ttsEngine = ttsEngine,
            audioFocusManager = audioFocusManager,
            userEscalationManager = userEscalationManager,
        )
    }

    val assistantHandoffController: AssistantHandoffController by lazy {
        AssistantHandoffController(appContext, screeningScope)
    }

    val conversationPipeline: ConversationPipeline by lazy {
        ConversationPipeline(
            ttsEngine = ttsEngine,
            audioCapture = callAudioCapture,
            speechToTextEngine = speechToTextEngine,
            llmEngine = llmProvider.activeEngine(),
        )
    }

    val unknownCallerOrchestrator: UnknownCallerConversationOrchestrator by lazy {
        UnknownCallerConversationOrchestrator(
            context = appContext,
            scope = screeningScope,
            audioFocusManager = audioFocusManager,
            conversationPipeline = conversationPipeline,
            ttsEngine = ttsEngine,
            llmEngine = llmProvider.activeEngine(),
            callLogRepository = callLogRepository,
            callControl = callControl,
            userEscalationManager = userEscalationManager,
        )
    }

    val knownContactAssistantOrchestrator: KnownContactAssistantOrchestrator by lazy {
        KnownContactAssistantOrchestrator(
            scope = screeningScope,
            audioFocusManager = audioFocusManager,
            conversationPipeline = conversationPipeline,
            ttsEngine = ttsEngine,
            callLogRepository = callLogRepository,
            callControl = callControl,
        )
    }

    val knownContactCallWatcher: KnownContactCallWatcher by lazy {
        KnownContactCallWatcher(
            context = appContext,
            scope = screeningScope,
            telephonyManager = appContext.getSystemService(TelephonyManager::class.java),
            assistantOrchestrator = knownContactAssistantOrchestrator,
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
            knownContactCallWatcher.start()
            initialized = true
        }
    }

    fun requireContext(): Context {
        check(initialized) { "ServiceLocator.init() must be called before use." }
        return appContext
    }
}
