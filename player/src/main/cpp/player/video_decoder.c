#include "player_core.h"

#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
#include <stdlib.h>

/** 解码线程私有状态。 */
typedef struct DecoderState {
    int64_t last_pts_us; // 上一帧 PTS，缺 PTS 时按帧间隔递推
} DecoderState;

/** 统一换算到微秒；缺 PTS 时用 best_effort 或按平均帧间隔递推。 */
static int64_t resolve_pts_us(PlayerContext *ctx, AVFrame *frame, DecoderState *st) {
    int64_t pts = frame->pts;
    if (pts == AV_NOPTS_VALUE) pts = frame->best_effort_timestamp;
    int64_t pts_us;
    if (pts != AV_NOPTS_VALUE) {
        pts_us = av_rescale_q(pts, ctx->video_time_base, av_make_q(1, 1000000));
    } else if (ctx->avg_frame_gap_us > 0) {
        pts_us = st->last_pts_us + ctx->avg_frame_gap_us;
    } else {
        pts_us = st->last_pts_us;
    }
    st->last_pts_us = pts_us;
    return pts_us;
}

/** 解码帧 → 独立 YUV420P 帧（pts 存微秒，入帧队列）。 */
static AVFrame *convert_to_yuv420p(PlayerContext *ctx, AVFrame *in, int64_t pts_us) {
    ctx->sws = sws_getCachedContext(ctx->sws, in->width, in->height, in->format,
                                    in->width, in->height, AV_PIX_FMT_YUV420P,
                                    SWS_BILINEAR, NULL, NULL, NULL);
    if (!ctx->sws) return NULL;
    AVFrame *out = av_frame_alloc();
    if (!out) return NULL;
    out->format = AV_PIX_FMT_YUV420P;
    out->width = in->width;
    out->height = in->height;
    out->sample_aspect_ratio = in->sample_aspect_ratio;
    if (av_frame_get_buffer(out, 0) < 0) {
        av_frame_free(&out);
        return NULL;
    }
    sws_scale(ctx->sws, (const uint8_t *const *) in->data, in->linesize, 0, in->height,
              out->data, out->linesize);
    out->pts = pts_us;
    return out;
}

/** 消费滤镜链更新请求（Kotlin setFilterGraph 置入 pending）；失败保持直通并上报。 */
static void process_filter_update(PlayerContext *ctx) {
    char *pending = NULL;
    pthread_mutex_lock(&ctx->filter_mu);
    if (ctx->filter_pending) {
        pending = ctx->filter_pending;
        ctx->filter_pending = NULL;
    }
    pthread_mutex_unlock(&ctx->filter_mu);
    if (!pending) return;
    int rc = filter_state_configure(&ctx->filter, ctx->vcodec, pending);
    if (rc < 0 && ctx->callbacks.on_error) {
        char message[AV_ERROR_MAX_STRING_SIZE] = {0};
        av_strerror(rc, message, sizeof(message));
        ctx->callbacks.on_error(ctx->user, rc, message);
    }
    free(pending);
}

/** 单个解码帧 →（滤镜）→ YUV420P → 帧队列；pts 存微秒；epoch 失配（解码期间 seek）则丢弃。 */
static void push_frame(PlayerContext *ctx, AVFrame *frame, int64_t pts_us, unsigned epoch) {
    unsigned stamp = queue_stamp(&ctx->frame_queue);
    int64_t target_us = atomic_load_explicit(&ctx->seek_target_us, memory_order_relaxed);
    if (pts_us < target_us) return;
    AVFrame *out = NULL;
    if (ctx->filter.graph) {
        // buffersrc time_base 固定 1/1000000：送入帧 pts 即微秒
        frame->pts = pts_us;
        if (av_buffersrc_add_frame(ctx->filter.src, frame) < 0) return;
        AVFrame *filtered = av_frame_alloc();
        if (!filtered) return;
        while (av_buffersink_get_frame(ctx->filter.sink, filtered) == 0) {
            if (!out) {
                out = convert_to_yuv420p(ctx, filtered, filtered->pts);
            }
            av_frame_unref(filtered);
        }
        av_frame_free(&filtered);
    } else {
        out = convert_to_yuv420p(ctx, frame, pts_us);
    }
    if (!out) return;
    // 入队前复核代际：解码/sws 期间发生 seek（perform_seek 已递增）→ 旧帧不得入队重置时钟
    if (atomic_load_explicit(&ctx->media_epoch, memory_order_acquire) != epoch) {
        av_frame_free(&out);
        return;
    }
    if (!queue_push_stamped(&ctx->frame_queue, out, stamp, &ctx->abort_request)) {
        av_frame_free(&out);
    }
}

static void drain_frames(PlayerContext *ctx, DecoderState *st, unsigned epoch) {
    AVFrame *frame = av_frame_alloc();
    if (!frame) return;
    while (avcodec_receive_frame(ctx->vcodec, frame) == 0) {
        int64_t pts_us = resolve_pts_us(ctx, frame, st);
        push_frame(ctx, frame, pts_us, epoch);
        av_frame_unref(frame);
    }
    av_frame_free(&frame);
}

/** 解码线程：packet 队列 → 软解 → [滤镜] → sws 统一 YUV420P → 帧队列；EOF 后等待 seek 复位。 */
void *decode_thread_func(void *arg) {
    PlayerContext *ctx = arg;
    DecoderState st = {0};
    while (!atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) {
        if (atomic_load_explicit(&ctx->video_flush_request, memory_order_relaxed)) {
            atomic_store_explicit(&ctx->video_flush_request, false, memory_order_relaxed);
            avcodec_flush_buffers(ctx->vcodec);
            // seek 冲刷：滤镜图内残留旧时间戳帧，按当前链重建
            filter_state_configure(&ctx->filter, ctx->vcodec, ctx->filter.chain);
        }
        process_filter_update(ctx);
        // 捕获 seek 代际：本轮解码全程携带，入队前复核
        unsigned epoch = atomic_load_explicit(&ctx->media_epoch, memory_order_acquire);
        AVPacket *pkt = queue_pop(&ctx->packet_queue, &ctx->abort_request);
        if (!pkt) {
            // EOF 或 abort：冲刷解码器残余帧
            avcodec_send_packet(ctx->vcodec, NULL);
            drain_frames(ctx, &st, epoch);
            if (atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) break;
            queue_signal_eof(&ctx->frame_queue);
            // EOF 后等待 seek 复位或 abort
            if (!queue_wait_reset(&ctx->packet_queue, &ctx->abort_request)) break;
            continue;
        }
        int rc = avcodec_send_packet(ctx->vcodec, pkt);
        if (rc == AVERROR(EAGAIN)) {
            drain_frames(ctx, &st, epoch);
            rc = avcodec_send_packet(ctx->vcodec, pkt);
        }
        av_packet_free(&pkt);
        drain_frames(ctx, &st, epoch);
        if (rc < 0 && rc != AVERROR(EAGAIN) && ctx->callbacks.on_error) {
            char message[AV_ERROR_MAX_STRING_SIZE] = {0};
            av_strerror(rc, message, sizeof(message));
            ctx->callbacks.on_error(ctx->user, rc, message);
        }
    }
    return NULL;
}
