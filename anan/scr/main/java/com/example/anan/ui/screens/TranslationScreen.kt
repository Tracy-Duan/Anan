package com.example.anan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anan.viewmodel.TranslationViewModel
import kotlinx.coroutines.launch

@Composable
fun TranslationScreen() {
    val context = LocalContext.current
    
    val viewModel: TranslationViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TranslationViewModel(context) as T
            }
        }
    )
    
    val isTranslating by viewModel.isTranslating.collectAsState()
    val translatedText by viewModel.translatedText.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var sourceText by remember { mutableStateOf("") }
    var sourceLanguage by remember { mutableStateOf("中文") }
    var targetLanguage by remember { mutableStateOf("English") }
    
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF808080))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 输入和输出区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 输入框
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        TextField(
                            value = sourceText,
                            onValueChange = { sourceText = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "请输入要翻译的文本",
                                    color = Color(0xFF999999)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFF7B68EE)
                            )
                        )
                    }
                }
                
                // 翻译按钮（输入框下方）
                Button(
                    onClick = {
                        if (sourceText.isNotBlank()) {
                            scope.launch {
                                viewModel.translate(
                                    text = sourceText,
                                    sourceLang = sourceLanguage,
                                    targetLang = targetLanguage
                                )
                            }
                        }
                    },
                    enabled = sourceText.isNotBlank() && !isTranslating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7B68EE)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "翻译中...",
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "$sourceLanguage → $targetLanguage",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
                
                // 切换语言按钮
                Button(
                    onClick = {
                        val temp = sourceLanguage
                        sourceLanguage = targetLanguage
                        targetLanguage = temp
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE8E6FF)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = "⇄ 切换语言",
                        fontSize = 14.sp,
                        color = Color(0xFF7B68EE)
                    )
                }

                // 输出框
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 反向翻译按钮（输出框上方）
                        Button(
                            onClick = {
                                if (translatedText.isNotBlank()) {
                                    scope.launch {
                                        viewModel.translate(
                                            text = translatedText,
                                            sourceLang = targetLanguage,
                                            targetLang = sourceLanguage
                                        )
                                    }
                                }
                            },
                            enabled = translatedText.isNotBlank() && !isTranslating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7B68EE)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "翻译中...",
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "$targetLanguage → $sourceLanguage",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isTranslating) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF7B68EE)
                                )
                            }
                        } else if (error != null) {
                            Text(
                                text = "翻译失败：${error}",
                                color = Color(0xFFEF4444),
                                fontSize = 14.sp
                            )
                        } else if (translatedText.isEmpty()) {
                            Text(
                                text = "翻译结果将显示在这里",
                                color = Color(0xFF999999),
                                fontSize = 16.sp
                            )
                        } else {
                            Text(
                                text = translatedText,
                                fontSize = 16.sp,
                                color = Color(0xFF333333)
                            )
                        }
                    }
                }
            }
        }
    }
}
