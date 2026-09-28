#include "filter_graph.h"

#include <libavutil/opt.h>
#include <stdlib.h>
#include <string.h>

void filter_state_init(FilterState *fs) {
    memset(fs, 0, sizeof(*fs));
}

void filter_state_destroy(FilterState *fs) {
    if (fs->graph) avfilter_graph_free(&fs->graph);
    free(fs->chain);
    fs->chain = NULL;
    fs->src = NULL;
    fs->sink = NULL;
}

int filter_state_configure(FilterState *fs, const AVCodecContext *codec, const char *chain) {
    filter_state_destroy(fs); // 先拆旧图；失败时保持直通
    if (!chain || !chain[0]) return 0;

    fs->graph = avfilter_graph_alloc();
    if (!fs->graph) return AVERROR(ENOMEM);

    AVRational aspect = codec->sample_aspect_ratio;
    if (aspect.num <= 0 || aspect.den <= 0) aspect = av_make_q(1, 1);
    char args[512];
    snprintf(args, sizeof(args),
             "video_size=%dx%d:pix_fmt=%d:time_base=1/1000000:pixel_aspect=%d/%d",
             codec->width, codec->height, codec->pix_fmt, aspect.num, aspect.den);

    const AVFilter *buffersrc = avfilter_get_by_name("buffer");
    const AVFilter *buffersink = avfilter_get_by_name("buffersink");
    int rc = AVERROR_FILTER_NOT_FOUND;
    if (!buffersrc || !buffersink) goto fail;

    rc = avfilter_graph_create_filter(&fs->src, buffersrc, "in", args, NULL, fs->graph);
    if (rc < 0) goto fail;
    rc = avfilter_graph_create_filter(&fs->sink, buffersink, "out", NULL, NULL, fs->graph);
    if (rc < 0) goto fail;

    // 滤镜输出统一 YUV420P（与直通路径的 sws 转换目标一致）；
    // 预构建头文件无 av_opt_set_int_list 宏，按其展开用 av_opt_set_bin 等价设置
    const enum AVPixelFormat pix_fmts[] = {AV_PIX_FMT_YUV420P, AV_PIX_FMT_NONE};
    rc = av_opt_set_bin(fs->sink, "pix_fmts", (const uint8_t *) pix_fmts,
                        (int) (sizeof(pix_fmts) - sizeof(pix_fmts[0])),
                        AV_OPT_SEARCH_CHILDREN);
    if (rc < 0) goto fail;

    // 链两端接到 buffersrc/buffersink（名字与 create_filter 一致）
    AVFilterInOut *outputs = avfilter_inout_alloc(); // buffersrc 的输出
    AVFilterInOut *inputs = avfilter_inout_alloc();  // buffersink 的输入
    if (!outputs || !inputs) {
        rc = AVERROR(ENOMEM);
        goto inout_fail;
    }
    outputs->name = av_strdup("in");
    outputs->filter_ctx = fs->src;
    outputs->pad_idx = 0;
    outputs->next = NULL;
    inputs->name = av_strdup("out");
    inputs->filter_ctx = fs->sink;
    inputs->pad_idx = 0;
    inputs->next = NULL;
    rc = avfilter_graph_parse_ptr(fs->graph, chain, &inputs, &outputs, NULL);
    rc = rc < 0 ? rc : avfilter_graph_config(fs->graph, NULL);

inout_fail:
    avfilter_inout_free(&inputs);
    avfilter_inout_free(&outputs);
    if (rc < 0) goto fail;

    fs->chain = strdup(chain);
    return 0;

fail:
    filter_state_destroy(fs);
    return rc;
}
