package com.example.anan.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anan.R
import com.example.anan.ui.components.ChatInput
import com.example.anan.ui.components.FolderSelectionDialog
import com.example.anan.ui.components.LoadingState
import com.example.anan.ui.components.MessageBubble
import com.example.anan.viewmodel.ChatViewModel
import com.example.anan.viewmodel.FolderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onTranslationClick: () -> Unit = {},
    onMyProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val viewModel: ChatViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(context) as T
            }
        }
    )
    
    val folderViewModel: FolderViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FolderViewModel(context) as T
            }
        }
    )
    
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isModelLoading by viewModel.isModelLoading.collectAsState()
    val modelLoaded by viewModel.modelLoaded.collectAsState()
    val error by viewModel.error.collectAsState()
    val listState = rememberLazyListState()
    
    // 知识检索模式状态
    var isKnowledgeSearchMode by remember { mutableStateOf(false) }
    // 选中的文件夹ID
    var selectedFolderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 是否显示文件夹选择对话框
    var showFolderDialog by remember { mutableStateOf(false) }
    
    val folders by folderViewModel.folders.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadModel()
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + if (isLoading) 1 else 0)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onTranslationClick) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = "翻译",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onMyProfileClick) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "我的",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (modelLoaded) {
                ChatInput(
                    onSend = { message ->
                        // 只有在开启知识检索模式且选中了文件夹时才传递folderIds
                        val folderIds = if (isKnowledgeSearchMode && selectedFolderIds.isNotEmpty()) {
                            selectedFolderIds
                        } else {
                            emptySet()
                        }
                        viewModel.sendMessage(message, folderIds)
                    },
                    onKnowledgeSearchClick = {
                        // 切换知识检索模式
                        isKnowledgeSearchMode = !isKnowledgeSearchMode
                        // 如果开启知识检索模式，显示文件夹选择对话框
                        if (!isKnowledgeSearchMode) {
                            // 关闭时清空选中的文件夹
                            selectedFolderIds = emptySet()
                        } else {
                            showFolderDialog = true
                        }
                    },
                    onStop = {
                        viewModel.stopGeneration()
                    },
                    isLoading = isLoading,
                    isEnabled = !isLoading,
                    isKnowledgeSearchMode = isKnowledgeSearchMode
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isModelLoading -> {
                    LoadingState(text = "正在加载AI模型...")
                }
                !modelLoaded -> {
                    LoadingState(text = error ?: "模型加载失败")
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            MessageBubble(message = message)
                        }
                        if (isLoading) {
                            item {
                                ThinkingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 文件夹选择对话框
    if (showFolderDialog) {
        FolderSelectionDialog(
            folders = folders ?: emptyList(),
            selectedFolderIds = selectedFolderIds,
            onFolderToggle = { folderId ->
                selectedFolderIds = if (selectedFolderIds.contains(folderId)) {
                    selectedFolderIds - folderId
                } else {
                    selectedFolderIds + folderId
                }
            },
            onDismiss = {
                showFolderDialog = false
                // 如果没有选中文件夹，关闭知识检索模式
                if (selectedFolderIds.isEmpty()) {
                    isKnowledgeSearchMode = false
                }
            },
            onConfirm = {
                showFolderDialog = false
                // 如果没有选中文件夹，关闭知识检索模式
                if (selectedFolderIds.isEmpty()) {
                    isKnowledgeSearchMode = false
                }
            }
        )
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF888888)
        )
        Text(
            text = "安安思考中...",
            color = Color(0xFF666666),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}