package com.example.anan.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anan.model.KnowledgeDocument
import com.example.anan.model.KnowledgeFolder
import kotlinx.coroutines.launch

@Composable
fun FolderDetailScreen(
    folderId: String,
    folders: List<KnowledgeFolder>,
    onUploadDocument: (String, String, Uri?) -> Unit,
    onDocumentDelete: (String) -> Unit,
    onSaveAndBuildIndex: suspend (String) -> Unit = {},
    onRenameFolder: (String, String) -> Unit = { _, _ -> }
) {
    val folder = folders.find { it.id == folderId }
    if (folder == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("文件夹不存在")
        }
        return
    }
    
    var showUploadDialog by remember { mutableStateOf(false) }
    var documentName by remember { mutableStateOf("") }
    var isBuildingIndex by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf(folder.name) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        }
        
        if (fileName == null) {
            fileName = uri.path
            val cut = fileName?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                fileName = fileName?.substring(cut + 1)
            }
        }
        
        return fileName
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileNameFromUri(it)
            if (fileName != null) {
                documentName = fileName
                onUploadDocument(folder.id, fileName, it)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF808080))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 标题（可点击修改）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 24.dp, start = 48.dp, end = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folder.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { 
                            newFolderName = folder.name
                            showRenameDialog = true
                        }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 文档列表区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    folder.documents.forEach { document ->
                        DocumentItem(
                            document = document,
                            onDelete = { onDocumentDelete(document.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 上传按钮
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/msword"
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7B68EE)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "上传文档",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "上传Word文档",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 保存并构建索引按钮
                    Button(
                        onClick = {
                            scope.launch {
                                isBuildingIndex = true
                                onSaveAndBuildIndex(folder.id)
                                isBuildingIndex = false
                            }
                        },
                        enabled = !isBuildingIndex && folder.documents.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (folder.documents.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isBuildingIndex) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在构建索引...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "保存并构建向量索引",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("上传文档") },
            text = {
                Column {
                    OutlinedTextField(
                        value = documentName,
                        onValueChange = { documentName = it },
                        label = { Text("文档名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "提示：实际上传功能需要实现文件选择器",
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (documentName.isNotBlank()) {
                            onUploadDocument(folder.id, documentName, null)
                            documentName = ""
                            showUploadDialog = false
                        }
                    }
                ) {
                    Text("上传")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 重命名文件夹对话框
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newValue -> newFolderName = newValue },
                    label = { Text("文件夹名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank() && newFolderName != folder.name) {
                            onRenameFolder(folder.id, newFolderName)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun DocumentItem(
    document: KnowledgeDocument,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Text(
                    text = "Word文档",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }

            // 删除按钮（扁平化设计）
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFEF4444)
                )
            ) {
                Text(
                    text = "删除",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
