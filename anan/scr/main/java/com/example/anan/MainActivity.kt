package com.example.anan

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.anan.navigation.Screen
import com.example.anan.repository.ChatRepository
import com.example.anan.ui.ChatScreen
import com.example.anan.ui.screens.EditProfileScreen
import com.example.anan.ui.screens.FolderDetailScreen
import com.example.anan.ui.screens.MyProfileScreen
import com.example.anan.ui.screens.TranslationScreen
import com.example.anan.ui.screens.UserAgreementScreen
import com.example.anan.utils.WordDocumentParser
import com.example.anan.viewmodel.FolderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var chatRepository: ChatRepository? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 加载保存的用户资料
        val prefs = getSharedPreferences("user_profile", MODE_PRIVATE)
        val savedUserName = prefs.getString("user_name", "用户") ?: "用户"
        val savedAvatarPath = prefs.getString("user_avatar", null)
        
        setContent {
            // 用户资料状态 - 移到setContent内部
            var userProfileName by remember { mutableStateOf(savedUserName) }  // 用户自己的名字
            var userProfileAvatar by remember { mutableStateOf<String?>(savedAvatarPath) }
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isLoading by remember { mutableStateOf(true) }
                    val navController = rememberNavController()
                    val folderViewModel: FolderViewModel = viewModel(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                return FolderViewModel(this@MainActivity) as T
                            }
                        }
                    )
                    
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            chatRepository = ChatRepository(this@MainActivity)
                        }
                        isLoading = false
                    }
                    
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF808080)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF6366F1),
                                    strokeWidth = 4.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "安安正在启动...",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Chat.route,
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None }
                        ) {
                            composable(Screen.Chat.route) {
                                ChatScreen(
                                    onTranslationClick = { navController.navigate(Screen.Translation.route) },
                                    onMyProfileClick = { navController.navigate(Screen.MyProfile.route) }
                                )
                            }
                            
                            composable(Screen.MyProfile.route) {
                                val folders by folderViewModel.folders.collectAsState()
                                MyProfileScreen(
                                    onFolderClick = { folder ->
                                        navController.navigate(Screen.FolderDetail.createRoute(folder.id))
                                    },
                                    folders = folders,
                                    onAddFolder = { name -> folderViewModel.addFolder(name) },
                                    onEditProfileClick = { navController.navigate(Screen.EditProfile.route) },
                                    onUserAgreementClick = { navController.navigate(Screen.UserAgreement.route) },
                                    userName = userProfileName,
                                    userAvatar = userProfileAvatar
                                )
                            }
                            
                            composable(Screen.Translation.route) {
                                TranslationScreen()
                            }
                            
                            composable(Screen.EditProfile.route) {
                                val avatarPickerLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.PickVisualMedia()
                                ) { uri ->
                                    if (uri != null) {
                                        val avatarDir = File(filesDir, "avatar")
                                        if (!avatarDir.exists()) avatarDir.mkdirs()
                                        val avatarFile = File(avatarDir, "profile_avatar.jpg")
                                        try {
                                            contentResolver.openInputStream(uri)?.use { input ->
                                                avatarFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            userProfileAvatar = avatarFile.absolutePath
                                            Toast.makeText(this@MainActivity, "✅ 头像已更新", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "❌ 头像更新失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                
                                EditProfileScreen(
                                    onSave = { userName, avatarUri ->
                                        userProfileName = userName
                                        chatRepository?.setUserName(userName)
                                        if (avatarUri != null) userProfileAvatar = avatarUri
                                        
                                        // 持久化保存用户资料
                                        prefs.edit()
                                            .putString("user_name", userName)
                                            .apply()
                                        if (avatarUri != null) {
                                            prefs.edit()
                                                .putString("user_avatar", avatarUri)
                                                .apply()
                                        }
                                        
                                        Toast.makeText(this@MainActivity, "✅ 资料已保存", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    },
                                    onPickAvatarClick = {
                                        avatarPickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    currentAvatarUri = userProfileAvatar,
                                    currentUserName = userProfileName
                                )
                            }
                            
                            composable(Screen.UserAgreement.route) {
                                UserAgreementScreen()
                            }
                            
                            composable(Screen.FolderDetail.route) { backStackEntry ->
                                val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                                val folders by folderViewModel.folders.collectAsState()
                                
                                FolderDetailScreen(
                                    folderId = folderId,
                                    folders = folders,
                                    onUploadDocument = { fid, docName, uri ->
                                        if (uri != null) {
                                            val destFile = copyUriToFile(uri, fid, docName)
                                            if (destFile != null) {
                                                folderViewModel.addDocument(fid, docName, destFile.absolutePath)
                                                android.util.Log.d("MainActivity", "文件已保存: ${destFile.absolutePath}")
                                                Toast.makeText(this@MainActivity, "✅ 文档上传成功", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "❌ 文档上传失败", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            folderViewModel.addDocument(fid, docName)
                                        }
                                    },
                                    onDocumentDelete = { docId ->
                                        folderViewModel.deleteDocument(folderId, docId)
                                    },
                                    onSaveAndBuildIndex = { fid ->
                                        runBlocking {
                                            try {
                                                android.util.Log.d("MainActivity", "开始为文件夹 $fid 构建向量索引...")
                                                val currentFolder = folderViewModel.getFolder(fid)
                                                if (currentFolder == null) {
                                                    android.util.Log.w("MainActivity", "文件夹 $fid 不存在")
                                                    runOnUiThread {
                                                        Toast.makeText(this@MainActivity, "❌ 文件夹不存在", Toast.LENGTH_SHORT).show()
                                                    }
                                                    return@runBlocking
                                                }
                                                val parser = WordDocumentParser()
                                                val documents = mutableListOf<com.example.anan.utils.WordDocumentParser.DocumentContent>()
                                                currentFolder.documents.forEach { doc ->
                                                    if (doc.filePath.isNotBlank()) {
                                                        val file = File(doc.filePath)
                                                        if (file.exists()) {
                                                            val content = parser.parseWordFile(file)
                                                            if (content != null) {
                                                                documents.add(content)
                                                                android.util.Log.d("MainActivity", "已解析文档: ${doc.name}")
                                                            }
                                                        } else {
                                                            android.util.Log.w("MainActivity", "文件不存在: ${doc.filePath}")
                                                        }
                                                    }
                                                }
                                                if (documents.isNotEmpty()) {
                                                    val repository = chatRepository
                                                    if (repository != null) {
                                                        val count = repository.getKnowledgeBase().buildFolderIndex(fid, documents)
                                                        android.util.Log.d("MainActivity", "文件夹 $fid 向量索引构建完成: $count 个知识块")
                                                        runOnUiThread {
                                                            Toast.makeText(this@MainActivity, "✅ 索引构建成功！共 $count 个知识块", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                } else {
                                                    android.util.Log.w("MainActivity", "文件夹 $fid 中没有可解析的文档")
                                                    runOnUiThread {
                                                        Toast.makeText(this@MainActivity, "⚠️ 没有可解析的文档", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("MainActivity", "构建向量索引失败", e)
                                                runOnUiThread {
                                                    Toast.makeText(this@MainActivity, "❌ 索引构建失败: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    onRenameFolder = { fid, newName ->
                                        folderViewModel.renameFolder(fid, newName)
                                        Toast.makeText(this@MainActivity, "✅ 文件夹已重命名", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF4F46E5),
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFFEC4899),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDB2777),
    onSecondaryContainer = androidx.compose.ui.graphics.Color.White,
    tertiary = androidx.compose.ui.graphics.Color(0xFF14B8A6),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF0D9488),
    onTertiaryContainer = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFF808080),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    surface = androidx.compose.ui.graphics.Color(0xFF808080),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFB5B5B5),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF444444),
    error = androidx.compose.ui.graphics.Color(0xFFEF4444),
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFDC2626),
    onErrorContainer = androidx.compose.ui.graphics.Color.White,
    outline = androidx.compose.ui.graphics.Color(0xFF555555),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF3A3A3A),
    scrim = androidx.compose.ui.graphics.Color.Black,
    inverseSurface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFFA5B4FC),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFFC4C4C4),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFFD4D4D4),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFDEDEDE),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFD4D4D4),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFCCCCCC),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFB5B5B5),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFA0A0A0)
)

private fun ComponentActivity.copyUriToFile(uri: Uri, folderId: String, fileName: String): File? {
    return try {
        val destDir = File(filesDir, "knowledge/folders/$folderId/documents")
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        android.util.Log.d("MainActivity", "文件复制成功: ${destFile.absolutePath}")
        destFile
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "文件复制失败", e)
        null
    }
}
