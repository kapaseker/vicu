#include "player_core.h"

static void packet_free(void *p) {
    AVPacket *pkt = p;
    av_packet_free(&pkt);
}

static void frame_free(void *p) {
    AVFrame *f = p;
    av_frame_free(&f);
}

/** demux 线程内执行 seek：清队列、复位解码器与时钟后跳转。 */
static void perform_seek(PlayerContext *ctx) {
    int64_t target_us = ctx->seek_target_us;
    queue_flush(&ctx->packet_queue, packet_free);
    queue_flush(&ctx->audio_packet_queue, packet_free);
    queue_flush(&ctx->frame_queue, frame_free);
    atomic_store(&ctx->video_flush_request, true);
    if (ctx->has_audio) atomic_store(&ctx->audio_flush_request, true);
    atomic_store(&ctx->audio_eof, false);
    clock_reset(&ctx->clock);
    ctx->last_report_us = 0;
    if (ctx->state == PLAYER_ENDED) atomic_store(&ctx->state, PLAYER_PLAYING);

    int64_t ts = av_rescale_q(target_us, av_make_q(1, 1000000), ctx->video_time_base);
    avformat_seek_file(ctx->fmt, ctx->video_index, INT64_MIN, ts, INT64_MAX, 0);
}

/** demux 线程：av_read_frame → 按流分发 packet 队列；并执行 seek。 */
void *demux_thread_func(void *arg) {
    PlayerContext *ctx = arg;
    while (!atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) {
        if (atomic_load_explicit(&ctx->seek_request, memory_order_acquire)) {
            atomic_store_explicit(&ctx->seek_request, false, memory_order_release);
            perform_seek(ctx);
        }

        AVPacket *pkt = av_packet_alloc();
        if (!pkt) break;
        int rc = av_read_frame(ctx->fmt, pkt);
        if (rc < 0) {
            av_packet_free(&pkt);
            queue_signal_eof(&ctx->packet_queue);
            queue_signal_eof(&ctx->audio_packet_queue);
            // EOF 后等待 seek 复位或 abort（支持结尾往回拖）
            if (!queue_wait_reset(&ctx->packet_queue, &ctx->abort_request)) break;
            continue;
        }
        if (pkt->stream_index == ctx->video_index) {
            if (!queue_push(&ctx->packet_queue, pkt, &ctx->abort_request)) {
                av_packet_free(&pkt);
                break;
            }
        } else if (ctx->has_audio && pkt->stream_index == ctx->audio_index) {
            if (!queue_push(&ctx->audio_packet_queue, pkt, &ctx->abort_request)) {
                av_packet_free(&pkt);
                break;
            }
        } else {
            av_packet_free(&pkt);
        }
    }
    return NULL;
}
