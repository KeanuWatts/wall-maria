package com.aicallscreen.ui

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicallscreen.R
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.llm.ScreeningAction
import com.aicallscreen.service.ScreeningForegroundService
import com.aicallscreen.session.ScreeningSession
import com.aicallscreen.session.ScreeningSessionManager
import com.aicallscreen.session.ScreeningUiEvent
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Live transcript of the AI ↔ caller conversation with a Take Over action.
 */
class LiveScreeningActivity : AppCompatActivity() {

    private lateinit var session: ScreeningSession
    private val chatAdapter = ChatLineAdapter()
    private val chatLines = mutableListOf<ChatLine>()
    private var partialCallerLineId: String? = null
    private var lineCounter = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLockScreenDisplay()
        setContentView(R.layout.activity_live_screening)

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            ?: run { finish(); return }

        val resolved = ScreeningSessionManager.getSession(sessionId)
            ?: run { finish(); return }
        session = resolved

        findViewById<TextView>(R.id.liveCallerLabel).text =
            getString(R.string.live_screening_title, session.displayLabel)

        val statusLine = findViewById<TextView>(R.id.liveStatusLine)
        val list = findViewById<RecyclerView>(R.id.liveTranscriptList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = chatAdapter

        findViewById<MaterialButton>(R.id.buttonTakeOver).setOnClickListener {
            ServiceLocator.userTakeoverHandler.executeTakeover(session)
            statusLine.text = getString(R.string.live_status_takeover)
            findViewById<MaterialButton>(R.id.buttonTakeOver).isEnabled = false
        }

        lifecycleScope.launch {
            session.events.collectLatest { event ->
                renderEvent(event, statusLine, list)
            }
        }
    }

    private fun renderEvent(
        event: ScreeningUiEvent,
        statusLine: TextView,
        list: RecyclerView,
    ) {
        when (event) {
            is ScreeningUiEvent.Status -> {
                statusLine.text = event.message
                appendSystemLine(event.message)
            }
            is ScreeningUiEvent.AiSpeaking -> {
                statusLine.text = getString(R.string.live_status_ai_speaking)
                appendAiLine(event.text)
            }
            is ScreeningUiEvent.CallerPartial -> {
                statusLine.text = getString(R.string.live_status_caller_speaking)
                upsertCallerPartial(event.text)
            }
            is ScreeningUiEvent.CallerHeard -> {
                statusLine.text = getString(R.string.live_status_listening_done)
                finalizeCallerLine(event.text)
            }
            is ScreeningUiEvent.LlmThinking -> {
                statusLine.text = event.preview
            }
            is ScreeningUiEvent.LlmDecision -> {
                statusLine.text = decisionLabel(event.action)
                appendSystemLine(getString(R.string.live_llm_thoughts, event.thoughts))
            }
            is ScreeningUiEvent.SessionEnded -> {
                statusLine.text = event.summary
                appendSystemLine(event.summary)
                findViewById<MaterialButton>(R.id.buttonTakeOver).isEnabled =
                    event.reason != ScreeningUiEvent.EndReason.USER_TAKEOVER
            }
        }
        list.scrollToPosition(chatLines.lastIndex.coerceAtLeast(0))
    }

    private fun decisionLabel(action: ScreeningAction): String = when (action) {
        ScreeningAction.BLOCK_CALL -> getString(R.string.live_decision_block)
        ScreeningAction.CONNECT_TO_USER -> getString(R.string.live_decision_connect)
        ScreeningAction.TAKE_MESSAGE_AND_END -> getString(R.string.live_decision_message)
        ScreeningAction.CONTINUE_DIALOG -> getString(R.string.live_decision_continue)
    }

    private fun appendAiLine(text: String) {
        partialCallerLineId = null
        addLine(ChatLine(nextId(), getString(R.string.chat_role_ai), text, isAi = true))
    }

    private fun appendSystemLine(text: String) {
        addLine(ChatLine(nextId(), getString(R.string.chat_role_system), text, isAi = false, isSystem = true))
    }

    private fun upsertCallerPartial(text: String) {
        val id = partialCallerLineId ?: nextId().also { partialCallerLineId = it }
        val line = ChatLine(id, getString(R.string.chat_role_caller), text, isAi = false)
        val index = chatLines.indexOfFirst { it.id == id }
        if (index >= 0) {
            chatLines[index] = line
        } else {
            chatLines.add(line)
        }
        chatAdapter.submitList(chatLines.toList())
    }

    private fun finalizeCallerLine(text: String) {
        val id = partialCallerLineId ?: nextId()
        val line = ChatLine(id, getString(R.string.chat_role_caller), text, isAi = false)
        val index = chatLines.indexOfFirst { it.id == id }
        if (index >= 0) {
            chatLines[index] = line
        } else {
            chatLines.add(line)
        }
        partialCallerLineId = null
        chatAdapter.submitList(chatLines.toList())
    }

    private fun addLine(line: ChatLine) {
        chatLines.add(line)
        chatAdapter.submitList(chatLines.toList())
    }

    private fun nextId(): String = "line_${lineCounter++}"

    private fun enableLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    override fun onDestroy() {
        ScreeningForegroundService.stop(this)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"

        fun createIntent(context: android.content.Context, sessionId: String) =
            android.content.Intent(context, LiveScreeningActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }
}
