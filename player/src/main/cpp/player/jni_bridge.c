#include "player_core.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdlib.h>

#define LOG_TAG "vicuplayer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM *g_vm = NULL;
static jclass g_player_class = NULL; // NativePlayerImpl 全局引用
static jmethodID g_notify_prepared;
static jmethodID g_notify_position;
static jmethodID g_notify_ended;
static jmethodID g_notify_error;
static jmethodID g_notify_audio_data;
static jmethodID g_audio_clock;
static jfieldID g_handle_fid;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    g_vm = vm;
    return JNI_VERSION_1_6;
}

static bool cache_method_ids(JNIEnv *env, jobject thiz) {
    if (g_notify_prepared) return true;
    jclass local = (*env)->GetObjectClass(env, thiz);
    g_player_class = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    if (!g_player_class) return false;
    g_notify_prepared = (*env)->GetMethodID(env, g_player_class, "notifyPrepared", "(IIJZ)V");
    g_notify_position = (*env)->GetMethodID(env, g_player_class, "notifyPosition", "(J)V");
    g_notify_ended = (*env)->GetMethodID(env, g_player_class, "notifyEnded", "(I)V");
    g_notify_error = (*env)->GetMethodID(env, g_player_class, "notifyError", "(ILjava/lang/String;)V");
    g_notify_audio_data = (*env)->GetMethodID(env, g_player_class, "notifyAudioData", "([BJI)V");
    g_audio_clock = (*env)->GetMethodID(env, g_player_class, "audioClockUs", "()J");
    g_handle_fid = (*env)->GetFieldID(env, g_player_class, "nativeHandle", "J");
    return g_notify_prepared && g_notify_position && g_notify_ended && g_notify_error &&
           g_notify_audio_data && g_audio_clock && g_handle_fid;
}

/** 取得当前线程 JNIEnv；必要时 attach（native 工作线程首次回调）。 */
static JNIEnv *get_env(bool *attached) {
    *attached = false;
    JNIEnv *env = NULL;
    if ((*g_vm)->GetEnv(g_vm, &env, JNI_VERSION_1_6) == JNI_OK) return env;
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) == 0) {
        *attached = true;
        return env;
    }
    return NULL;
}

// ---------------- 引擎回调（native 工作线程 → Kotlin） ----------------

static void jni_on_prepared(void *user, int width, int height, int64_t duration_ms, bool has_audio) {
    bool attached = false;
    JNIEnv *env = get_env(&attached);
    if (!env) return;
    (*env)->CallVoidMethod(env, user, g_notify_prepared, (jint) width, (jint) height,
                           (jlong) duration_ms, (jboolean) has_audio);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
}

static void jni_on_position(void *user, int64_t position_ms) {
    bool attached = false;
    JNIEnv *env = get_env(&attached);
    if (!env) return;
    (*env)->CallVoidMethod(env, user, g_notify_position, (jlong) position_ms);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
}

static void jni_on_ended(void *user, unsigned epoch) {
    bool attached = false;
    JNIEnv *env = get_env(&attached);
    if (!env) return;
    (*env)->CallVoidMethod(env, user, g_notify_ended, (jint) epoch);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
}

static void jni_on_error(void *user, int code, const char *message) {
    bool attached = false;
    JNIEnv *env = get_env(&attached);
    if (!env) return;
    jstring jmessage = message ? (*env)->NewStringUTF(env, message) : NULL;
    (*env)->CallVoidMethod(env, user, g_notify_error, (jint) code, jmessage);
    if (jmessage) (*env)->DeleteLocalRef(env, jmessage);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
}

static void jni_on_destroy(void *user) {
    JNIEnv *env = NULL;
    // destroy 由持有 nativeHandle 的 JNI 线程调用（已 attach）
    if ((*g_vm)->GetEnv(g_vm, &env, JNI_VERSION_1_6) == JNI_OK) {
        (*env)->DeleteGlobalRef(env, user);
    }
}

static void jni_on_audio_data(void *user, const uint8_t *data, int size, int64_t pts_us, unsigned epoch) {
    bool attached = false;
    JNIEnv *env = get_env(&attached);
    if (!env) return;
    jbyteArray array = (*env)->NewByteArray(env, size);
    if (array) {
        (*env)->SetByteArrayRegion(env, array, 0, size, (const jbyte *) data);
        (*env)->CallVoidMethod(env, user, g_notify_audio_data, array, (jlong) pts_us, (jint) epoch);
        (*env)->DeleteLocalRef(env, array);
    }
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
}

static int64_t jni_get_audio_clock_us(void *user) {
    JNIEnv *env = NULL;
    // 渲染线程（native）未 attach，按需临时 attach
    int rc = (*g_vm)->GetEnv(g_vm, &env, JNI_VERSION_1_6);
    bool attached = false;
    if (rc == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) == 0) attached = true;
        else return -1;
    } else if (rc != JNI_OK) {
        return -1;
    }
    int64_t result = (int64_t) (*env)->CallLongMethod(env, user, g_audio_clock);
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
    return result;
}

// ---------------- JNI 导出 ----------------

static PlayerContext *get_ctx(JNIEnv *env, jobject thiz) {
    jlong handle = (*env)->GetLongField(env, thiz, g_handle_fid);
    return (PlayerContext *) (intptr_t) handle;
}

JNIEXPORT jint JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativePrepare(JNIEnv *env, jobject thiz, jint fd) {
    if (!cache_method_ids(env, thiz)) return -1;

    // 已有会话先释放（重复 prepare）
    PlayerContext *old = get_ctx(env, thiz);
    if (old) {
        player_destroy(old);
        free(old);
        (*env)->SetLongField(env, thiz, g_handle_fid, 0);
    }

    PlayerCallbacks callbacks = {
            .on_prepared = jni_on_prepared,
            .on_position = jni_on_position,
            .on_ended = jni_on_ended,
            .on_error = jni_on_error,
            .on_destroy = jni_on_destroy,
            .on_audio_data = jni_on_audio_data,
            .get_audio_clock_us = jni_get_audio_clock_us,
    };
    jobject user = (*env)->NewGlobalRef(env, thiz);
    if (!user) return -1;

    PlayerContext *ctx = player_create(&callbacks, user);
    if (!ctx) {
        (*env)->DeleteGlobalRef(env, user);
        return -1;
    }
    int rc = player_prepare(ctx, fd);
    if (rc < 0) {
        LOGE("prepare failed: rc=%d (%s)", rc, player_state_name(PLAYER_ERROR));
        player_destroy(ctx);
        free(ctx);
        return rc;
    }
    (*env)->SetLongField(env, thiz, g_handle_fid, (jlong) (intptr_t) ctx);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativeSetSurface(JNIEnv *env, jobject thiz, jobject surface) {
    PlayerContext *ctx = get_ctx(env, thiz);
    if (!ctx) return;
    ANativeWindow *window = NULL;
    if (surface != NULL) {
        window = ANativeWindow_fromSurface(env, surface); // 返回已 acquire 的引用
    }
    player_set_window(ctx, window); // 接管引用；NULL 解除绑定
}

JNIEXPORT void JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativeStart(JNIEnv *env, jobject thiz) {
    PlayerContext *ctx = get_ctx(env, thiz);
    if (ctx) player_start(ctx);
}

JNIEXPORT void JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativePause(JNIEnv *env, jobject thiz) {
    PlayerContext *ctx = get_ctx(env, thiz);
    if (ctx) player_pause(ctx);
}

JNIEXPORT void JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativeSeek(JNIEnv *env, jobject thiz, jlong positionMs) {
    PlayerContext *ctx = get_ctx(env, thiz);
    if (ctx) player_seek(ctx, (int64_t) positionMs);
}

JNIEXPORT void JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativeSetFilterGraph(JNIEnv *env, jobject thiz, jstring chain) {
    PlayerContext *ctx = get_ctx(env, thiz);
    if (!ctx) return;
    const char *utf = chain ? (*env)->GetStringUTFChars(env, chain, NULL) : NULL;
    player_set_filter_graph(ctx, utf);
    if (utf) (*env)->ReleaseStringUTFChars(env, chain, utf);
}

JNIEXPORT void JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativeSetPlayRange(
        JNIEnv *env, jobject thiz, jlong startMs, jlong endMs) {
    PlayerContext *ctx = get_ctx(env, thiz);
    if (ctx) player_set_play_range(ctx, (int64_t) startMs, (int64_t) endMs);
}

JNIEXPORT void JNICALL
Java_com_rockbyte_vicu_player_NativePlayerImpl_nativeRelease(JNIEnv *env, jobject thiz) {
    PlayerContext *ctx = get_ctx(env, thiz);
    if (!ctx) return;
    (*env)->SetLongField(env, thiz, g_handle_fid, 0);
    player_destroy(ctx);
    free(ctx);
}
