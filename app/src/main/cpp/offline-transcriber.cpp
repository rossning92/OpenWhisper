#include <jni.h>
#include <asm/hwcap.h>
#include <sys/auxv.h>
#include <algorithm>
#include <memory>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>
#include "transcribe.h"

static void check(transcribe_status status) {
    if (status != TRANSCRIBE_OK) throw std::runtime_error(transcribe_status_string(status));
}

static void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ross_speechtotext_OfflineEngine_supported(JNIEnv *, jclass) {
    const auto features = getauxval(AT_HWCAP);
    return (features & HWCAP_ASIMDHP) && (features & HWCAP_ASIMDDP);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ross_speechtotext_OfflineEngine_load(JNIEnv *env, jclass, jstring path) {
    try {
        if (!Java_com_ross_speechtotext_OfflineEngine_supported(env, nullptr)) {
            throw std::runtime_error("Offline English requires a CPU with FP16 and dot-product support");
        }
        const char *chars = env->GetStringUTFChars(path, nullptr);
        if (!chars) return 0;
        std::string filename;
        try { filename = chars; }
        catch (...) { env->ReleaseStringUTFChars(path, chars); throw; }
        env->ReleaseStringUTFChars(path, chars);
        transcribe_model_load_params params;
        transcribe_model_load_params_init(&params);
        params.backend = TRANSCRIBE_BACKEND_CPU;
        transcribe_model *model = nullptr;
        check(transcribe_model_load_file(filename.c_str(), &params, &model));
        return reinterpret_cast<jlong>(model);
    } catch (const std::exception &error) { fail(env, error.what()); }
      catch (...) { fail(env, "Could not load offline model"); }
    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ross_speechtotext_OfflineEngine_run(JNIEnv *env, jclass, jlong handle, jfloatArray audio,
                                           jbyteArray vocabulary) {
    try {
        if (!handle || !audio) throw std::runtime_error("No model or audio");
        const int count = env->GetArrayLength(audio);
        if (count <= 0) throw std::runtime_error("No audio recorded");
        std::vector<float> pcm(count);
        env->GetFloatArrayRegion(audio, 0, count, pcm.data());
        if (env->ExceptionCheck()) return nullptr;
        transcribe_session_params params;
        transcribe_session_params_init(&params);
        params.n_threads = 4;
        transcribe_session *raw = nullptr;
        check(transcribe_session_init(reinterpret_cast<transcribe_model *>(handle), &params, &raw));
        std::unique_ptr<transcribe_session, decltype(&transcribe_session_free)> session(raw, transcribe_session_free);
        std::vector<std::string> terms;
        if (vocabulary) {
            const jsize size = env->GetArrayLength(vocabulary);
            std::string text(size, '\0');
            if (size) env->GetByteArrayRegion(vocabulary, 0, size, reinterpret_cast<jbyte *>(text.data()));
            if (env->ExceptionCheck()) return nullptr;
            std::istringstream lines(text);
            std::string line;
            while (std::getline(lines, line)) {
                const auto first = line.find_first_not_of(" \t\r");
                if (first == std::string::npos) continue;
                line = line.substr(first, line.find_last_not_of(" \t\r") - first + 1);
                if (std::find(terms.begin(), terms.end(), line) == terms.end()) terms.push_back(line);
            }
        }
        std::vector<const char *> words;
        for (const auto &term : terms) words.push_back(term.c_str());
        transcribe_run_params run_params;
        transcribe_run_params_init(&run_params);
        run_params.vocabulary = words.empty() ? nullptr : words.data();
        run_params.n_vocabulary = static_cast<int32_t>(words.size());
        check(transcribe_run(session.get(), pcm.data(), count, &run_params));
        // Java decodes UTF-8; NewStringUTF expects modified UTF-8.
        const std::string text(transcribe_full_text(session.get()));
        auto result = env->NewByteArray(static_cast<jsize>(text.size()));
        if (result) env->SetByteArrayRegion(result, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
        return result;
    } catch (const std::exception &error) { fail(env, error.what()); }
      catch (...) { fail(env, "Offline transcription failed"); }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ross_speechtotext_OfflineEngine_unload(JNIEnv *, jclass, jlong handle) {
    transcribe_model_free(reinterpret_cast<transcribe_model *>(handle));
}
