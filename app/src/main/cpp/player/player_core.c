#include "player_core.h"

#include <libavutil/time.h>
#include <libswscale/swscale.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

// ---------------- 队列 ----------------

bool queue_init(BlockQueue *q, int capacity) {
    memset(q, 0, sizeof(*q));
    q->capacity = capacity;
    if (pthread_mutex_init(&q->mu, NULL) != 0) return false;
    if (pthread_cond_init(&q->not_full, NULL) != 0) {
        pthread_mutex_destroy(&q->mu);
        return false;
    }
    if (pthread_cond_init(&q->not_empty, NULL) != 0) {
        pthread_cond_destroy(&q->not_full);
        pthread_mutex_destroy(&q->mu);
        return false;
    }
    return true;
}

void queue_flush(BlockQueue *q, void (*free_item)(void *)) {
    pthread_mutex_lock(&q->mu);
    QueueNode *node = q->head;
    while (node) {
        QueueNode *next = node->next;
        if (node->data) free_item(node->data);
        free(node);
        node = next;
    }
    q->head = q->tail = NULL;
    q->size = 0;
    q->eof = false;
    q->stamp++;
    pthread_cond_broadcast(&q->not_full);
    pthread_cond_broadcast(&q->not_empty);
    pthread_mutex_unlock(&q->mu);
}

void queue_destroy(BlockQueue *q, void (*free_item)(void *)) {
    queue_flush(q, free_item);
    pthread_mutex_destroy(&q->mu);
    pthread_cond_destroy(&q->not_full);
    pthread_cond_destroy(&q->not_empty);
}

bool queue_push(BlockQueue *q, void *data, const atomic_bool *abort) {
    pthread_mutex_lock(&q->mu);
    while (q->size >= q->capacity && !q->eof && !atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_cond_wait(&q->not_full, &q->mu);
    }
    if (q->size >= q->capacity || q->eof || atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_mutex_unlock(&q->mu);
        return false;
    }
    QueueNode *node = malloc(sizeof(QueueNode));
    if (!node) {
        pthread_mutex_unlock(&q->mu);
        return false;
    }
    node->data = data;
    node->next = NULL;
    if (q->tail) q->tail->next = node;
    else q->head = node;
    q->tail = node;
    q->size++;
    pthread_cond_signal(&q->not_empty);
    pthread_mutex_unlock(&q->mu);
    return true;
}

void *queue_pop(BlockQueue *q, const atomic_bool *abort) {
    pthread_mutex_lock(&q->mu);
    while (q->head == NULL && !q->eof && !atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_cond_wait(&q->not_empty, &q->mu);
    }
    if (q->head == NULL) {
        pthread_mutex_unlock(&q->mu);
        return NULL;
    }
    QueueNode *node = q->head;
    q->head = node->next;
    if (!q->head) q->tail = NULL;
    q->size--;
    void *data = node->data;
    free(node);
    pthread_cond_signal(&q->not_full);
    pthread_mutex_unlock(&q->mu);
    return data;
}

bool queue_peek_wait(BlockQueue *q, void **out, unsigned *stamp, const atomic_bool *abort) {
    pthread_mutex_lock(&q->mu);
    while (q->head == NULL && !q->eof && !atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_cond_wait(&q->not_empty, &q->mu);
    }
    bool has = q->head != NULL;
    if (has) {
        *out = q->head->data;
        *stamp = q->stamp;
    }
    pthread_mutex_unlock(&q->mu);
    return has;
}

void queue_abort_broadcast(BlockQueue *q) {
    pthread_mutex_lock(&q->mu);
    pthread_cond_broadcast(&q->not_full);
    pthread_cond_broadcast(&q->not_empty);
    pthread_mutex_unlock(&q->mu);
}

void queue_signal_eof(BlockQueue *q) {
    pthread_mutex_lock(&q->mu);
    q->eof = true;
    pthread_cond_broadcast(&q->not_empty);
    pthread_mutex_unlock(&q->mu);
}

bool queue_wait_reset(BlockQueue *q, const atomic_bool *abort) {
    pthread_mutex_lock(&q->mu);
    while (q->eof && !atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_cond_wait(&q->not_full, &q->mu); // flush 广播 not_full
    }
    bool reset = !q->eof;
    pthread_mutex_unlock(&q->mu);
    return reset;
}

bool queue_push_stamped(BlockQueue *q, void *data, unsigned stamp, const atomic_bool *abort) {
    pthread_mutex_lock(&q->mu);
    while (q->stamp == stamp && q->size >= q->capacity && !q->eof &&
           !atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_cond_wait(&q->not_full, &q->mu);
    }
    if (q->stamp != stamp || q->size >= q->capacity || q->eof ||
        atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_mutex_unlock(&q->mu);
        return false;
    }
    QueueNode *node = malloc(sizeof(QueueNode));
    if (!node) {
        pthread_mutex_unlock(&q->mu);
        return false;
    }
    node->data = data;
    node->next = NULL;
    if (q->tail) q->tail->next = node;
    else q->head = node;
    q->tail = node;
    q->size++;
    pthread_cond_signal(&q->not_empty);
    pthread_mutex_unlock(&q->mu);
    return true;
}

bool queue_wait_stamp_change(BlockQueue *q, unsigned stamp, const atomic_bool *abort) {
    pthread_mutex_lock(&q->mu);
    while (q->stamp == stamp && !atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_cond_wait(&q->not_empty, &q->mu); // flush/abort 均会广播 not_empty
    }
    bool changed = q->stamp != stamp;
    pthread_mutex_unlock(&q->mu);
    return changed;
}

unsigned queue_stamp(BlockQueue *q) {
    pthread_mutex_lock(&q->mu);
    unsigned stamp = q->stamp;
    pthread_mutex_unlock(&q->mu);
    return stamp;
}

void *queue_pop_stamped(BlockQueue *q, unsigned stamp, const atomic_bool *abort) {
    pthread_mutex_lock(&q->mu);
    if (q->stamp != stamp || q->head == NULL ||
        atomic_load_explicit(abort, memory_order_relaxed)) {
        pthread_mutex_unlock(&q->mu);
        return NULL;
    }
    QueueNode *node = q->head;
    q->head = node->next;
    if (!q->head) q->tail = NULL;
    q->size--;
    void *data = node->data;
    free(node);
    pthread_cond_signal(&q->not_full);
    pthread_mutex_unlock(&q->mu);
    return data;
}

// ---------------- 时钟 ----------------

void clock_init(Clock *c) {
    pthread_mutex_init(&c->mu, NULL);
    c->base_pts_us = AV_NOPTS_VALUE;
    c->base_wall_us = 0;
    c->pause_wall_us = 0;
}

void clock_destroy(Clock *c) {
    pthread_mutex_destroy(&c->mu);
}

void clock_sync_base(Clock *c, int64_t pts_us) {
    pthread_mutex_lock(&c->mu);
    if (c->base_pts_us == AV_NOPTS_VALUE) {
        c->base_pts_us = pts_us;
        c->base_wall_us = av_gettime();
    }
    pthread_mutex_unlock(&c->mu);
}

void clock_reset(Clock *c) {
    pthread_mutex_lock(&c->mu);
    c->base_pts_us = AV_NOPTS_VALUE;
    c->base_wall_us = 0;
    c->pause_wall_us = 0;
    pthread_mutex_unlock(&c->mu);
}

void clock_pause(Clock *c) {
    pthread_mutex_lock(&c->mu);
    if (c->pause_wall_us == 0) c->pause_wall_us = av_gettime();
    pthread_mutex_unlock(&c->mu);
}

void clock_resume(Clock *c) {
    pthread_mutex_lock(&c->mu);
    if (c->pause_wall_us > 0) {
        c->base_wall_us += av_gettime() - c->pause_wall_us;
        c->pause_wall_us = 0;
    }
    pthread_mutex_unlock(&c->mu);
}

int64_t clock_playback_us(Clock *c) {
    pthread_mutex_lock(&c->mu);
    int64_t result = 0;
    if (c->base_pts_us != AV_NOPTS_VALUE) {
        int64_t wall = c->pause_wall_us > 0 ? c->pause_wall_us : av_gettime();
        result = c->base_pts_us + (wall - c->base_wall_us);
    }
    pthread_mutex_unlock(&c->mu);
    return result;
}

// ---------------- 生命周期 ----------------

static void packet_free(void *p) {
    AVPacket *pkt = p;
    av_packet_free(&pkt);
}

static void frame_free(void *p) {
    AVFrame *f = p;
    av_frame_free(&f);
}

PlayerContext *player_create(const PlayerCallbacks *callbacks, void *user) {
    PlayerContext *ctx = calloc(1, sizeof(PlayerContext));
    if (!ctx) return NULL;
    atomic_init(&ctx->state, PLAYER_IDLE);
    atomic_init(&ctx->abort_request, false);
    atomic_init(&ctx->video_flush_request, false);
    atomic_init(&ctx->audio_flush_request, false);
    atomic_init(&ctx->audio_eof, false);
    atomic_init(&ctx->seek_request, false);
    atomic_init(&ctx->seek_target_us, 0);
    atomic_init(&ctx->play_start_us, 0);
    atomic_init(&ctx->play_end_us, INT64_MAX);
    ctx->callbacks = *callbacks;
    ctx->user = user;
    ctx->video_index = -1;
    ctx->audio_index = -1;
    filter_state_init(&ctx->filter);
    pthread_mutex_init(&ctx->filter_mu, NULL);
    pthread_mutex_init(&ctx->surface_mu, NULL);
    pthread_cond_init(&ctx->surface_cond, NULL);
    clock_init(&ctx->clock);
    // 包队列放宽（packet 体积小），帧队列收紧（YUV 帧内存大）
    if (!queue_init(&ctx->packet_queue, 256)) goto fail_packet_queue;
    if (!queue_init(&ctx->frame_queue, 4)) goto fail_frame_queue;
    if (!queue_init(&ctx->audio_packet_queue, 64)) goto fail_audio_queue;
    return ctx;

fail_audio_queue:
    queue_destroy(&ctx->frame_queue, frame_free);
fail_frame_queue:
    queue_destroy(&ctx->packet_queue, packet_free);
fail_packet_queue:
    clock_destroy(&ctx->clock);
    pthread_cond_destroy(&ctx->surface_cond);
    pthread_mutex_destroy(&ctx->surface_mu);
    pthread_mutex_destroy(&ctx->filter_mu);
    free(ctx);
    return NULL;
}

/** 打开指定类型流并创建解码器上下文；失败返回负值错误码。 */
static int open_stream_decoder(AVFormatContext *fmt, int stream_index, AVCodecContext **out) {
    AVStream *stream = fmt->streams[stream_index];
    const AVCodec *codec = avcodec_find_decoder(stream->codecpar->codec_id);
    if (!codec) return AVERROR_DECODER_NOT_FOUND;
    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx) return AVERROR(ENOMEM);
    int rc = avcodec_parameters_to_context(codec_ctx, stream->codecpar);
    if (rc < 0) {
        avcodec_free_context(&codec_ctx);
        return rc;
    }
    rc = avcodec_open2(codec_ctx, codec, NULL);
    if (rc < 0) {
        avcodec_free_context(&codec_ctx);
        return rc;
    }
    *out = codec_ctx;
    return 0;
}

int player_prepare(PlayerContext *ctx, int fd) {
    // fd 协议禁止在 URL 中传数字，必须通过 AVOption 设置；协议内部会 dup。
    AVDictionary *options = NULL;
    int rc = av_dict_set_int(&options, "fd", fd, 0);
    if (rc >= 0) rc = avformat_open_input(&ctx->fmt, "fd:", NULL, &options);
    av_dict_free(&options);
    close(fd);
    if (rc < 0) return rc;
    rc = avformat_find_stream_info(ctx->fmt, NULL);
    if (rc < 0) return rc;

    ctx->video_index = av_find_best_stream(ctx->fmt, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    if (ctx->video_index < 0) return ctx->video_index;
    AVStream *video_stream = ctx->fmt->streams[ctx->video_index];
    ctx->video_time_base = video_stream->time_base;
    if (ctx->fmt->duration > 0) ctx->duration_us = ctx->fmt->duration;
    if (video_stream->avg_frame_rate.num > 0 && video_stream->avg_frame_rate.den > 0) {
        ctx->avg_frame_gap_us = av_rescale_q(1, video_stream->avg_frame_rate, av_make_q(1, 1000000));
    }
    rc = open_stream_decoder(ctx->fmt, ctx->video_index, &ctx->vcodec);
    if (rc < 0) return rc;

    // 音频可选：失败不阻断视频播放
    ctx->audio_index = av_find_best_stream(ctx->fmt, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
    if (ctx->audio_index >= 0) {
        AVStream *audio_stream = ctx->fmt->streams[ctx->audio_index];
        ctx->audio_time_base = audio_stream->time_base;
        if (open_stream_decoder(ctx->fmt, ctx->audio_index, &ctx->acodec) == 0) {
            ctx->has_audio = true;
        } else {
            ctx->audio_index = -1;
        }
    }

    atomic_store(&ctx->state, PLAYER_READY);
    int thread_rc = pthread_create(&ctx->demux_thread, NULL, demux_thread_func, ctx);
    if (thread_rc != 0) goto thread_failed;
    ctx->demux_started = true;
    thread_rc = pthread_create(&ctx->decode_thread, NULL, decode_thread_func, ctx);
    if (thread_rc != 0) goto thread_failed;
    ctx->decode_started = true;
    if (ctx->has_audio) {
        thread_rc = pthread_create(&ctx->audio_thread, NULL, audio_thread_func, ctx);
        if (thread_rc != 0) goto thread_failed;
        ctx->audio_started = true;
    }
    thread_rc = pthread_create(&ctx->render_thread, NULL, render_thread_func, ctx);
    if (thread_rc != 0) goto thread_failed;
    ctx->render_started = true;

    if (ctx->callbacks.on_prepared) {
        ctx->callbacks.on_prepared(ctx->user, ctx->vcodec->width, ctx->vcodec->height,
                                   ctx->duration_us / 1000, ctx->has_audio);
    }
    return 0;

thread_failed:
    atomic_store(&ctx->state, PLAYER_ERROR);
    return AVERROR(thread_rc);
}

void player_set_window(PlayerContext *ctx, ANativeWindow *window) {
    pthread_mutex_lock(&ctx->surface_mu);
    ANativeWindow *old = ctx->window;
    ctx->window = window;
    pthread_cond_broadcast(&ctx->surface_cond);
    pthread_mutex_unlock(&ctx->surface_mu);
    if (old) ANativeWindow_release(old);
}

void player_start(PlayerContext *ctx) {
    atomic_store(&ctx->state, PLAYER_PLAYING);
    clock_resume(&ctx->clock);
}

void player_pause(PlayerContext *ctx) {
    atomic_store(&ctx->state, PLAYER_PAUSED);
    clock_pause(&ctx->clock);
}

void player_seek(PlayerContext *ctx, int64_t position_ms) {
    if (position_ms < 0) position_ms = 0;
    int64_t target_us = position_ms * 1000;
    // clamp 到播放区间（trim 预览）
    int64_t start_us = atomic_load_explicit(&ctx->play_start_us, memory_order_relaxed);
    int64_t end_us = atomic_load_explicit(&ctx->play_end_us, memory_order_relaxed);
    if (target_us < start_us) target_us = start_us;
    if (target_us > end_us) target_us = end_us;
    atomic_store_explicit(&ctx->seek_target_us, target_us, memory_order_relaxed);
    atomic_store_explicit(&ctx->seek_request, true, memory_order_release);
    // 清空背压队列，使阻塞的 demux 立即回到循环处理 seek；perform_seek 会再次冲刷竞态写入。
    queue_flush(&ctx->packet_queue, packet_free);
    queue_flush(&ctx->audio_packet_queue, packet_free);
    queue_flush(&ctx->frame_queue, frame_free);
}

void player_set_filter_graph(PlayerContext *ctx, const char *chain) {
    char *pending = strdup(chain ? chain : "");
    if (!pending) return;
    pthread_mutex_lock(&ctx->filter_mu);
    free(ctx->filter_pending);
    ctx->filter_pending = pending;
    pthread_mutex_unlock(&ctx->filter_mu);
}

void player_set_play_range(PlayerContext *ctx, int64_t start_ms, int64_t end_ms) {
    if (start_ms < 0) start_ms = 0;
    atomic_store_explicit(&ctx->play_start_us, start_ms * 1000, memory_order_relaxed);
    atomic_store_explicit(
            &ctx->play_end_us,
            end_ms < 0 ? INT64_MAX : end_ms * 1000,
            memory_order_relaxed);
}

void player_destroy(PlayerContext *ctx) {
    atomic_store(&ctx->abort_request, true);
    queue_abort_broadcast(&ctx->packet_queue);
    queue_abort_broadcast(&ctx->audio_packet_queue);
    queue_abort_broadcast(&ctx->frame_queue);
    pthread_mutex_lock(&ctx->surface_mu);
    pthread_cond_broadcast(&ctx->surface_cond);
    pthread_mutex_unlock(&ctx->surface_mu);

    if (ctx->demux_started) pthread_join(ctx->demux_thread, NULL);
    if (ctx->decode_started) pthread_join(ctx->decode_thread, NULL);
    if (ctx->audio_started) pthread_join(ctx->audio_thread, NULL);
    if (ctx->render_started) pthread_join(ctx->render_thread, NULL);

    if (ctx->sws) sws_freeContext(ctx->sws);
    if (ctx->vcodec) avcodec_free_context(&ctx->vcodec);
    if (ctx->acodec) avcodec_free_context(&ctx->acodec);
    if (ctx->fmt) avformat_close_input(&ctx->fmt);
    filter_state_destroy(&ctx->filter);
    free(ctx->filter_pending);
    queue_destroy(&ctx->packet_queue, packet_free);
    queue_destroy(&ctx->audio_packet_queue, packet_free);
    queue_destroy(&ctx->frame_queue, frame_free);
    if (ctx->window) ANativeWindow_release(ctx->window);
    clock_destroy(&ctx->clock);
    pthread_mutex_destroy(&ctx->filter_mu);
    pthread_mutex_destroy(&ctx->surface_mu);
    pthread_cond_destroy(&ctx->surface_cond);
    if (ctx->callbacks.on_destroy) ctx->callbacks.on_destroy(ctx->user);
}

const char *player_state_name(PlayerState state) {
    switch (state) {
        case PLAYER_IDLE: return "IDLE";
        case PLAYER_READY: return "READY";
        case PLAYER_PLAYING: return "PLAYING";
        case PLAYER_PAUSED: return "PAUSED";
        case PLAYER_ENDED: return "ENDED";
        case PLAYER_ERROR: return "ERROR";
    }
    return "UNKNOWN";
}
