package com.example.anan.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anan.data.Message
import com.example.anan.data.ModelConfig
import com.example.anan.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val context: Context) : ViewModel() {
    private val repository = ChatRepository(context)
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _isModelLoading = MutableStateFlow(false)
    val isModelLoading: StateFlow<Boolean> = _isModelLoading
    
    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadModel() {
        if (_isModelLoading.value || _modelLoaded.value) return
        
        viewModelScope.launch {
            _isModelLoading.value = true
            _error.value = null
            
            android.util.Log.d("ChatViewModel", "开始加载模型...")
            try {
                val success = repository.loadModel(ModelConfig())
                android.util.Log.d("ChatViewModel", "模型加载结果: $success")
                if (success) {
                    _modelLoaded.value = true
                    android.util.Log.d("ChatViewModel", "模型加载成功！")
                } else {
                    _error.value = "模型加载失败"
                    android.util.Log.e("ChatViewModel", "模型加载失败")
                }
            } catch (e: Exception) {
                _error.value = "模型加载出错: ${e.message}"
                android.util.Log.e("ChatViewModel", "模型加载异常", e)
            } finally {
                _isModelLoading.value = false
            }
        }
    }

    fun sendMessage(content: String, folderIds: Set<String> = emptySet()) {
        if (content.isBlank() || _isLoading.value) return
        
        viewModelScope.launch {
            _error.value = null
            
            try {
                repository.sendMessage(
                    content = content,
                    folderIds = folderIds,
                    onPartial = { partial ->
                        _messages.value = repository.getMessages()
                    },
                    onUserMessageAdded = {
                        _messages.value = repository.getMessages()
                        _isLoading.value = true
                    }
                )
                _messages.value = repository.getMessages()
            } catch (e: Exception) {
                _error.value = "发送失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun stopGeneration() {
        repository.stopGeneration()
        _isLoading.value = false
    }

    fun getMessages(): List<Message> = repository.getMessages()

    override fun onCleared() {
        repository.release()
        super.onCleared()
    }
}