package com.aicallscreen.llm

import android.content.Context
import com.aicallscreen.di.ServiceLocator

/**
 * Strategy factory that selects on-device or cloud LLM implementations at runtime.
 */
class LLMProvider(
    context: Context,
    private val useCloud: Boolean,
    private val cloudBaseUrl: String,
) {

    private val onDeviceEngine: ILocalLLMEngine by lazy {
        OnDeviceLlamaEngine(scope = ServiceLocator.screeningScope)
    }

    private val cloudEngine: ILocalLLMEngine by lazy {
        CloudApiLlamaEngine(
            scope = ServiceLocator.screeningScope,
            baseUrl = cloudBaseUrl,
        )
    }

    @Volatile
    private var runtimeUseCloud: Boolean = useCloud

    fun setUseCloud(enabled: Boolean) {
        runtimeUseCloud = enabled
    }

    fun isCloudEnabled(): Boolean = runtimeUseCloud

    fun activeEngine(): ILocalLLMEngine =
        if (runtimeUseCloud) cloudEngine else onDeviceEngine
}
