package com.example.anan.repository

import android.content.Context
import android.util.Log
import com.example.anan.utils.WordDocumentParser
import java.io.File

class KnowledgeBase(private val context: Context) {
    
    companion object {
        private const val TAG = "KnowledgeBase"
        private const val CHUNK_SIZE = 800
        private const val CHUNK_OVERLAP = 100
    }
    
    data class KnowledgeChunk(
        val id: String,
        val sourceFile: String,
        val title: String,
        val content: String,
        val chunkIndex: Int,
        val category: String = "general"
    )
    
    private val knowledgeChunks = mutableListOf<KnowledgeChunk>()
    private val knowledgeDir = File(context.filesDir, "knowledge")
    
    init {
        if (!knowledgeDir.exists()) {
            knowledgeDir.mkdirs()
        }
    }
    
    fun loadFromWordDocuments(): Int {
        val parser = WordDocumentParser()
        val documents = parser.parseWordFilesFromAssets(context, "knowledge")
        
        if (documents.isEmpty()) {
            Log.w(TAG, "未找到 Word 文档")
            return 0
        }
        
        knowledgeChunks.clear()
        var totalChunks = 0
        
        documents.forEach { doc ->
            val chunks = splitIntoChunks(doc)
            knowledgeChunks.addAll(chunks)
            totalChunks += chunks.size
            Log.d(TAG, "文件 '${doc.title}' 分割为 ${chunks.size} 个知识块")
        }
        
        Log.d(TAG, "知识库加载完成，共 ${documents.size} 个文档，$totalChunks 个知识块")
        saveToDisk()
        
        return totalChunks
    }
    
    private fun splitIntoChunks(doc: WordDocumentParser.DocumentContent): List<KnowledgeChunk> {
        val chunks = mutableListOf<KnowledgeChunk>()
        val paragraphs = doc.paragraphs
        
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        
        paragraphs.forEach { paragraph ->
            if (currentChunk.length + paragraph.length > CHUNK_SIZE && currentChunk.isNotEmpty()) {
                chunks.add(
                    KnowledgeChunk(
                        id = "${doc.fileName}_chunk_$chunkIndex",
                        sourceFile = doc.fileName,
                        title = doc.title,
                        content = currentChunk.toString(),
                        chunkIndex = chunkIndex
                    )
                )
                chunkIndex++
                
                val overlapText = getOverlapText(currentChunk.toString())
                currentChunk = StringBuilder(overlapText)
            }
            
            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
            }
            currentChunk.append(paragraph)
        }
        
        if (currentChunk.isNotEmpty()) {
            chunks.add(
                KnowledgeChunk(
                    id = "${doc.fileName}_chunk_$chunkIndex",
                    sourceFile = doc.fileName,
                    title = doc.title,
                    content = currentChunk.toString(),
                    chunkIndex = chunkIndex
                )
            )
        }
        
        return chunks
    }
    
    private fun getOverlapText(fullText: String): String {
        if (fullText.length <= CHUNK_OVERLAP) return fullText
        
        val lastParagraph = fullText.split("\n\n").lastOrNull() ?: ""
        return if (lastParagraph.length > CHUNK_OVERLAP) {
            lastParagraph.takeLast(CHUNK_OVERLAP)
        } else {
            lastParagraph
        }
    }
    
    fun searchKnowledge(query: String, maxResults: Int = 5): String {
        if (knowledgeChunks.isEmpty()) {
            Log.w(TAG, "知识库为空")
            return ""
        }
        
        val queryLower = query.lowercase()
        val queryWords = queryLower.split("\\s+".toRegex()).filter { it.length > 1 }
        
        val scored = knowledgeChunks.map { chunk ->
            val score = calculateRelevanceScore(chunk.content, queryLower, queryWords)
            Pair(chunk, score)
        }.filter { it.second > 0 }
          .sortedByDescending { it.second }
          .take(maxResults)
        
        if (scored.isEmpty()) {
            Log.d(TAG, "未找到相关知识")
            return ""
        }
        
        val result = scored.joinToString("\n\n---\n\n") { (chunk, _) ->
            "【来源: ${chunk.title}】\n${chunk.content}"
        }
        
        Log.d(TAG, "检索到 ${scored.size} 条相关知识")
        return result
    }
    
    private fun calculateRelevanceScore(content: String, query: String, queryWords: List<String>): Int {
        var score = 0
        val contentLower = content.lowercase()
        
        queryWords.forEach { word ->
            val count = contentLower.split(word).size - 1
            score += count * 15
        }
        
        if (contentLower.contains(query)) {
            score += 100
        }
        
        val sentences = content.split("[。！？.!?]".toRegex())
        sentences.forEach { sentence ->
            val sentenceLower = sentence.lowercase()
            var matchCount = 0
            queryWords.forEach { word ->
                if (sentenceLower.contains(word)) matchCount++
            }
            if (matchCount >= 2) {
                score += matchCount * 20
            }
            if (matchCount >= queryWords.size / 2) {
                score += 50
            }
        }
        
        val titleMatch = queryWords.count { word ->
            contentLower.contains(word)
        }
        score += titleMatch * 30
        
        return score
    }
    
    fun addKnowledge(title: String, content: String, category: String = "general") {
        val chunks = splitTextIntoChunks(content, title, category)
        knowledgeChunks.addAll(chunks)
        saveToDisk()
        Log.d(TAG, "添加新知识: ${chunks.size} 个知识块")
    }
    
    private fun splitTextIntoChunks(text: String, title: String, category: String): List<KnowledgeChunk> {
        val chunks = mutableListOf<KnowledgeChunk>()
        val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
        
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        
        paragraphs.forEach { paragraph ->
            if (currentChunk.length + paragraph.length > CHUNK_SIZE && currentChunk.isNotEmpty()) {
                chunks.add(
                    KnowledgeChunk(
                        id = "custom_${System.currentTimeMillis()}_$chunkIndex",
                        sourceFile = "manual_input",
                        title = title,
                        content = currentChunk.toString(),
                        chunkIndex = chunkIndex,
                        category = category
                    )
                )
                chunkIndex++
                currentChunk = StringBuilder()
            }
            
            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
            }
            currentChunk.append(paragraph.trim())
        }
        
        if (currentChunk.isNotEmpty()) {
            chunks.add(
                KnowledgeChunk(
                    id = "custom_${System.currentTimeMillis()}_$chunkIndex",
                    sourceFile = "manual_input",
                    title = title,
                    content = currentChunk.toString(),
                    chunkIndex = chunkIndex,
                    category = category
                )
            )
        }
        
        return chunks
    }
    
    private fun saveToDisk() {
        try {
            val json = knowledgeChunks.joinToString("|||") { chunk ->
                "${chunk.id}::${chunk.sourceFile}::${chunk.title}::${chunk.content.replace("\n", "\\n")}::${chunk.chunkIndex}::${chunk.category}"
            }
            
            val saveFile = File(knowledgeDir, "knowledge_index.txt")
            saveFile.writeText(json)
            Log.d(TAG, "知识库已保存到磁盘")
        } catch (e: Exception) {
            Log.e(TAG, "保存知识库失败", e)
        }
    }
    
    fun loadFromDisk(): Boolean {
        return try {
            val saveFile = File(knowledgeDir, "knowledge_index.txt")
            if (!saveFile.exists()) return false
            
            val json = saveFile.readText()
            if (json.isBlank()) return false
            
            knowledgeChunks.clear()
            
            json.split("|||").forEach { chunkStr ->
                val parts = chunkStr.split("::")
                if (parts.size == 6) {
                    knowledgeChunks.add(
                        KnowledgeChunk(
                            id = parts[0],
                            sourceFile = parts[1],
                            title = parts[2],
                            content = parts[3].replace("\\n", "\n"),
                            chunkIndex = parts[4].toInt(),
                            category = parts[5]
                        )
                    )
                }
            }
            
            Log.d(TAG, "从磁盘加载知识库: ${knowledgeChunks.size} 个知识块")
            true
        } catch (e: Exception) {
            Log.e(TAG, "从磁盘加载知识库失败", e)
            false
        }
    }
    
    fun getKnowledgeCount(): Int = knowledgeChunks.size
    
    fun getDocumentCount(): Int {
        return knowledgeChunks.map { it.sourceFile }.distinct().size
    }
    
    fun clearKnowledge() {
        knowledgeChunks.clear()
        val saveFile = File(knowledgeDir, "knowledge_index.txt")
        if (saveFile.exists()) {
            saveFile.delete()
        }
        Log.d(TAG, "知识库已清空")
    }
}
