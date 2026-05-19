package com.example.anan.repository

import android.content.Context
import android.util.Log
import com.example.anan.utils.EmbeddingHelper
import com.example.anan.utils.WordDocumentParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 基于向量搜索的知识库
 * 使用语义向量进行相似度检索，支持智能判断是否需要查询知识库
 */
class VectorKnowledgeBase(private val context: Context) {
    
    companion object {
        private const val TAG = "VectorKB"
        private const val CHUNK_SIZE = 500
        private const val CHUNK_OVERLAP = 100
        private const val RELEVANCE_THRESHOLD = 0.40f  // 降低阈值，适应简化向量
    }
    
    private val knowledgeChunks = mutableListOf<KnowledgeChunk>()
    private val vectorIndex = VectorIndex()
    private val folderVectorIndices = mutableMapOf<String, VectorIndex>() // 每个文件夹独立的向量索引
    private val embeddingHelper = EmbeddingHelper(context)
    private val knowledgeDir = File(context.filesDir, "knowledge")
    private var isInitialized = false
    private val gson = Gson()
    
    data class KnowledgeChunk(
        val id: String,
        val sourceFile: String,
        val title: String,
        val content: String,
        val chunkIndex: Int,
        val category: String = "general",
        val embedding: FloatArray? = null
    )
    
    init {
        if (!knowledgeDir.exists()) {
            knowledgeDir.mkdirs()
        }
    }
    
    /**
     * 从磁盘加载持久化的数据（chunks 和文件夹索引）
     */
    private fun loadPersistedData() {
        try {
            val foldersDir = File(knowledgeDir, "folders")
            if (!foldersDir.exists()) {
                Log.d(TAG, "没有已保存的文件夹数据")
                return
            }
            
            // 遍历所有文件夹目录
            foldersDir.listFiles()?.forEach { folderDir ->
                if (folderDir.isDirectory) {
                    val folderId = folderDir.name
                    val indexFile = File(folderDir, "vector_index.dat")
                    val chunksFile = File(folderDir, "chunks.json")
                    
                    // 加载向量索引
                    if (indexFile.exists()) {
                        val folderIndex = VectorIndex()
                        val loaded = folderIndex.loadFromFile(indexFile)
                        if (loaded) {
                            folderVectorIndices[folderId] = folderIndex
                            Log.d(TAG, "加载文件夹 $folderId 的向量索引: ${folderIndex.size()} 条")
                        }
                    }
                    
                    // 加载 chunks
                    if (chunksFile.exists()) {
                        val loadedChunks = loadChunksFromFile(chunksFile)
                        knowledgeChunks.addAll(loadedChunks)
                        Log.d(TAG, "加载文件夹 $folderId 的 ${loadedChunks.size} 个 chunks")
                    }
                }
            }
            
            Log.d(TAG, "数据加载完成，共加载 ${folderVectorIndices.size} 个文件夹索引，${knowledgeChunks.size} 个知识块")
        } catch (e: Exception) {
            Log.e(TAG, "加载持久化数据失败", e)
        }
    }
    
    /**
     * 保存 chunks 到文件
     */
    private fun saveChunksToFile(chunks: List<KnowledgeChunk>, file: File) {
        try {
            val json = gson.toJson(chunks)
            file.writeText(json)
            Log.d(TAG, "保存 ${chunks.size} 个 chunks 到 ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "保存 chunks 失败", e)
        }
    }
    
    /**
     * 从文件加载 chunks
     */
    private fun loadChunksFromFile(file: File): List<KnowledgeChunk> {
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<KnowledgeChunk>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "加载 chunks 失败: ${file.name}", e)
            emptyList()
        }
    }
    
    /**
     * 初始化知识库（异步）
     */
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 加载嵌入模型
                val modelAssetPath = "models/bge-small-zh-v1.5.onnx"
                val modelFile = File(context.cacheDir, "bge-small-zh-v1.5.onnx")
                
                if (!modelFile.exists()) {
                    Log.d(TAG, "从 assets 复制嵌入模型...")
                    context.assets.open(modelAssetPath).use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    // 检查文件大小是否匹配（使用 InputStream 而非 openFd，避免压缩问题）
                    val assetSize = context.assets.open(modelAssetPath).use { it.available() }
                    if (modelFile.length() != assetSize.toLong()) {
                        Log.w(TAG, "模型文件大小不匹配，重新复制...")
                        Log.d(TAG, "Assets: $assetSize bytes, Cache: ${modelFile.length()} bytes")
                        context.assets.open(modelAssetPath).use { input ->
                            modelFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                val modelLoaded = embeddingHelper.initialize(modelFile.absolutePath)
                if (!modelLoaded) {
                    Log.e(TAG, "嵌入模型初始化失败")
                    return@withContext false
                }

                // 2. 加载已有的文件夹索引和 chunks
                loadPersistedData()
                
                isInitialized = true
                Log.d(TAG, "知识库初始化完成，已加载 ${knowledgeChunks.size} 个知识块")
                true
            } catch (e: Exception) {
                Log.e(TAG, "初始化知识库失败", e)
                false
            }
        }
    }
    
    /**
     * 从 Word 文档加载并构建索引
     */
    suspend fun loadFromWordDocuments(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val parser = WordDocumentParser()
                val documents = parser.parseWordFilesFromAssets(context, "knowledge")
                
                if (documents.isEmpty()) {
                    Log.w(TAG, "未找到 Word 文档")
                    return@withContext 0
                }
                
                knowledgeChunks.clear()
                vectorIndex.clear()
                
                var totalChunks = 0
                var vectorizedChunks = 0
                
                documents.forEach { doc ->
                    val chunks = splitIntoChunks(doc)
                    knowledgeChunks.addAll(chunks)
                    
                    // 为每个 chunk 生成向量
                    chunks.forEach { chunk ->
                        val embedding = embeddingHelper.embed(chunk.content)
                        if (embedding != null) {
                            val entry = VectorIndex.VectorEntry(
                                id = chunk.id,
                                vector = embedding,
                                metadata = mapOf(
                                    "title" to chunk.title,
                                    "source" to chunk.sourceFile,
                                    "content" to chunk.content
                                )
                            )
                            vectorIndex.add(entry)
                            vectorizedChunks++
                        }
                    }
                    
                    totalChunks += chunks.size
                    Log.d(TAG, "文件 '${doc.title}' 处理完成: ${chunks.size} 个知识块")
                }
                
                // 保存索引
                val indexFile = File(knowledgeDir, "vector_index.dat")
                vectorIndex.saveToFile(indexFile)
                
                isInitialized = true
                Log.d(TAG, "向量知识库构建完成: ${documents.size} 个文档, $totalChunks 个知识块, $vectorizedChunks 个已生成向量")
                
                totalChunks
            } catch (e: Exception) {
                Log.e(TAG, "加载 Word 文档失败", e)
                0
            }
        }
    }
    
    /**
     * 为指定文件夹构建向量索引（用户上传文档后调用）
     */
    suspend fun buildFolderIndex(folderId: String, documents: List<WordDocumentParser.DocumentContent>): Int {
        return withContext(Dispatchers.IO) {
            try {
                // 确保嵌入模型已初始化
                if (!isInitialized) {
                    Log.d(TAG, "嵌入模型未初始化，正在初始化...")
                    val initSuccess = initialize()
                    if (!initSuccess) {
                        Log.e(TAG, "嵌入模型初始化失败")
                        return@withContext 0
                    }
                }
                
                val folderIndex = VectorIndex()
                var totalChunks = 0
                var vectorizedChunks = 0
                
                documents.forEach { doc ->
                    // 修改 chunk ID 前缀，标记所属文件夹
                    val chunks = splitIntoChunksForFolder(doc, folderId)
                    knowledgeChunks.addAll(chunks)
                    Log.d(TAG, "已添加 ${chunks.size} 个 chunks 到 knowledgeChunks，当前总数: ${knowledgeChunks.size}")
                    
                    // 为每个 chunk 生成向量
                    chunks.forEach { chunk ->
                        val embedding = embeddingHelper.embed(chunk.content)
                        if (embedding != null) {
                            val entry = VectorIndex.VectorEntry(
                                id = chunk.id,
                                vector = embedding,
                                metadata = mapOf(
                                    "title" to chunk.title,
                                    "source" to chunk.sourceFile,
                                    "content" to chunk.content,
                                    "folderId" to folderId
                                )
                            )
                            folderIndex.add(entry)
                            vectorizedChunks++
                        } else {
                            Log.w(TAG, "Chunk 向量生成失败: ${chunk.id}")
                        }
                    }
                    
                    totalChunks += chunks.size
                    Log.d(TAG, "文件夹 $folderId - 文件 '${doc.title}' 处理完成: ${chunks.size} 个知识块")
                }
                
                // 保存该文件夹的索引
                val folderIndexDir = File(knowledgeDir, "folders/$folderId")
                if (!folderIndexDir.exists()) {
                    folderIndexDir.mkdirs()
                }
                val indexFile = File(folderIndexDir, "vector_index.dat")
                folderIndex.saveToFile(indexFile)
                
                // 保存该文件夹的 chunks（用于关键词搜索）
                val folderChunks = knowledgeChunks.filter { it.id.startsWith("folder_${folderId}_") }
                saveChunksToFile(folderChunks, File(folderIndexDir, "chunks.json"))
                
                Log.d(TAG, "文件夹 $folderId 向量索引构建完成: ${documents.size} 个文档, $totalChunks 个知识块")
                totalChunks
            } catch (e: Exception) {
                Log.e(TAG, "构建文件夹索引失败", e)
                0
            }
        }
    }
    
    /**
     * 从指定文件夹中搜索相关知识
     */
    suspend fun searchInFolders(query: String, folderIds: Set<String>, maxResults: Int = 3): SearchResult {
        return withContext(Dispatchers.IO) {
            if (!isInitialized || folderIds.isEmpty()) {
                Log.w(TAG, "知识库未初始化或未选择文件夹")
                return@withContext SearchResult(relevant = false, content = "")
            }
            
            try {
                Log.d(TAG, "开始在 ${folderIds.size} 个文件夹中搜索: $query")
                Log.d(TAG, "当前 knowledgeChunks 总数: ${knowledgeChunks.size}")
                
                val allResults = mutableListOf<Pair<KnowledgeChunk, Int>>()
                
                // 在每个选中的文件夹中搜索
                folderIds.forEach { folderId ->
                    val folderChunks = knowledgeChunks.filter { it.id.startsWith("folder_${folderId}_") }
                    Log.d(TAG, "文件夹 $folderId 找到 ${folderChunks.size} 个 chunks")
                    if (folderChunks.isNotEmpty()) {
                        val folderResults = keywordSearchWithSynonymsInChunks(query, folderChunks, maxResults)
                        Log.d(TAG, "文件夹 $folderId 搜索结果: ${folderResults.size} 条")
                        allResults.addAll(folderResults)
                    }
                }
                
                if (allResults.isEmpty()) {
                    Log.d(TAG, "在选中文件夹中未找到相关知识")
                    return@withContext SearchResult(relevant = false, content = "")
                }
                
                // 排序并取前 maxResults 个
                val topResults = allResults
                    .sortedByDescending { it.second }
                    .take(maxResults)
                
                // 格式化结果
                val content = topResults.joinToString("\n\n---\n\n") { (chunk, score) ->
                    "【来源: ${chunk.title}】(相关度: $score)\n${chunk.content}"
                }
                
                Log.d(TAG, "在 ${folderIds.size} 个文件夹中搜索完成，找到 ${topResults.size} 条相关知识")
                SearchResult(relevant = true, content = content, similarity = topResults.firstOrNull()?.second?.toFloat() ?: 0f)
                
            } catch (e: Exception) {
                Log.e(TAG, "搜索失败", e)
                SearchResult(relevant = false, content = "")
            }
        }
    }
    
    /**
     * 在指定 chunks 中进行关键词搜索
     */
    private fun keywordSearchWithSynonymsInChunks(query: String, chunks: List<KnowledgeChunk>, maxResults: Int): List<Pair<KnowledgeChunk, Int>> {
        val queryLower = query.lowercase()
        val expandedWords = expandSynonyms(queryLower)
        
        val scored = chunks.map { chunk ->
            var score = 0
            val contentLower = chunk.content.lowercase()
            val titleLower = chunk.title.lowercase()
            
            expandedWords.forEach { word ->
                val count = contentLower.split(word).size - 1
                score += count * 10
                
                if (titleLower.contains(word)) {
                    score += 50
                }
            }
            
            if (contentLower.contains(queryLower)) {
                score += 100
            }
            
            Pair(chunk, score)
        }.filter { it.second > 20 }
          .sortedByDescending { it.second }
          .take(maxResults)
        
        return scored
    }
    
    /**
     * 为文件夹分割文本块（带文件夹ID前缀）
     */
    private fun splitIntoChunksForFolder(doc: WordDocumentParser.DocumentContent, folderId: String): List<KnowledgeChunk> {
        val chunks = mutableListOf<KnowledgeChunk>()
        val paragraphs = doc.paragraphs
        
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        
        paragraphs.forEach { paragraph ->
            if (currentChunk.length + paragraph.length > CHUNK_SIZE && currentChunk.isNotEmpty()) {
                chunks.add(
                    KnowledgeChunk(
                        id = "folder_${folderId}_${doc.fileName}_chunk_$chunkIndex",
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
                    id = "folder_${folderId}_${doc.fileName}_chunk_$chunkIndex",
                    sourceFile = doc.fileName,
                    title = doc.title,
                    content = currentChunk.toString(),
                    chunkIndex = chunkIndex
                )
            )
        }
        
        return chunks
    }
    
    /**
     * 重建索引
     */
    private suspend fun rebuildIndex() {
        val count = loadFromWordDocuments()
        Log.d(TAG, "索引重建完成，共 $count 个知识块")
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
    
    /**
     * 智能搜索：结合向量搜索和关键词搜索
     */
    suspend fun smartSearch(query: String, maxResults: Int = 5): SearchResult {
        return withContext(Dispatchers.IO) {
            if (!isInitialized || knowledgeChunks.isEmpty()) {
                Log.w(TAG, "知识库未初始化")
                return@withContext SearchResult(relevant = false, content = "")
            }
            
            try {
                // 使用关键词搜索（更可靠）
                val results = keywordSearchWithSynonyms(query, maxResults)
                
                if (results.isEmpty()) {
                    Log.d(TAG, "未找到相关知识")
                    return@withContext SearchResult(relevant = false, content = "")
                }
                
                // 格式化结果
                val content = results.joinToString("\n\n---\n\n") { (chunk, score) ->
                    "【来源: ${chunk.title}】(相关度: $score)\n${chunk.content}"
                }
                
                Log.d(TAG, "搜索完成，找到 ${results.size} 条相关知识")
                SearchResult(relevant = true, content = content, similarity = results.firstOrNull()?.second?.toFloat() ?: 0f)
                
            } catch (e: Exception) {
                Log.e(TAG, "搜索失败", e)
                SearchResult(relevant = false, content = "")
            }
        }
    }
    
    /**
     * 关键词搜索 + 同义词扩展
     */
    private fun keywordSearchWithSynonyms(query: String, maxResults: Int): List<Pair<KnowledgeChunk, Int>> {
        val queryLower = query.lowercase()
        
        // 扩展同义词
        val expandedWords = expandSynonyms(queryLower)
        
        val scored = knowledgeChunks.map { chunk ->
            var score = 0
            val contentLower = chunk.content.lowercase()
            val titleLower = chunk.title.lowercase()
            
            // 原始查询词匹配
            expandedWords.forEach { word ->
                val count = contentLower.split(word).size - 1
                score += count * 10
                
                // 标题中匹配加倍
                if (titleLower.contains(word)) {
                    score += 50
                }
            }
            
            // 完整句子匹配加分
            if (contentLower.contains(queryLower)) {
                score += 100
            }
            
            Pair(chunk, score)
        }.filter { it.second > 20 }  // 过滤低分结果
          .sortedByDescending { it.second }
          .take(maxResults)
        
        return scored
    }
    
    /**
     * 同义词扩展
     */
    private fun expandSynonyms(query: String): List<String> {
        val words = query.split("\\s+".toRegex()).filter { it.length > 1 }
        val expanded = mutableListOf<String>()
        
        val synonymMap = mapOf(
            "转专业" to listOf("转专业", "转换专业", "调换专业", "变更专业", "专业调整"),
            "条件" to listOf("条件", "要求", "资格", "规定", "标准"),
            "流程" to listOf("流程", "步骤", "程序", "方法", "手续"),
            "申请" to listOf("申请", "报名", "提交", "填报"),
            "竞赛" to listOf("竞赛", "比赛", "赛事", "竞赓"),
            "学籍" to listOf("学籍", "学生档案", "注册", "学籍管理"),
            "考试" to listOf("考试", "考核", "测试", "测评"),
            "规定" to listOf("规定", "制度", "政策", "办法", "条例"),
            "管理" to listOf("管理", "规定", "办法", "细则")
        )
        
        words.forEach { word ->
            expanded.add(word)
            // 查找同义词
            synonymMap.forEach { (key, synonyms) ->
                if (word.contains(key) || key.contains(word)) {
                    expanded.addAll(synonyms)
                }
            }
        }
        
        return expanded.distinct()
    }
    
    /**
     * 搜索结果数据类
     */
    data class SearchResult(
        val relevant: Boolean,      // 是否相关
        val content: String,        // 检索内容
        val similarity: Float = 0f  // 最高相似度
    )
    
    fun getKnowledgeCount(): Int = knowledgeChunks.size
    
    fun getDocumentCount(): Int {
        return knowledgeChunks.map { it.sourceFile }.distinct().size
    }
    
    fun addKnowledge(title: String, content: String, category: String = "general") {
        // 简化版：直接添加到chunks，不生成向量（后续可以完善）
        val chunk = KnowledgeChunk(
            id = "custom_${System.currentTimeMillis()}",
            sourceFile = "manual_input",
            title = title,
            content = content,
            chunkIndex = 0,
            category = category
        )
        knowledgeChunks.add(chunk)
        Log.d(TAG, "添加新知识: $title")
    }
    
    fun release() {
        embeddingHelper.release()
        vectorIndex.clear()
        knowledgeChunks.clear()
        isInitialized = false
    }
}
