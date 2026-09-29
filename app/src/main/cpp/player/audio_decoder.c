#include "player_core.h"

#include <libswresample/swresample.h>
#include <stdlib.h>

#define AUDIO_OUT_SAMPLE_RATE 48000
#define AUDIO_OUT_CHANNELS 2

/** 音频解码线程私有状态。 */
typedef struct AudioState {
    struct SwrContext *swr;
    uint8_t *buffer;         // 重采样输出（交错 S16）
    int buffer_capacity;     // 字节
    int64_t last_pts_us;
    // swr 输入参数快照（SwrContext 不透明，需自行比较是否重建）
    int in_format;
    int in_rate;
    AVChannelLayout in_layout;
} AudioState;

static void audio_state_free(AudioState *st) {
    if (st->swr) swr_free(&st->swr);
    free(st->buffer);
    st->buffer = NULL;
    st->buffer_capacity = 0;
    av_channel_layout_uninit(&st->in_layout);
}

/** 输入参数变化时重建 swr（S16 双声道 48kHz 交错输出）。 */
static bool ensure_swr(AudioState *st, AVFrame *frame) {
    if (st->swr) {
        if (frame->format == st->in_format && frame->sample_rate == st->in_rate &&
            av_channel_layout_compare(&frame->ch_layout, &st->in_layout) == 0) {
            return true;
        }
        swr_free(&st->swr);
    }
    AVChannelLayout out_layout = AV_CHANNEL_LAYOUT_STEREO;
    int rc = swr_alloc_set_opts2(
            &st->swr,
            &out_layout, AV_SAMPLE_FMT_S16, AUDIO_OUT_SAMPLE_RATE,
            &frame->ch_layout, frame->format, frame->sample_rate,
            0, NULL);
    if (rc < 0 || !st->swr) return false;
    if (swr_init(st->swr) < 0) return false;
    av_channel_layout_uninit(&st->in_layout);
    return av_channel_layout_copy(&st->in_layout, &frame->ch_layout) >= 0;
}

/** 解码帧 → S16 双声道 48kHz 交错后经回调送 Kotlin AudioTrack（阻塞写回即背压）。 */
static void emit_audio_frame(PlayerContext *ctx, AVFrame *frame, AudioState *st, unsigned epoch) {
    if (!ensure_swr(st, frame)) return;

    int64_t pts = frame->pts;
    if (pts == AV_NOPTS_VALUE) pts = frame->best_effort_timestamp;
    int64_t pts_us = st->last_pts_us;
    if (pts != AV_NOPTS_VALUE) {
        pts_us = av_rescale_q(pts, ctx->audio_time_base, av_make_q(1, 1000000));
    }
    st->last_pts_us = pts_us +
                      (int64_t) frame->nb_samples * 1000000 / AUDIO_OUT_SAMPLE_RATE;
    // 播放区间终点（trim 预览）：区间外的音频静默丢弃
    if (pts_us >= atomic_load_explicit(&ctx->play_end_us, memory_order_relaxed)) {
        return;
    }
    // seek 代际失效：解码期间发生 seek（向后 seek 的旧帧 pts 可 ≥ 新目标，pts 守卫拦不住）
    if (atomic_load_explicit(&ctx->media_epoch, memory_order_acquire) != epoch) return;
    int64_t target_us = atomic_load_explicit(&ctx->seek_target_us, memory_order_relaxed);
    if (pts_us < target_us) return;

    int max_out = swr_get_out_samples(st->swr, frame->nb_samples);
    if (max_out <= 0) return;
    int needed = max_out * AUDIO_OUT_CHANNELS * (int) sizeof(int16_t);
    if (needed > st->buffer_capacity) {
        free(st->buffer);
        st->buffer = malloc(needed);
        st->buffer_capacity = needed;
        if (!st->buffer) return;
    }
    uint8_t *out_plane[1] = {st->buffer};
    int converted = swr_convert(st->swr, out_plane, max_out,
                                (const uint8_t *const *) frame->data, frame->nb_samples);
    if (converted <= 0) return;
    int size = converted * AUDIO_OUT_CHANNELS * (int) sizeof(int16_t);
    ctx->callbacks.on_audio_data(ctx->user, st->buffer, size, pts_us, epoch);
}

static void drain_audio(PlayerContext *ctx, AudioState *st, unsigned epoch) {
    AVFrame *frame = av_frame_alloc();
    if (!frame) return;
    while (avcodec_receive_frame(ctx->acodec, frame) == 0) {
        emit_audio_frame(ctx, frame, st, epoch);
        av_frame_unref(frame);
    }
    av_frame_free(&frame);
}

/** 音频解码线程：音频 packet 队列 → 解码 → swr → JNI 回调；EOF 后等待 seek 复位。 */
void *audio_thread_func(void *arg) {
    PlayerContext *ctx = arg;
    AudioState st = {0};
    while (!atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) {
        if (atomic_load_explicit(&ctx->audio_flush_request, memory_order_relaxed)) {
            atomic_store_explicit(&ctx->audio_flush_request, false, memory_order_relaxed);
            avcodec_flush_buffers(ctx->acodec);
            swr_free(&st.swr);
        }
        // 捕获 seek 代际：本轮解码全程携带，发射前复核（seek 后旧帧无论 pts 均丢弃）
        unsigned epoch = atomic_load_explicit(&ctx->media_epoch, memory_order_acquire);
        AVPacket *pkt = queue_pop(&ctx->audio_packet_queue, &ctx->abort_request);
        if (!pkt) {
            avcodec_send_packet(ctx->acodec, NULL);
            drain_audio(ctx, &st, epoch);
            if (atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) break;
            atomic_store(&ctx->audio_eof, true);
            // EOF 后等待 seek 复位或 abort
            if (!queue_wait_reset(&ctx->audio_packet_queue, &ctx->abort_request)) break;
            continue;
        }
        int rc = avcodec_send_packet(ctx->acodec, pkt);
        if (rc == AVERROR(EAGAIN)) {
            drain_audio(ctx, &st, epoch);
            rc = avcodec_send_packet(ctx->acodec, pkt);
        }
        av_packet_free(&pkt);
        drain_audio(ctx, &st, epoch);
    }
    audio_state_free(&st);
    return NULL;
}
