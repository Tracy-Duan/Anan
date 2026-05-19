package com.example.anan.model

import com.example.anan.data.ModelConfig

interface LLMModel {
    suspend fun loadModel(config: ModelConfig): Boolean
    suspend fun generateResponse(prompt: String, callback: (String) -> Unit): String
    fun isModelLoaded(): Boolean
    fun stopGeneration()
    fun release()
}

class MockLLMModel : LLMModel {
    private var isLoaded = false

    override suspend fun loadModel(config: ModelConfig): Boolean {
        isLoaded = true
        return true
    }

    override suspend fun generateResponse(prompt: String, callback: (String) -> Unit): String {
        val responses = listOf(
            "这是一个测试响应。你问的是：$prompt",
            "感谢你的提问！关于 \"$prompt\"，我来帮你分析一下...",
            "好问题！让我想想... $prompt 这个话题很有趣。",
            "我理解你的需求。对于 \"$prompt\"，我的看法是：这是一个很好的问题，值得深入探讨。",
            "你说得对！\"$prompt\" 确实是一个值得关注的话题。让我详细解释一下..."
        )
        val response = responses.random()
        callback(response)
        return response
    }

    override fun isModelLoaded(): Boolean = isLoaded

    override fun stopGeneration() {
        // Mock 实现，无需操作
    }

    override fun release() {
        isLoaded = false
    }
}

object ModelFactory {
    fun create(): LLMModel {
        return MockLLMModel()
    }
    
    fun createNative(context: android.content.Context): LLMModel {
        return NativeLLMModel(context)
    }
}