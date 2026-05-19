# 安安 (Anan)

> 一个完全离线运行的 AI 助手 Android 应用

<div align="center">

**隐私优先 · 完全离线 · 智能对话 · 知识检索**

</div>

---

## 应用简介

安安是一个运行在本地的 AI 助手应用，基于 Qwen 3.5-2B 大语言模型。所有数据和模型都运行在您的设备上，**无需联网**，保证绝对的数据安全和隐私。

###  核心特点

-  **完全离线** - 无联网功能，所有数据本地处理
-  **智能对话** - 基于 Qwen 3.5-2B 模型，支持多轮上下文理解
-  **知识检索** - 支持 Word 文档上传，基于向量检索增强 (RAG)
-  **现代 UI** - 基于 Jetpack Compose 构建的流畅界面
-  **可控生成** - 支持中断 AI 回答，灵活控制对话节奏
-  **个性化** - 自定义头像、昵称和 AI 性格

---

##  技术栈

| 类别 | 技术 |
|------|------|
| **开发框架** | Android + Jetpack Compose |
| **开发语言** | Kotlin + C++ |
| **AI 模型** | Qwen 3.5-2B (GGUF 格式) |
| **推理引擎** | llama.cpp |
| **向量检索** | BGE-small-zh-v1.5 + ONNX Runtime |
| **构建工具** | Gradle + CMake |

---

##  功能特性

###  智能对话
- 多轮对话上下文理解
- 支持中断正在生成的回答
- 长按消息可复制文本
- 流畅的打字机效果

###  知识检索
- 创建和管理知识文件夹
- 支持 Word 文档上传
- 基于 BGE 模型的向量检索
- RAG (检索增强生成) 技术

###  个性化设置
- 自定义用户头像和昵称
- AI 助手性格设置
- 离线翻译功能
- 用户协议和致谢

---

##  快速开始

### 环境要求

- **Android Studio**: Hedgehog 或更高版本
- **Android SDK**: 33+
- **JDK**: 17+
- **NDK**: 25+
- **CMake**: 3.22+

### 构建步骤

1. **克隆项目**

2. **下载模型文件**
   
   需要下载以下模型文件并放置到 `app/src/main/assets/models/` 目录：
   
   - **对话模型**: Qwen 3.5-2B GGUF 格式
     - 下载地址：[Hugging Face](https://huggingface.co/) (请替换为实际链接)
     - 文件名：`Qwen3.5-2B-Polaris-HighIQ-INSTRUCT-Q4_K_M.gguf`
   
   - **向量模型**: BGE-small-zh-v1.5 ONNX 格式
     - 下载地址：[Hugging Face](https://huggingface.co/BAAI/bge-small-zh-v1.5)
     - 文件名：`bge-small-zh-v1.5.onnx`

3. **编译运行**
   ```bash
   # 清理构建
   ./gradlew clean
   
   # 编译 Debug 版本
   ./gradlew assembleDebug
   
   # 安装到设备
   ./gradlew installDebug
   ```

### 使用 Android Studio

1. 打开 Android Studio
2. 选择 `File` → `Open` → 选择项目目录
3. 等待 Gradle 同步完成
4. 点击 `Run` 按钮 (▶️) 编译并安装

---

##  项目结构

```
anan/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/anan/
│   │   │   ├── ui/                  # UI 层 (Jetpack Compose)
│   │   │   │   ├── screens/         # 页面组件
│   │   │   │   └── components/      # 通用组件
│   │   │   ├── viewmodel/           # ViewModel 层
│   │   │   ├── repository/          # 数据仓库层
│   │   │   ├── model/               # AI 模型接口
│   │   │   └── utils/               # 工具类
│   │   ├── cpp/                     # C++ 代码
│   │   │   ├── native-lib.cpp       # JNI 接口
│   │   │   └── llama.cpp-master/    # llama.cpp 源码
│   │   ── assets/                  # 资源文件
│   │       └── models/              # AI 模型文件
│   └── build.gradle.kts
├── gradle/                          # Gradle Wrapper
├── build.gradle.kts                 # 项目构建配置
└── README.md                        # 项目说明
```

---

##  开发指南

### 添加新的对话功能

1. 在 `ChatRepository.kt` 中添加业务逻辑
2. 在 `ChatViewModel.kt` 中暴露给 UI 层
3. 在 `ChatScreen.kt` 中添加 UI 组件

### 修改 AI 性格

编辑 `AIProfile.kt` 中的 `SYSTEM_PROMPT`：

```kotlin
const val SYSTEM_PROMPT = """
你是安安。你清纯阳光、充满活力...
"""
```

### 添加新的知识库功能

1. 在 `VectorKnowledgeBase.kt` 中实现向量检索逻辑
2. 使用 `EmbeddingHelper.kt` 生成文本向量
3. 在 `ChatRepository.kt` 中集成检索结果

---

##  致谢

本项目感谢以下优秀的开源项目：

- **[llama.cpp](https://github.com/ggerganov/llama.cpp)** - 高效的 LLM 推理引擎
  - 作者：Georgi Gerganov [@ggerganov](https://github.com/ggerganov)
  
- **[Qwen](https://github.com/QwenLM/Qwen)** - 通义千问大语言模型
  - 出品：阿里云
  
- **[BGE](https://github.com/FlagOpen/FlagEmbedding)** - 向量嵌入模型
  - 出品：北京智源人工智能研究院 (BAAI)
  
- **[Hugging Face](https://huggingface.co/)** - 模型量化和社区支持

---

##  许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。


---

<div align="center">

**感谢使用安安！**

Made with by Tracy Duan

2026 年 5 月

</div>
