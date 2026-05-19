package com.example.anan.model

import java.util.UUID

data class KnowledgeFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val documents: List<KnowledgeDocument> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class KnowledgeDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val uploadedAt: Long = System.currentTimeMillis()
)
