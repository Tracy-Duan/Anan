package com.example.anan.utils

import android.content.Context
import android.util.Log
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

class WordDocumentParser {
    
    companion object {
        private const val TAG = "WordParser"
    }
    
    data class DocumentContent(
        val fileName: String,
        val title: String,
        val content: String,
        val paragraphs: List<String>
    )
    
    fun parseWordFile(file: File): DocumentContent? {
        return try {
            Log.d(TAG, "开始解析 Word 文件: ${file.name}")
            
            FileInputStream(file).use { inputStream ->
                XWPFDocument(inputStream).use { document ->
                    val paragraphs = mutableListOf<String>()
                    
                    document.paragraphs.forEach { paragraph ->
                        val text = paragraph.text.trim()
                        if (text.isNotEmpty()) {
                            paragraphs.add(text)
                        }
                    }
                    
                    val fullContent = paragraphs.joinToString("\n\n")
                    val title = extractTitle(paragraphs, file.nameWithoutExtension)
                    
                    Log.d(TAG, "解析完成: ${paragraphs.size} 个段落, ${fullContent.length} 字符")
                    
                    DocumentContent(
                        fileName = file.name,
                        title = title,
                        content = fullContent,
                        paragraphs = paragraphs
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 Word 文件失败: ${file.name}", e)
            null
        }
    }
    
    fun parseWordFilesFromAssets(context: Context, assetFolder: String = "knowledge"): List<DocumentContent> {
        val documents = mutableListOf<DocumentContent>()
        
        try {
            val fileList = context.assets.list(assetFolder)
            if (fileList.isNullOrEmpty()) {
                Log.w(TAG, "资产目录为空: $assetFolder")
                return documents
            }
            
            val wordFiles = fileList.filter { 
                it.endsWith(".docx", ignoreCase = true) || it.endsWith(".doc", ignoreCase = true)
            }
            
            Log.d(TAG, "找到 ${wordFiles.size} 个 Word 文件")
            
            wordFiles.forEach { fileName ->
                val tempFile = copyAssetToTemp(context, "$assetFolder/$fileName", fileName)
                if (tempFile != null) {
                    val docContent = parseWordFile(tempFile)
                    if (docContent != null) {
                        documents.add(docContent)
                    }
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取资产目录失败", e)
        }
        
        return documents
    }
    
    private fun copyAssetToTemp(context: Context, assetPath: String, fileName: String): File? {
        return try {
            val tempFile = File(context.cacheDir, fileName)
            context.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "复制资产文件失败: $assetPath", e)
            null
        }
    }
    
    private fun extractTitle(paragraphs: List<String>, defaultName: String): String {
        if (paragraphs.isEmpty()) return defaultName
        
        val firstParagraph = paragraphs[0]
        return if (firstParagraph.length <= 100) {
            firstParagraph
        } else {
            defaultName
        }
    }
}
