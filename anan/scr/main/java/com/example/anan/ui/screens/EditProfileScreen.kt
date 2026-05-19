package com.example.anan.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import java.io.File

@Composable
fun EditProfileScreen(
    onSave: (String, String?) -> Unit,
    onPickAvatarClick: () -> Unit,
    currentAvatarUri: String? = null,
    currentUserName: String = "用户"
) {
    var userName by remember { mutableStateOf(currentUserName) }
    var avatarUri by remember { mutableStateOf(currentAvatarUri) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF808080))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上框
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(330.dp)
                    .clip(RoundedCornerShape(65.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(65.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 头像和修改头像按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 头像
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF5F5F5))
                                .border(2.dp, Color(0xFFDDDDDD), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!avatarUri.isNullOrEmpty()) {
                                val imageFile = File(avatarUri)
                                if (imageFile.exists()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(imageFile),
                                        contentDescription = "头像",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(text = "😺", fontSize = 50.sp)
                                }
                            } else {
                                Text(text = "😺", fontSize = 50.sp)
                            }
                        }

                        // 修改头像按钮
                        Button(
                            onClick = onPickAvatarClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8C4FF)),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "修改头像",
                                fontSize = 16.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 昵称输入框
                    TextField(
                        value = userName,
                        onValueChange = { userName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(28.dp)),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 20.sp,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "您的昵称",
                                fontSize = 20.sp,
                                color = Color(0xFF999999),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 提示文字
                    Text(
                        text = "由于我们是纯离线运行，所以实在",
                        fontSize = 11.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "没有什么好编辑的",
                        fontSize = 11.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            // 保存按钮
            Button(
                onClick = {
                    onSave(userName, avatarUri)
                },
                modifier = Modifier
                    .width(214.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8C4FF)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "保存",
                    fontSize = 18.sp,
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
