package com.aicallscreen.session

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

class ScreeningSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val phoneNumber: String,
    val displayLabel: String,
    val sessionType: ScreeningSessionType,
    val ownerDisplayName: String = "",
    val contactDisplayName: String = "",
) {

    private val _events = MutableSharedFlow<ScreeningUiEvent>(
        replay = 32,
        extraBufferCapacity = 128,
    )
    val events: SharedFlow<ScreeningUiEvent> = _events.asSharedFlow()

    @Volatile
    var userTakeoverRequested: Boolean = false

    @Volatile
    var cancelled: Boolean = false

    @Volatile
    private var pipelineJob: Job? = null

    fun attachPipelineJob(job: Job) {
        pipelineJob = job
    }

    fun isPipelineActive(): Boolean = !cancelled && !userTakeoverRequested

    suspend fun emit(event: ScreeningUiEvent) {
        _events.emit(event)
    }

    fun requestTakeover() {
        userTakeoverRequested = true
        cancelled = true
        pipelineJob?.cancel()
    }

    fun cancel() {
        cancelled = true
        pipelineJob?.cancel()
    }
}
