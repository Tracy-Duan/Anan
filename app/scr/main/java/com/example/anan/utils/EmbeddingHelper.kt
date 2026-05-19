   package com.example.anan.utils

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

class EmbeddingHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingHelper"
        private const val EMBEDDING_DIM = 512  // bge-small-zh-v1.5 的输出维度是 512
        private const val MAX_SEQ_LENGTH = 512
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false
    
    /**
     * 初始化嵌入模型
     */
    fun initialize(modelPath: String): Boolean {
        return try {
            Log.d(TAG, "正在加载 BGE 嵌入模型: $modelPath")
            
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "模型文件不存在: $modelPath")
                return false
            }
            
            Log.d(TAG, "模型文件大小: ${modelFile.length() / 1024 / 1024} MB")
            
            ortEnv = OrtEnvironment.getEnvironment()
            
            // 直接使用默认选项加载
            Log.d(TAG, "正在创建 ONNX Session...")
            val sessionOptions = OrtSession.SessionOptions()
            session = ortEnv!!.createSession(modelPath, sessionOptions)
            isInitialized = true
            
            Log.d(TAG, "BGE 嵌入模型加载成功")
            Log.d(TAG, "输入节点: ${session!!.inputNames.joinToString(", ")}")
            Log.d(TAG, "输出节点: ${session!!.outputNames.joinToString(", ")}")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "BGE 嵌入模型加载失败: ${e.message}", e)
            isInitialized = false
            false
        }
    }
    
    /**
     * 将文本转换为向量
     */
    fun embed(text: String): FloatArray? {
        if (!isInitialized || session == null) {
            Log.e(TAG, "嵌入模型未初始化")
            return null
        }
        
        return try {
            // 使用简化的 tokenizer（生产环境建议使用真实的 BertTokenizer）
            val inputIds = LongArray(MAX_SEQ_LENGTH) { 0L }
            val attentionMask = LongArray(MAX_SEQ_LENGTH) { 0L }
            val tokenTypeIds = LongArray(MAX_SEQ_LENGTH) { 0L }
            
            val tokens = simpleTokenize(text)
            val seqLen = minOf(tokens.size, MAX_SEQ_LENGTH - 2)
            
            inputIds[0] = 101L // [CLS]
            attentionMask[0] = 1L
            
            for (i in 0 until seqLen) {
                inputIds[i + 1] = tokens[i].toLong()
                attentionMask[i + 1] = 1L
            }
            inputIds[seqLen + 1] = 102L // [SEP]
            attentionMask[seqLen + 1] = 1L
            
            val env = ortEnv!!
            
            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, MAX_SEQ_LENGTH.toLong()))
            val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), longArrayOf(1, MAX_SEQ_LENGTH.toLong()))
            val tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), longArrayOf(1, MAX_SEQ_LENGTH.toLong()))
            
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )
            
            val output = session!!.run(inputs)
            val result = output[0] as OnnxTensor
            
            // BGE 模型输出 last_hidden_state，取 [CLS] 标记的向量
            val floatBuffer = result.floatBuffer
            val embedding = FloatArray(EMBEDDING_DIM)
            
            // 提取第一个 token ([CLS]) 的向量
            for (i in 0 until EMBEDDING_DIM) {
                embedding[i] = floatBuffer[i]
            }
            
            // L2 归一化
            normalize(embedding)
            
            // 清理资源
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            output.close()
            
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "生成嵌入向量失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 简单的 Tokenizer 模拟
     * BGE 模型词汇表大小约为 21128，token ID 范围是 [0, 21127]
     * 特殊 token: [PAD]=0, [UNK]=100, [CLS]=101, [SEP]=102
     */
    private fun simpleTokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        for (char in text) {
            // 将字符映射到有效的 vocab 范围 [1000, 21127]
            // 确保不超过最大 vocab size (21128)
            val tokenId = (char.code % 20128) + 1000
            tokens.add(tokenId)
        }
        return tokens
    }
    
    /**
     * 向量归一化
     */
    private fun normalize(vector: FloatArray) {
        var norm = 0f
        for (v in vector) {
            norm += v * v
        }
        norm = sqrt(norm)
        
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            session?.close()
            ortEnv?.close()
            session = null
            ortEnv = null
            isInitialized = false
            Log.d(TAG, "嵌入模型资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        }
    }
}
