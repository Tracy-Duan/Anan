package com.example.anan.repository

import android.util.Log
import java.io.File

/**
 * 轻量级向量索引
 * 使用内存存储，支持快速的向量相似度搜索
 */
class VectorIndex {
    
    companion object {
        private const val TAG = "VectorIndex"
        private const val SIMILARITY_THRESHOLD = 0.40f  // 降低阈值，提高召回率
    }
    
    data class VectorEntry(
        val id: String,
        val vector: FloatArray,
        val metadata: Map<String, String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as VectorEntry
            return id == other.id
        }
        
        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
    
    private val entries = mutableListOf<VectorEntry>()
    
    /**
     * 添加向量条目
     */
    fun add(entry: VectorEntry) {
        entries.add(entry)
    }
    
    /**
     * 批量添加
     */
    fun addAll(newEntries: List<VectorEntry>) {
        entries.addAll(newEntries)
        Log.d(TAG, "向量索引更新，当前共 ${entries.size} 条")
    }
    
    /**
     * 清空索引
     */
    fun clear() {
        entries.clear()
    }
    
    /**
     * 搜索最相似的向量
     * @param queryVector 查询向量
     * @param topK 返回前K个结果
     * @param threshold 相似度阈值
     * @return 按相似度排序的结果列表
     */
    fun search(
        queryVector: FloatArray,
        topK: Int = 5,
        threshold: Float = SIMILARITY_THRESHOLD
    ): List<Pair<VectorEntry, Float>> {
        if (entries.isEmpty()) {
            Log.w(TAG, "向量索引为空")
            return emptyList()
        }
        
        val scored = entries.mapNotNull { entry ->
            val similarity = cosineSimilarity(queryVector, entry.vector)
            if (similarity >= threshold) {
                Pair(entry, similarity)
            } else {
                null
            }
        }.sortedByDescending { it.second }
         .take(topK)
        
        Log.d(TAG, "向量搜索完成，找到 ${scored.size} 条相关结果（阈值: $threshold）")
        return scored
    }
    
    /**
     * 检查是否有相关内容
     * @return 最高相似度
     */
    fun checkRelevance(queryVector: FloatArray): Float {
        if (entries.isEmpty()) return 0f
        
        var maxSimilarity = 0f
        for (entry in entries) {
            val sim = cosineSimilarity(queryVector, entry.vector)
            if (sim > maxSimilarity) {
                maxSimilarity = sim
                if (maxSimilarity > 0.9f) break  // 已经很相似了，提前退出
            }
        }
        
        return maxSimilarity
    }
    
    /**
     * 计算余弦相似度
     */
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        if (norm1 == 0f || norm2 == 0f) return 0f
        
        return dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
    }
    
    /**
     * 获取索引大小
     */
    fun size(): Int = entries.size
    
    /**
     * 保存到文件（简化版，实际应该序列化向量）
     */
    fun saveToFile(file: File) {
        try {
            val data = entries.joinToString("|||") { entry ->
                val vectorStr = entry.vector.joinToString(",") { "%.6f".format(it) }
                val metadataStr = entry.metadata.entries.joinToString(";") { "${it.key}=${it.value}" }
                "${entry.id}::${vectorStr}::${metadataStr}"
            }
            file.writeText(data)
            Log.d(TAG, "向量索引已保存: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "保存向量索引失败", e)
        }
    }
    
    /**
     * 从文件加载
     */
    fun loadFromFile(file: File): Boolean {
        return try {
            if (!file.exists()) return false
            
            val data = file.readText()
            if (data.isBlank()) return false
            
            entries.clear()
            
            data.split("|||").forEach { entryStr ->
                val parts = entryStr.split("::")
                if (parts.size == 3) {
                    val id = parts[0]
                    val vector = parts[1].split(",").map { it.toFloat() }.toFloatArray()
                    val metadata = parts[2].split(";").associate { 
                        val kv = it.split("=")
                        if (kv.size == 2) kv[0] to kv[1] else "" to ""
                    }
                    
                    entries.add(VectorEntry(id, vector, metadata))
                }
            }
            
            Log.d(TAG, "向量索引已加载: ${entries.size} 条")
            true
        } catch (e: Exception) {
            Log.e(TAG, "加载向量索引失败", e)
            false
        }
    }
}
