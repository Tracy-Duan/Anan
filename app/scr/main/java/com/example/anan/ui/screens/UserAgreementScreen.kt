package com.example.anan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UserAgreementScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF808080))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            // 顶部紫色装饰区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(Color(0xFFB388FF)),
                contentAlignment = Alignment.Center
            ) {
                // 用户协议内容卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFE8E8E8))
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "素未谋面的朋友你好呀，",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )
                        Text(
                            text = "又或者是已然谋面的朋友，",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )
                        Text(
                            text = "我是Tracy Duan。",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "感谢你的下载和使用，这是我的第一个vibecoding作品，也是第一个能够称得上产品的'作业'。\n当然，这作业不是为谁而写的，只是我个人在AI时代的一次尝试罢了。",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "所有数据和程序完全运行在本地，无联网功能，保证绝对的数据安全和隐私，这点你可以放一百个心。",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "这个App的核心是Qwen-3.5-2B模型，在这里要感谢阿里的开源以及Hugging Face社区的量化。\n模型推理引擎是llama.cpp，感谢保加利亚工程师Georgi Gerganov的开源，它的Github是@ggerganov。\n向量数据库构建源于BAAl的bge-small-zh-v1.5模型，同样感谢开源。",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "项目源码开源在：https://github.com/Tracy-Duan/Anan。欢迎交流",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "最后再次感谢大家，谢谢你们的打开\nTracy Duan\n2026年5月19日",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF333333),
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }
    }
}
