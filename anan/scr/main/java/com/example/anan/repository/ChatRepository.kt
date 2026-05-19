package com.example.anan.repository

import android.content.Context
import com.example.anan.data.AIProfile
import com.example.anan.data.Message
import com.example.anan.data.MessageStatus
import com.example.anan.data.ModelConfig
import com.example.anan.model.LLMModel
import com.example.anan.model.ModelFactory
import com.example.anan.repository.VectorKnowledgeBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(private val context: Context) {
    private var model: LLMModel? = null
    private val knowledgeBase = VectorKnowledgeBase(context)
    private val messages = mutableListOf<Message>()
    private var userName: String = "用户"  // 用户的名字

    /**
     * 设置用户的名字
     */
    fun setUserName(userName: String) {
        this.userName = userName
    }

    /**
     * 构建系统提示词
     */
    fun buildSystemPrompt(): String {
        return AIProfile.SYSTEM_PROMPT.replace("\${userName}", userName)
    }

    private fun getModel(): LLMModel {
        if (model == null) {
            model = ModelFactory.createNative(context)
        }
        return model!!
    }

    suspend fun loadModel(config: ModelConfig): Boolean {
        return withContext(Dispatchers.IO) {
            val result = getModel().loadModel(config)
            if (result) {
                initializeKnowledgeBase()
            }
            result
        }
    }

    private suspend fun initializeKnowledgeBase() {
        try {
            android.util.Log.d("ChatRepository", "初始化向量知识库...")

            val success = knowledgeBase.initialize()

            if (success) {
                android.util.Log.d("ChatRepository", "向量知识库初始化完成，等待用户上传文档")
            } else {
                android.util.Log.e("ChatRepository", "向量知识库初始化失败")
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "知识库初始化失败", e)
        }
    }

    fun isModelLoaded(): Boolean = model?.isModelLoaded() ?: false

    suspend fun sendMessage(
        content: String,
        folderIds: Set<String> = emptySet(),
        onPartial: (String) -> Unit = {},
        onUserMessageAdded: () -> Unit = {}
    ): Message {
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT
        )
        messages.add(userMessage)
        onUserMessageAdded()

        return withContext(Dispatchers.IO) {
            val aiMessageId = UUID.randomUUID().toString()
            var hasPartial = false

            // 构建包含历史对话的完整prompt
            val enhancedPrompt = if (folderIds.isNotEmpty()) {
                buildEnhancedPromptWithFolders(content, folderIds)
            } else {
                buildEnhancedPrompt(content)
            }

            val aiResponse = getModel().generateResponse(enhancedPrompt) { partial ->
                hasPartial = true
                val trimmed = partial.trimStart()
                val existing = messages.indexOfFirst { it.id == aiMessageId && !it.isUser }
                val partialMessage = Message(
                    id = aiMessageId,
                    content = trimmed,
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.RECEIVED
                )
                if (existing >= 0) {
                    messages[existing] = partialMessage
                } else {
                    messages.add(partialMessage)
                }
                onPartial(trimmed)
            }

            val trimmedResponse = aiResponse.trimStart()
            if (hasPartial) {
                val existing = messages.indexOfFirst { it.id == aiMessageId && !it.isUser }
                val finalMessage = Message(
                    id = aiMessageId,
                    content = trimmedResponse,
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.RECEIVED
                )
                if (existing >= 0) {
                    messages[existing] = finalMessage
                } else {
                    messages.add(finalMessage)
                }
                finalMessage
            } else {
                val aiMessage = Message(
                    id = aiMessageId,
                    content = trimmedResponse,
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.RECEIVED
                )
                messages.add(aiMessage)
                aiMessage
            }
        }
    }

    private suspend fun buildEnhancedPromptWithFolders(userQuery: String, folderIds: Set<String>): String {
        // 从最近对话中提取上下文关键词，增强搜索查询
        val enhancedQuery = extractContextKeywords(userQuery)

        // 使用增强后的查询进行搜索
        android.util.Log.d("ChatRepository", "原始查询: $userQuery, 增强查询: $enhancedQuery")
        val searchResult = knowledgeBase.searchInFolders(enhancedQuery, folderIds, maxResults = 3)

        // 获取最近对话作为上下文
        val recentConversations = getRecentConversationContext()

        // 构建完整的对话历史
        val conversationHistory = buildConversationHistory()

        return if (searchResult.relevant && searchResult.content.isNotBlank()) {
            """
$conversationHistory

【参考资料】
$searchResult

---

【用户问题】
$userQuery

请仔细阅读参考资料，结合最近的对话上下文，详细回答用户问题。要求：
1. **必须基于资料**：答案要从参考资料中提取，不要编造
2. **结合上下文**：理解用户问题与之前对话的关联（用户可能省略了前面提到的主题）
3. **详细完整**：把资料中的相关信息都整理出来，不要遗漏重点
4. **用自己的话**：不要直接复制资料，要用你自己的语气重新表达
5. **条理清晰**：如果内容较多，可以分点说明
6. **用中文回答**：保持友好、亲切的语气

Anan的回答：
""".trimIndent()
        } else {
            """
$conversationHistory

【用户问题】
$userQuery

请结合最近的对话上下文回答用户问题。注意理解用户问题的真实意图（用户可能省略了前面提到的主题，如从“南航竞赛等级”简化为“有什么等级”）。

Anan的回答：
""".trimIndent()
        }
    }

    /**
     * 从最近对话中提取上下文关键词
     * 用于增强搜索查询，解决"有什么等级"这类承继性问题
     *
     * 示例：
     * - 历史："南航竞赛有哪些等级？"
     * - 当前："有什么等级？"
     * - 提取："南航 竞赛"
     * - 最终查询："南航 竞赛 有什么等级？"
     */
    private fun extractContextKeywords(currentQuery: String): String {
        // 如果当前查询已经足够长（包含足够信息），直接使用
        if (currentQuery.length > 15) {
            return currentQuery
        }

        // 获取最近 4 条消息（2轮对话）
        val recentMessages = messages.takeLast(4)
        if (recentMessages.isEmpty()) {
            return currentQuery
        }

        // 从最近对话中提取关键词
        val contextText = recentMessages.joinToString(" ") { it.content }
        val keywords = extractKeywordsFromText(contextText)

        // 组合：关键词 + 当前查询
        return if (keywords.isNotEmpty()) {
            "$keywords $currentQuery"
        } else {
            currentQuery
        }
    }

    /**
     * 从文本中提取关键词（轻量级实现，不需要额外模型）
     * 提取规则：
     * 1. 提取长度>=2的中文字词
     * 2. 排除常见停用词
     * 3. 优先保留名词、专有名词
     */
    private fun extractKeywordsFromText(text: String): String {
        val stopWords = setOf(
            // 常见停用词
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上",
            "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这",
            "那", "哪", "什么", "怎么", "为什么", "吗", "呢", "吧", "啊", "哦", "呀",
            // 常见动词
            "问", "回答", "想", "知道", "了解", "觉得", "认为", "觉得",
            // 常见形容词
            "好", "大", "小", "多", "少", "高", "低", "长", "短"
        )

        // 简单的分词策略：按空格、标点分割
        val words = text.split(Regex("[\\s，。！？；：]+"))
            .filter { it.length >= 2 }  // 至少 2 个字符
            .filter { it !in stopWords }  // 排除停用词
            .filter { it.any { char -> char in '\u4e00'..'\u9fff' } }  // 只保留包含中文的词
            .distinct()  // 去重
            .take(5)  // 最多取 5 个关键词

        return words.joinToString(" ")
    }

    /**
     * 构建完整的对话历史（用于Prompt中）
     * 包含系统提示词和所有历史对话
     */
    private fun buildConversationHistory(): String {
        val systemPrompt = buildSystemPrompt()
        val historyMessages = messages.takeLast(10)  // 最多保留最近5轮对话
        
        if (historyMessages.isEmpty()) {
            return systemPrompt
        }

        val historyText = historyMessages.joinToString("\n") { msg ->
            if (msg.isUser) "$userName：${msg.content}" else "Anan：${msg.content}"
        }

        return "$systemPrompt\n\n$historyText"
    }

    /**
     * 获取最近的对话上下文（用于Prompt中展示给LLM）
     */
    private fun getRecentConversationContext(): String {
        val recentMessages = messages.takeLast(4)  // 最近2轮对话
        if (recentMessages.isEmpty()) {
            return "无"
        }

        return recentMessages.joinToString("\n") { msg ->
            if (msg.isUser) "$userName：${msg.content}" else "Anan：${msg.content}"
        }
    }

    /**
     * 构建普通对话prompt（不开启知识检索时使用）
     * 只包含系统提示词和对话历史，不进行任何知识库检索
     */
    private suspend fun buildEnhancedPrompt(userQuery: String): String {
        // 构建完整的对话历史
        val conversationHistory = buildConversationHistory()

        return """
$conversationHistory

---

【用户问题】
$userQuery

请结合你的性格设定回答用户问题。

Anan的回答：
""".trimIndent()
    }

    fun addKnowledgeFromText(title: String, content: String, category: String = "general") {
        knowledgeBase.addKnowledge(title, content, category)
    }

    fun getKnowledgeStats(): Pair<Int, Int> {
        return Pair(knowledgeBase.getKnowledgeCount(), knowledgeBase.getDocumentCount())
    }

    fun getMessages(): List<Message> = messages.toList()

    fun getKnowledgeBase(): VectorKnowledgeBase = knowledgeBase

    fun stopGeneration() {
        model?.stopGeneration()
    }

    fun release() {
        model?.release()
        model = null
    }
}