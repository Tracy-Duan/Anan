package com.example.anan.data

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val imageUrl: String? = null,
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    SENDING, SENT, RECEIVED, ERROR
}

data class ChatSession(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long,
    val messageCount: Int
)

data class ModelConfig(
    val modelPath: String = "models/Qwen3.5-2B-Polaris-HighIQ-INSTRUCT-Q4_K_M.gguf",
    val maxTokens: Int = 1536,
    val temperature: Float = 0.5f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val aiName: String = "安安",  // AI助手的名字
    val userName: String = "用户",  // 用户的名字
    val systemPrompt: String = "你是Anan。你清纯阳光、充满活力，洋溢着青春气息。说话风格自然随和，偶尔可爱俏皮但不过分，整体舒展大方略显可爱。你喜欢帮助别人，回答要条理清晰又亲切，知识面很广。你的开发者是Tracy Duan。\n\n重要规则：\n1. 必须用中文回答\n2. 保持友好、活泼的语气\n3. 回答要准确、有条理\n4. 不要编造事实\n5. 如果不知道答案，诚实地说不知道"
)
