#include <jni.h>
#include <string>
#include <vector>
#include <cstdlib>
#include <android/log.h>
#include <unistd.h>
#include <chrono>

#define LOG_TAG "NativeLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include "common.h"
#include "sampling.h"
#include "chat.h"
#include "llama.h"

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_batch g_batch;
static common_sampler * g_sampler = nullptr;
static common_chat_templates_ptr g_chat_templates;

static std::string g_partial_response;
static volatile bool g_generating = false;

static std::vector<common_chat_msg> g_chat_msgs;
static int g_current_pos = 0;
static int g_stop_pos = 0;

constexpr int N_THREADS_MIN = 2;
constexpr int N_THREADS_MAX = 4;
constexpr int N_THREADS_HEADROOM = 2;
constexpr int DEFAULT_CONTEXT_SIZE = 8192;
constexpr int BATCH_SIZE = 2048;

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeInit(JNIEnv *env, jobject, jstring nativeLibDir) {
    const char* dir = env->GetStringUTFChars(nativeLibDir, nullptr);
    ggml_backend_load_all_from_path(dir);
    env->ReleaseStringUTFChars(nativeLibDir, dir);
    llama_backend_init();
    LOGI("Backends loaded");
}

JNIEXPORT jboolean JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeLoadModel(
        JNIEnv* env, jobject, jstring modelPath, jint, jfloat temperature, jfloat topP) {
    if (g_model) return JNI_TRUE;

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    LOGI("Loading: %s", path);

    llama_model_params mp = llama_model_default_params();
    mp.use_mmap = true;
    g_model = llama_model_load_from_file(path, mp);
    if (!g_model) { LOGE("Load failed"); env->ReleaseStringUTFChars(modelPath, path); return JNI_FALSE; }

    int nt = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
        (int)sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));
    LOGI("Threads: %d", nt);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = DEFAULT_CONTEXT_SIZE;
    cp.n_batch = BATCH_SIZE; cp.n_ubatch = BATCH_SIZE;
    cp.n_threads = nt; cp.n_threads_batch = nt;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { llama_model_free(g_model); g_model=nullptr; env->ReleaseStringUTFChars(modelPath, path); return JNI_FALSE; }

    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");

    common_params_sampling sparams;
    sparams.temp = temperature;
    sparams.top_p = topP;
    sparams.top_k = 40;
    g_sampler = common_sampler_init(g_model, sparams);

    g_current_pos = 0;
    g_chat_msgs.clear();

    LOGI("Ready ctx=%d th=%d", cp.n_ctx, nt);
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

static void reset_chat() {
    g_chat_msgs.clear();
    g_current_pos = 0;
    g_stop_pos = 0;
    llama_memory_clear(llama_get_memory(g_ctx), false);
}

static std::string format_and_add_msg(const std::string & role, const std::string & content) {
    common_chat_msg msg;
    msg.role = role;
    msg.content = content;
    auto formatted = common_chat_format_single(
        g_chat_templates.get(), g_chat_msgs, msg, role == "user", false);
    g_chat_msgs.push_back(msg);
    return formatted;
}

static int decode_tokens(const llama_tokens & tokens, int start_pos, bool last_logit = false) {
    for (int i = 0; i < (int)tokens.size(); i += BATCH_SIZE) {
        int cur = std::min((int)tokens.size() - i, BATCH_SIZE);
        common_batch_clear(g_batch);
        for (int j = 0; j < cur; j++) {
            common_batch_add(g_batch, tokens[i+j], start_pos+i+j, {0},
                           last_logit && (i+j == tokens.size()-1));
        }
        if (llama_decode(g_ctx, g_batch)) { LOGE("Decode fail"); return 1; }
    }
    return 0;
}

static std::string sanitize_utf8(const std::string & s) {
    std::string out;
    out.reserve(s.size());
    int i = 0;
    while (i < (int)s.size()) {
        unsigned char c = s[i];
        int len = 1;
        if      (c < 0x80)       len = 1;
        else if ((c & 0xE0) == 0xC0) len = 2;
        else if ((c & 0xF0) == 0xE0) len = 3;
        else if ((c & 0xF8) == 0xF0) len = 4;
        else { i++; continue; }

        if (i + len > (int)s.size()) break;

        bool valid = true;
        for (int j = 1; j < len; j++) {
            if ((s[i+j] & 0xC0) != 0x80) { valid = false; break; }
        }
        if (valid) {
            out.append(s, i, len);
        }
        i += len;
    }
    return out;
}

static std::string extract_answer(const std::string & raw) {
    // 尝试移除可能的思考标签（如果模型使用）
    auto end_think = raw.find("</think>");
    if (end_think != std::string::npos) {
        size_t start = end_think + 9;
        if (start < raw.size()) return raw.substr(start);
        return "";
    }
    auto begin_think = raw.find("<think>");
    if (begin_think != std::string::npos) {
        return raw.substr(0, begin_think);
    }
    
    // 如果没有思考标签，直接返回原始内容
    return raw;
}

JNIEXPORT jstring JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeGenerateResponse(
        JNIEnv* env, jobject, jstring prompt, jstring systemPrompt, jfloat, jfloat, jint maxTokens) {
    if (!g_model || !g_ctx) return env->NewStringUTF("ERR");

    const char* user = env->GetStringUTFChars(prompt, nullptr);
    const char* sys = env->GetStringUTFChars(systemPrompt, nullptr);
    if (!user) return env->NewStringUTF("ERR");

    auto t0 = std::chrono::high_resolution_clock::now();

    g_current_pos = 0;
    g_stop_pos = 0;
    llama_memory_clear(llama_get_memory(g_ctx), false);

    // --- 滑动窗口逻辑：管理聊天历史 ---
    static bool system_added = false;
    common_chat_msg sys_msg;
    sys_msg.role = "system";
    sys_msg.content = (sys && strlen(sys) > 0) ? sys : "你是安安。你清纯阳光、充满活力，洋溢着青春气息。说话风格自然随和，偶尔带点俏皮但不过分，整体舒展大方略显可爱。你喜欢帮助别人，回答要条理清晰又亲切，知识面很广。\n\n重要规则：\n1. 必须用中文回答\n2. 保持友好、活泼的语气\n3. 回答要准确、有条理\n4. 不要编造事实\n5. 如果不知道答案，诚实地说不知道";
    
    // 1. 确保系统提示词始终在最前面
    if (!system_added || g_chat_msgs.empty() || g_chat_msgs[0].role != "system") {
        if (!system_added) {
            g_chat_msgs.insert(g_chat_msgs.begin(), sys_msg);
            system_added = true;
        } else if (g_chat_msgs[0].role != "system") {
            g_chat_msgs.insert(g_chat_msgs.begin(), sys_msg);
        }
    }

    // 2. 添加当前用户消息
    common_chat_msg user_msg;
    user_msg.role = "user";
    user_msg.content = user;
    g_chat_msgs.push_back(user_msg);

    // 3. 检查并裁剪过旧的历史记录 (保留 System + 最近的 N 轮对话)
    // 我们预留 1024 tokens 给生成的回答，剩下的给历史
    int max_history_tokens = DEFAULT_CONTEXT_SIZE - 1024; 
    
    // 简单估算：平均每个汉字/单词约 1-2 个 token。为了安全，我们按字符数粗略控制。
    // 如果历史记录太长，从第二条（跳过 system）开始删除
    while (g_chat_msgs.size() > 2) { // 至少保留 system 和当前 user
        size_t estimated_tokens = 0;
        for (size_t i = 1; i < g_chat_msgs.size(); ++i) { // 从 index 1 开始算，index 0 是 system
            estimated_tokens += g_chat_msgs[i].content.size() / 2; 
        }
        if (estimated_tokens < max_history_tokens) break;
        
        // 删除最旧的一轮对话（user + assistant）
        // 注意：g_chat_msgs[0] 是 system，所以从 index 1 开始删
        if (g_chat_msgs.size() > 2) {
            g_chat_msgs.erase(g_chat_msgs.begin() + 1);
            if (g_chat_msgs.size() > 2 && g_chat_msgs[1].role == "assistant") {
                 g_chat_msgs.erase(g_chat_msgs.begin() + 1);
            }
        } else {
            break;
        }
    }
    // --------------------------------------

    // 使用 llama.cpp 内置的聊天模板功能
    std::string formatted;
    formatted.reserve(DEFAULT_CONTEXT_SIZE);
    
    // 构建聊天模板输入
    common_chat_templates_inputs inputs;
    inputs.messages = g_chat_msgs;
    inputs.add_generation_prompt = true;
    inputs.use_jinja = true;
    inputs.enable_thinking = false;
    
    // 应用聊天模板
    auto chat_params = common_chat_templates_apply(g_chat_templates.get(), inputs);
    formatted = chat_params.prompt;

    LOGI("Prompt (%zu chars, %zu msgs): %.300s...", formatted.size(), g_chat_msgs.size(), formatted.c_str());

    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(512);
    int nt = llama_tokenize(vocab, formatted.c_str(), formatted.size(),
                            tokens.data(), tokens.size(), true, true);
    if (nt < 0) { tokens.resize(-nt); nt = llama_tokenize(vocab, formatted.c_str(), formatted.size(), tokens.data(), tokens.size(), true, true); }
    tokens.resize(nt > 0 ? nt : 0);

    int max_len = maxTokens > 0 ? maxTokens : 1024;
    g_stop_pos = (int)tokens.size() + max_len;

    if ((int)tokens.size() >= DEFAULT_CONTEXT_SIZE - 4) {
        LOGE("Context overflow: %d tokens, max %d", (int)tokens.size(), DEFAULT_CONTEXT_SIZE);
        env->ReleaseStringUTFChars(prompt, user);
        if (sys) env->ReleaseStringUTFChars(systemPrompt, sys);
        g_chat_msgs.pop_back();
        return env->NewStringUTF("(对话历史过长，请清除上下文后重试)");
    }

    auto t1 = std::chrono::high_resolution_clock::now();
    if (decode_tokens(tokens, 0, true)) {
        env->ReleaseStringUTFChars(prompt, user);
        if (sys) env->ReleaseStringUTFChars(systemPrompt, sys);
        g_chat_msgs.pop_back();
        return env->NewStringUTF("ERR:decode");
    }
    g_current_pos = (int)tokens.size();

    auto dms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::high_resolution_clock::now()-t1).count();
    LOGI("Prompt decode: %lldms (%d tok)", (long long)dms, g_current_pos);

    std::string full_output; full_output.reserve(8192);
    g_partial_response.clear();
    g_generating = true;

    int n = 0;
    while (n < max_len) {
        if (g_current_pos >= DEFAULT_CONTEXT_SIZE - 4) break;
        if (g_current_pos >= g_stop_pos) break;
        if (!g_generating) break;  // 检查是否被用户中断

        auto tok = common_sampler_sample(g_sampler, g_ctx, -1);
        common_sampler_accept(g_sampler, tok, true);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char piece[256];
        int np = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, false);
        if (np > 0) {
            full_output.append(piece, np);
            g_partial_response = sanitize_utf8(full_output);
        }

        common_batch_clear(g_batch);
        common_batch_add(g_batch, tok, g_current_pos, {0}, true);
        if (llama_decode(g_ctx, g_batch)) { LOGE("Decode@%d", n); break; }
        g_current_pos++; n++;
    }
    g_generating = false;

    std::string answer = extract_answer(full_output);

    if (!answer.empty()) {
        common_chat_msg assistant_msg;
        assistant_msg.role = "assistant";
        assistant_msg.content = answer;
        g_chat_msgs.push_back(assistant_msg);
    }

    auto total = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::high_resolution_clock::now()-t0).count();
    LOGI("Done: %d tok/%lld ms (%.1f t/s) history=%zu", n, (long long)total, n>0?n*1000.0/total:0, g_chat_msgs.size());
    LOGI("Answer(%zu): %s", answer.size(), answer.empty() ? "(empty)" : answer.c_str());

    env->ReleaseStringUTFChars(prompt, user);
    if (sys) env->ReleaseStringUTFChars(systemPrompt, sys);
    return env->NewStringUTF(sanitize_utf8(answer.empty() ? "(未生成有效回答)" : answer).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeGetPartialResponse(JNIEnv* env, jobject) {
    return env->NewStringUTF(g_generating ? sanitize_utf8(g_partial_response).c_str() : "");
}

JNIEXPORT jboolean JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeIsThinking(JNIEnv*, jobject) { return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeIsModelLoaded(JNIEnv*, jobject) {
    return g_model ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeRelease(JNIEnv*, jobject) {
    if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
    g_chat_templates.reset();
    if (g_batch.n_tokens >= 0) llama_batch_free(g_batch);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
}

JNIEXPORT void JNICALL
Java_com_example_anan_model_NativeLLMModel_nativeStopGeneration(JNIEnv*, jobject) {
    g_generating = false;
    LOGI("Generation stopped by user");
}

JNIEXPORT jstring JNICALL
Java_com_example_anan_MainActivity_stringFromJNI(JNIEnv* env, jobject) {
    return env->NewStringUTF("llama.cpp OK");
}

}