#ifndef VICU_FILTER_GRAPH_H
#define VICU_FILTER_GRAPH_H

#include <libavcodec/avcodec.h>
#include <libavfilter/avfilter.h>

/**
 * 视频滤镜插槽（buffersrc → avfilter_graph_parse → buffersink）：
 * 插在解码与帧队列之间；chain 为空时直通（不建图）。
 * 仅解码线程访问；参数变化由外部线程经 PlayerContext.filter_pending 请求重建。
 */
typedef struct FilterState {
    AVFilterGraph *graph;
    AVFilterContext *src;
    AVFilterContext *sink;
    char *chain; // 当前生效链（NULL/空 = 直通）
} FilterState;

void filter_state_init(FilterState *fs);
void filter_state_destroy(FilterState *fs);
/**
 * 按新链重建图；chain 为 NULL/空串则拆图直通。
 * buffersrc 匹配解码器输出参数、time_base 固定 1/1000000（帧 pts 存微秒），
 * buffersink 约束输出 YUV420P；失败时回退直通并返回负值错误码。
 */
int filter_state_configure(FilterState *fs, const AVCodecContext *codec, const char *chain);

#endif
