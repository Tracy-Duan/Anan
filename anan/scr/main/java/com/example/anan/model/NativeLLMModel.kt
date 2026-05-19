package com.example.anan.model

import android.content.Context
import com.example.anan.data.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class NativeLLMModel(private val context: Context) : LLMModel {
    private var currentConfig: ModelConfig? = null

    private external fun nativeInit(nativeLibDir: String)
    
    private external fun nativeLoadModel(
        modelPath: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): Boolean

    private external fun nativeGenerateResponse(
        prompt: String,
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int
    ): String

    private external fun nativeIsModelLoaded(): Boolean

    private external fun nativeGetPartialResponse(): String

    private external fun nativeIsThinking(): Boolean

    private external fun nativeStopGeneration()

    private external fun nativeRelease()

    companion object {
        init {
            try {
                System.loadLibrary("anan")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }
    }

    init {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            nativeInit(nativeLibDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun loadModel(config: ModelConfig): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelAssetPath = config.modelPath
                android.util.Log.d("NativeLLMModel", "Starting model load, asset path: $modelAssetPath")
                
                val internalModelPath = copyModelToInternalStorage(modelAssetPath)

                if (internalModelPath != null) {
                    android.util.Log.d("NativeLLMModel", "Model copied to: $internalModelPath")
                    val result = nativeLoadModel(internalModelPath, config.maxTokens, config.temperature, config.topP)
                    android.util.Log.d("NativeLLMModel", "nativeLoadModel returned: $result")
                    if (result) {
                        currentConfig = config
                    }
                    result
                } else {
                    android.util.Log.e("NativeLLMModel", "Failed to copy model from assets")
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("NativeLLMModel", "Exception loading model", e)
                e.printStackTrace()
                false
            }
        }
    }

    override suspend fun generateResponse(prompt: String, callback: (String) -> Unit): String {
        return withContext(Dispatchers.IO) {
            try {
                val config = currentConfig ?: ModelConfig()
                var lastPartial = ""
                val generationJob = async {
                    nativeGenerateResponse(prompt, config.systemPrompt, config.temperature, config.topP, config.maxTokens)
                }

                while (generationJob.isActive) {
                    val partial = nativeGetPartialResponse()
                    if (partial.isNotEmpty() && partial != lastPartial) {
                        lastPartial = partial
                        withContext(Dispatchers.Main) { callback(partial) }
                    }
                    delay(200)
                }

                val response = generationJob.await()
                withContext(Dispatchers.Main) { callback(response) }
                response
            } catch (e: Exception) {
                android.util.Log.e("NativeLLMModel", "Error", e)
                "生成响应时出错: ${e.message}"
            }
        }
    }

    override fun isModelLoaded(): Boolean {
        return try {
            nativeIsModelLoaded()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun release() {
        try {
            nativeRelease()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopGeneration() {
        try {
            nativeStopGeneration()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyModelToInternalStorage(assetPath: String): String? {
        return try {
            val fileName = assetPath.substringAfterLast("/")
            val internalFile = File(context.filesDir, fileName)

            if (internalFile.exists() && internalFile.length() > 100 * 1024 * 1024) {
                android.util.Log.d("NativeLLMModel", "Model exists: ${internalFile.absolutePath}, size: ${internalFile.length() / (1024*1024)} MB")
                return internalFile.absolutePath
            }

            if (internalFile.exists()) {
                internalFile.delete()
            }

            android.util.Log.d("NativeLLMModel", "Copying model from assets/$assetPath to ${internalFile.absolutePath}...")
            val startTime = System.currentTimeMillis()

            context.assets.open(assetPath).use { inputStream ->
                internalFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            val finalSize = internalFile.length()
            android.util.Log.d("NativeLLMModel", "Model copied in ${elapsed}ms, final size: $finalSize bytes (${finalSize / (1024.0 * 1024.0)} MB)")

            if (finalSize < 100 * 1024 * 1024) {
                android.util.Log.e("NativeLLMModel", "Model file is suspiciously small ($finalSize bytes), may be corrupted")
                return null
            }

            internalFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("NativeLLMModel", "Failed to copy model from assets", e)
            e.printStackTrace()
            null
        }
    }
}