#include "player_core.h"
#include "frame_upload.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <stdlib.h>
#include <time.h>

#define LOG_TAG "vicuplayer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// BT.601 limited range YUV → RGB
static const char *VERTEX_SHADER =
        "attribute vec4 aPosition;\n"
        "attribute vec2 aTexCoord;\n"
        "varying vec2 vTexCoord;\n"
        "void main() {\n"
        "  gl_Position = aPosition;\n"
        "  vTexCoord = aTexCoord;\n"
        "}\n";

static const char *FRAGMENT_SHADER =
        "precision mediump float;\n"
        "varying vec2 vTexCoord;\n"
        "uniform sampler2D yTex;\n"
        "uniform sampler2D uTex;\n"
        "uniform sampler2D vTex;\n"
        "void main() {\n"
        "  float y = texture2D(yTex, vTexCoord).r - 0.0625;\n"
        "  float u = texture2D(uTex, vTexCoord).r - 0.5;\n"
        "  float v = texture2D(vTex, vTexCoord).r - 0.5;\n"
        "  gl_FragColor = vec4(\n"
        "      1.164 * y + 1.596 * v,\n"
        "      1.164 * y - 0.391 * u - 0.813 * v,\n"
        "      1.164 * y + 2.018 * u,\n"
        "      1.0);\n"
        "}\n";

typedef struct RendererState {
    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    ANativeWindow *window; // 渲染线程自持引用
    GLuint program;
    GLuint tex_y;
    GLuint tex_u;
    GLuint tex_v;
    GLint a_position;
    GLint a_texcoord;
    FrameUploadBuffer upload;
    bool egl_ready;
} RendererState;

static GLuint compile_shader(GLenum type, const char *source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512] = {0};
        glGetShaderInfoLog(shader, sizeof(log) - 1, NULL, log);
        LOGE("shader compile failed: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static void destroy_textures(RendererState *r) {
    if (r->tex_y) glDeleteTextures(1, &r->tex_y);
    if (r->tex_u) glDeleteTextures(1, &r->tex_u);
    if (r->tex_v) glDeleteTextures(1, &r->tex_v);
    r->tex_y = r->tex_u = r->tex_v = 0;
}

static bool renderer_init(RendererState *r, ANativeWindow *window) {
    r->window = window;
    r->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (r->display == EGL_NO_DISPLAY) return false;
    if (!eglInitialize(r->display, NULL, NULL)) return false;

    const EGLint config_attribs[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
            EGL_NONE,
    };
    EGLConfig config;
    EGLint num_configs = 0;
    if (!eglChooseConfig(r->display, config_attribs, &config, 1, &num_configs) || num_configs < 1) {
        return false;
    }
    r->surface = eglCreateWindowSurface(r->display, config, window, NULL);
    if (r->surface == EGL_NO_SURFACE) return false;
    const EGLint ctx_attribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    r->context = eglCreateContext(r->display, config, EGL_NO_CONTEXT, ctx_attribs);
    if (r->context == EGL_NO_CONTEXT) return false;
    if (!eglMakeCurrent(r->display, r->surface, r->surface, r->context)) return false;
    eglSwapInterval(r->display, 1);

    GLuint vs = compile_shader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vs || !fs) {
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        return false;
    }
    r->program = glCreateProgram();
    glAttachShader(r->program, vs);
    glAttachShader(r->program, fs);
    glLinkProgram(r->program);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint linked = 0;
    glGetProgramiv(r->program, GL_LINK_STATUS, &linked);
    if (!linked) {
        char log[512] = {0};
        glGetProgramInfoLog(r->program, sizeof(log) - 1, NULL, log);
        LOGE("program link failed: %s", log);
        return false;
    }
    glUseProgram(r->program);

    r->a_position = glGetAttribLocation(r->program, "aPosition");
    r->a_texcoord = glGetAttribLocation(r->program, "aTexCoord");
    glUniform1i(glGetUniformLocation(r->program, "yTex"), 0);
    glUniform1i(glGetUniformLocation(r->program, "uTex"), 1);
    glUniform1i(glGetUniformLocation(r->program, "vTex"), 2);

    glGenTextures(1, &r->tex_y);
    glGenTextures(1, &r->tex_u);
    glGenTextures(1, &r->tex_v);
    GLuint textures[3] = {r->tex_y, r->tex_u, r->tex_v};
    for (int i = 0; i < 3; i++) {
        glBindTexture(GL_TEXTURE_2D, textures[i]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    glDisable(GL_DEPTH_TEST);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    r->egl_ready = true;
    return true;
}

static void renderer_destroy(RendererState *r) {
    if (r->display != EGL_NO_DISPLAY) {
        eglMakeCurrent(r->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        destroy_textures(r);
        if (r->program) {
            glDeleteProgram(r->program);
            r->program = 0;
        }
        if (r->surface != EGL_NO_SURFACE) {
            eglDestroySurface(r->display, r->surface);
            r->surface = EGL_NO_SURFACE;
        }
        if (r->context != EGL_NO_CONTEXT) {
            eglDestroyContext(r->display, r->context);
            r->context = EGL_NO_CONTEXT;
        }
        eglTerminate(r->display);
        r->display = EGL_NO_DISPLAY;
    }
    if (r->window) {
        ANativeWindow_release(r->window);
        r->window = NULL;
    }
    frame_upload_release(&r->upload);
    r->egl_ready = false;
}

/** 上传三平面并按保持宽高比的 letterbox 绘制。 */
static int renderer_draw(RendererState *r, AVFrame *frame) {
    if (!r->egl_ready) return 0;
    int win_w = ANativeWindow_getWidth(r->window);
    int win_h = ANativeWindow_getHeight(r->window);
    int rc = frame_upload_prepare(&r->upload, frame);
    if (rc < 0) return rc;
    int chroma_w = (frame->width + 1) / 2;
    int chroma_h = (frame->height + 1) / 2;

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, r->tex_y);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, frame->width, frame->height, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, r->upload.planes[0]);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, r->tex_u);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, chroma_w, chroma_h, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, r->upload.planes[1]);
    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_2D, r->tex_v);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, chroma_w, chroma_h, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, r->upload.planes[2]);

    double aspect = (double) frame->width / frame->height;
    if (frame->sample_aspect_ratio.num && frame->sample_aspect_ratio.den) {
        aspect *= av_q2d(frame->sample_aspect_ratio);
    }
    int draw_w, draw_h;
    if ((double) win_w / win_h > aspect) {
        draw_h = win_h;
        draw_w = (int) (win_h * aspect);
    } else {
        draw_w = win_w;
        draw_h = (int) ((double) win_w / aspect);
    }
    glViewport((win_w - draw_w) / 2, (win_h - draw_h) / 2, draw_w, draw_h);

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // 图像第 0 行在顶部：屏幕底部（y=-1）对应纹理 v=1
    static const GLfloat positions[] = {
            -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f,
    };
    static const GLfloat texcoords[] = {
            0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
    };
    glVertexAttribPointer(r->a_position, 2, GL_FLOAT, GL_FALSE, 0, positions);
    glVertexAttribPointer(r->a_texcoord, 2, GL_FLOAT, GL_FALSE, 0, texcoords);
    glEnableVertexAttribArray(r->a_position);
    glEnableVertexAttribArray(r->a_texcoord);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    return eglSwapBuffers(r->display, r->surface) == EGL_TRUE;
}

static void sleep_us(int64_t us) {
    struct timespec ts = {
            .tv_sec = (time_t) (us / 1000000),
            .tv_nsec = (long) (us % 1000000) * 1000,
    };
    nanosleep(&ts, NULL);
}

/** 阻塞等待渲染目标窗口；返回自持引用（调用方 release），abort 时返回 NULL。 */
static ANativeWindow *acquire_window(PlayerContext *ctx) {
    pthread_mutex_lock(&ctx->surface_mu);
    while (ctx->window == NULL && !atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) {
        pthread_cond_wait(&ctx->surface_cond, &ctx->surface_mu);
    }
    ANativeWindow *window = NULL;
    if (!atomic_load_explicit(&ctx->abort_request, memory_order_relaxed) && ctx->window) {
        window = ctx->window;
        ANativeWindow_acquire(window);
    }
    pthread_mutex_unlock(&ctx->surface_mu);
    return window;
}

/** 主时钟：有音频时钟（媒体时间）时用之，否则回退墙钟。 */
static int64_t master_clock_us(PlayerContext *ctx) {
    if (ctx->has_audio && ctx->callbacks.get_audio_clock_us) {
        int64_t audio = ctx->callbacks.get_audio_clock_us(ctx->user);
        if (audio >= 0) return audio;
    }
    return clock_playback_us(&ctx->clock);
}

/** 结束事件也携带代际，避免跨 JNI 的旧回调暂停新的 seek。锁内不调用外部回调。 */
static void report_ended(PlayerContext *ctx, unsigned epoch) {
    pthread_mutex_lock(&ctx->seek_mu);
    bool current = epoch == atomic_load(&ctx->media_epoch) && atomic_load(&ctx->state) != PLAYER_ENDED;
    if (current) atomic_store(&ctx->state, PLAYER_ENDED);
    pthread_mutex_unlock(&ctx->seek_mu);
    if (current && ctx->callbacks.on_ended) ctx->callbacks.on_ended(ctx->user, epoch);
}

/** 渲染线程：等待帧 → 按主时钟对时 → EGL 上屏；音频为主时钟，无音频时以墙钟近似。 */
void *render_thread_func(void *arg) {
    PlayerContext *ctx = arg;
    RendererState rs;
    memset(&rs, 0, sizeof(rs));
    bool egl_failed_reported = false;
    unsigned report_epoch = (unsigned)-1;

    while (!atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) {
        ANativeWindow *target = acquire_window(ctx);
        if (!target) break;

        if (rs.window == target) {
            ANativeWindow_release(target); // 已绑定同一窗口，释放临时引用
        } else {
            renderer_destroy(&rs);
            if (!renderer_init(&rs, target)) {
                renderer_destroy(&rs);
                if (!egl_failed_reported && ctx->callbacks.on_error) {
                    ctx->callbacks.on_error(ctx->user, -1, "EGL initialization failed");
                    egl_failed_reported = true;
                }
                sleep_us(100000); // 等待窗口更换后重试
                continue;
            }
        }

        int64_t pts_us = 0;
        unsigned stamp = 0;
        if (!queue_peek_wait(&ctx->frame_queue, &pts_us, &stamp, &ctx->abort_request)) {
            if (atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) break;
            // 帧队列 EOF：视频播完；有音频时等音频也播完
            bool audio_done = !ctx->has_audio || atomic_load(&ctx->audio_eof_epoch) == stamp;
            if (audio_done) report_ended(ctx, stamp);
            // 等待 seek 复位或 abort（支持结尾往回拖）
            if (!queue_wait_stamp_change(&ctx->frame_queue, stamp, &ctx->abort_request)) break;
            continue;
        }

        // 正式播放到区间终点即停；暂停 seek 允许显示端点帧，供截取选区预览。
        if (pts_us >= atomic_load_explicit(&ctx->play_end_us, memory_order_relaxed) &&
            atomic_load(&ctx->state) != PLAYER_PAUSED) {
            report_ended(ctx, stamp);
            // 该队列此时尚未 EOF，等 seek/flush 改变代际，避免反复检查同一帧忙循环
            if (!queue_wait_stamp_change(&ctx->frame_queue, stamp, &ctx->abort_request)) break;
            continue;
        }
        // 与 seek 的时钟复位串行，旧帧不能在 reset 之后重新设置旧基准。
        pthread_mutex_lock(&ctx->seek_mu);
        bool current = stamp == atomic_load(&ctx->media_epoch);
        if (current) clock_sync_base(&ctx->clock, pts_us);
        pthread_mutex_unlock(&ctx->seek_mu);
        if (!current) continue;

        // 等到展示时刻（音频时钟缺席时墙钟冻结即暂停）。
        // 期间若 seek 冲刷队列（代际变化）须立即放弃：旧帧 pts 远超新主时钟，
        // 否则会死等 (旧pts - 新时钟) 的全部时长，表现为画面/进度冻结而声音正常
        while (!atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) {
            if (queue_stamp(&ctx->frame_queue) != stamp) break;
            int64_t wait_us = pts_us - master_clock_us(ctx);
            if (wait_us <= 0) break;
            sleep_us(wait_us > 10000 ? 10000 : wait_us);
        }
        if (atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) break;

        // 取走该帧并上屏；代际变化说明 seek 已清空队列，帧指针失效需重新取
        void *owned = queue_pop_stamped(&ctx->frame_queue, stamp, &ctx->abort_request);
        if (owned) {
            AVFrame *drawn = owned;
            int draw_result = renderer_draw(&rs, drawn);
            av_frame_free(&drawn);
            if (draw_result < 0) {
                char message[AV_ERROR_MAX_STRING_SIZE] = {0};
                av_strerror(draw_result, message, sizeof(message));
                if (ctx->callbacks.on_error) ctx->callbacks.on_error(ctx->user, draw_result, message);
                break;
            }
            if (draw_result == 0) {
                // Surface 销毁/替换可以让 swap 短暂失败；释放 EGL 并在下轮重绑，不上报永久播放错误
                renderer_destroy(&rs);
            }
            // 进度回报节流（200ms）
            if (report_epoch != stamp) {
                report_epoch = stamp;
                ctx->last_report_us = 0;
            }
            if (ctx->callbacks.on_position &&
                (ctx->last_report_us == 0 || pts_us - ctx->last_report_us >= 200000)) {
                ctx->last_report_us = pts_us;
                ctx->callbacks.on_position(ctx->user, pts_us / 1000);
            }
        }
    }

    renderer_destroy(&rs);
    return NULL;
}
