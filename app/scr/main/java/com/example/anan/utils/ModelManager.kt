package com.example.anan.utils

import android.content.Context
import com.example.anan.data.ModelConfig
import com.example.anan.model.LLMModel
import com.example.anan.model.ModelFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 全局模型管理器 - 单例模式
 * 确保整个应用中只有一个模型实例，避免资源冲突
 */
object ModelManager {
    
    private var model: LLMModel? = null
    private val mutex = Mutex()
    private var isInitialized = false
    private var context: Context? = null
    
    /**
     * 获取模型实例（单例）
     */
    fun getModel(context: Context): LLMModel {
        if (model == null || this.context == null) {
            this.context = context.applicationContext
            model = ModelFactory.createNative(this.context!!)
        }
        return model!!
    }
    
    /**
     * 加载模型
     */
    suspend fun loadModel(config: ModelConfig): Boolean {
        return mutex.withLock {
            if (isInitialized) return@withLock true
            
            val currentModel = model ?: return@withLock false
            val result = currentModel.loadModel(config)
            
            if (result) {
                isInitialized = true
            }
            result
        }
    }
    
    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean {
        return model?.isModelLoaded() ?: false
    }
    
    /**
     * 释放模型资源
     */
    fun release() {
        model?.release()
        model = null
        isInitialized = false
        context = null
    }
}
