#include "player_core.h"

/** demux 是唯一读包者；epoch 描述当前文件位置，不随外部 seek 请求自动改变。 */
void *demux_thread_func(void *arg) {
    PlayerContext *ctx = arg;
    unsigned epoch = 0;
    while (!atomic_load_explicit(&ctx->abort_request, memory_order_relaxed)) {
        pthread_mutex_lock(&ctx->seek_mu);
        bool seek = ctx->seek_request;
        int64_t target_us = atomic_load(&ctx->seek_target_us);
        if (seek) {
            ctx->seek_request = false;
            epoch = atomic_load(&ctx->media_epoch);
        }
        pthread_mutex_unlock(&ctx->seek_mu);
        if (seek) {
            int64_t ts = av_rescale_q(target_us, av_make_q(1, 1000000), ctx->video_time_base);
            int rc = avformat_seek_file(ctx->fmt, ctx->video_index, INT64_MIN, ts, INT64_MAX, 0);
            if (rc < 0) {
                if (epoch == atomic_load(&ctx->media_epoch) && ctx->callbacks.on_error) {
                    char message[AV_ERROR_MAX_STRING_SIZE] = {0};
                    av_strerror(rc, message, sizeof(message));
                    ctx->callbacks.on_error(ctx->user, rc, message);
                }
                // 不把跳转失败后的旧位置标成新代际；等待下一次 seek 或释放。
                if (!queue_wait_stamp_change(&ctx->packet_queue, epoch, &ctx->abort_request)) break;
                continue;
            }
        }

        AVPacket *pkt = av_packet_alloc();
        if (!pkt) break;
        int rc = av_read_frame(ctx->fmt, pkt);
        if (rc < 0) {
            av_packet_free(&pkt);
            queue_signal_eof(&ctx->packet_queue, epoch);
            queue_signal_eof(&ctx->audio_packet_queue, epoch);
            // 旧 EOF 不得结束新 seek；flush 若已发生，此等待立即返回。
            if (!queue_wait_stamp_change(&ctx->packet_queue, epoch, &ctx->abort_request)) break;
            continue;
        }
        BlockQueue *queue = NULL;
        if (pkt->stream_index == ctx->video_index) queue = &ctx->packet_queue;
        else if (ctx->has_audio && pkt->stream_index == ctx->audio_index) queue = &ctx->audio_packet_queue;
        if (!queue || !queue_push_stamped(queue, pkt, epoch, &ctx->abort_request)) {
            av_packet_free(&pkt);
        }
    }
    return NULL;
}
