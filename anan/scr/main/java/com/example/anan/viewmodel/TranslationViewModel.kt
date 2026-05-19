package com.example.anan.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anan.data.ModelConfig
import com.example.anan.model.LLMModel
import com.example.anan.model.ModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslationViewModel(private val context: Context) : ViewModel() {
    
    private val model: LLMModel = ModelFactory.createNative(context)
    
    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating
    
    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    init {
        // 异步加载模型
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                model.loadModel(ModelConfig())
            }
        }
    }
    
    /**
     * 执行翻译
     * @param text 待翻译文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     */
    fun translate(text: String, sourceLang: String, targetLang: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _isTranslating.value = true
            _error.value = null
            _translatedText.value = ""
            
            try {
                val result = withContext(Dispatchers.IO) {
                    performTranslation(text, sourceLang, targetLang)
                }
                _translatedText.value = result
            } catch (e: Exception) {
                _error.value = e.message ?: "翻译失败"
            } finally {
                _isTranslating.value = false
            }
        }
    }
    
    /**
     * 执行翻译（使用 AI 模型）
     */
    private suspend fun performTranslation(text: String, sourceLang: String, targetLang: String): String {
        if (!model.isModelLoaded()) {
            throw Exception("AI模型未加载，请稍后再试")
        }
        
        // 构建翻译提示词
        val prompt = buildTranslationPrompt(text, sourceLang, targetLang)
        
        // 使用 AI 模型进行翻译，传入空的回调
        return model.generateResponse(prompt) { }
    }
    
    /**
     * 构建翻译提示词
     */
    private fun buildTranslationPrompt(text: String, sourceLang: String, targetLang: String): String {
        return """
你是一个专业的翻译助手。请将以下${sourceLang}文本翻译成${targetLang}：

原文：${text}

要求：
1. 只输出翻译结果，不要包含任何解释或额外内容
2. 保持原文的语气和风格
3. 确保翻译准确、自然、流畅

翻译结果：
""".trimIndent()
    }
    
    override fun onCleared() {
        model.release()
        super.onCleared()
    }
}
