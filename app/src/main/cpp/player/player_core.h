#ifndef VICU_PLAYER_CORE_H
#define VICU_PLAYER_CORE_H

#include <android/native_window.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/rational.h>

#include "filter_graph.h"

/** 播放器状态（外部控制面视角）。 */
typedef enum {
    PLAYER_IDLE = 0,
    PLAYER_READY,   // prepare 完成，线程已启动
    PLAYER_PLAYING,
    PLAYER_PAUSED,
    PLAYER_ENDED,
    PLAYER_ERROR,
} PlayerState;

/** 工作线程回调；user 为 JNI 层持有的 NativePlayerImpl 全局引用。 */
typedef struct PlayerCallbacks {
    void (*on_prepared)(void *user, int width, int height, int64_t duration_ms, bool has_audio);
    void (*on_position)(void *user, int64_t position_ms);
    void (*on_ended)(void *user);
    void (*on_error)(void *user, int code, const char *message);
    void (*on_destroy)(void *user);
    /** 音频帧回调（S16 双声道 48kHz 交错）；阻塞写回即自然背压。 */
    void (*on_audio_data)(void *user, const uint8_t *data, int size, int64_t pts_us);
    /** 音频主时钟（微秒，媒体时间）；无有效时钟时返回 -1（渲染回退墙钟）。 */
    int64_t (*get_audio_clock_us)(void *user);
} PlayerCallbacks;

/** 有界阻塞队列（void* 节点）：demux → decode → render 之间背压。 */
typedef struct QueueNode {
    void *data;
    struct QueueNode *next;
} QueueNode;

typedef struct BlockQueue {
    QueueNode *head;
    QueueNode *tail;
    int size;
    int capacity;
    bool eof;         // 生产者已结束
    unsigned stamp;   // flush 代际：消费方跨等待持引用时校验失效
    pthread_mutex_t mu;
    pthread_cond_t not_full;
    pthread_cond_t not_empty;
} BlockQueue;

bool queue_init(BlockQueue *q, int capacity);
void queue_destroy(BlockQueue *q, void (*free_item)(void *));
/** 成功则队列接管 data 所有权；abort 或队列已结束时返回 false（调用方自行释放）。 */
bool queue_push(BlockQueue *q, void *data, const atomic_bool *abort);
/** 空且（eof 或 abort）时返回 NULL。 */
void *queue_pop(BlockQueue *q, const atomic_bool *abort);
/** 阻塞等待直到有元素或（空且 eof/abort）；有元素时经 *out 返回（不接管所有权），*stamp 为对应代际。 */
bool queue_peek_wait(BlockQueue *q, void **out, unsigned *stamp, const atomic_bool *abort);
/** 清空队列、复位 eof 并递增代际（seek 用）；唤醒所有等待者。 */
void queue_flush(BlockQueue *q, void (*free_item)(void *));
/** 等待 eof 被 flush 复位或 abort；返回 true 表示可继续消费。 */
bool queue_wait_reset(BlockQueue *q, const atomic_bool *abort);
/** 等待 flush 改变队列代际（trim 终点后等待 seek）。 */
bool queue_wait_stamp_change(BlockQueue *q, unsigned stamp, const atomic_bool *abort);
/** 当前代际。 */
unsigned queue_stamp(BlockQueue *q);
/** 仅当代际未变化时弹出（peek 后跨等待防 flush 竞态）。 */
void *queue_pop_stamped(BlockQueue *q, unsigned stamp, const atomic_bool *abort);
/** 唤醒所有等待者（stop 时配合 abort 标志使用）。 */
void queue_abort_broadcast(BlockQueue *q);
void queue_signal_eof(BlockQueue *q);

/** 播放时钟：暂停冻结、恢复平移基准，避免暂停时长计入播放进度。 */
typedef struct Clock {
    pthread_mutex_t mu;
    int64_t base_pts_us;   // 起播帧 PTS（AV_NOPTS_VALUE = 未校准）
    int64_t base_wall_us;  // base_pts 对应的墙钟时刻
    int64_t pause_wall_us; // 暂停时刻墙钟（0 = 未暂停）
} Clock;

void clock_init(Clock *c);
void clock_destroy(Clock *c);
/** 首帧校准（仅一次）。 */
void clock_sync_base(Clock *c, int64_t pts_us);
/** 复位到未校准（seek 后由下一视频帧重新校准）。 */
void clock_reset(Clock *c);
void clock_pause(Clock *c);
void clock_resume(Clock *c);
/** 当前播放位置（微秒）；暂停时冻结在暂停点。 */
int64_t clock_playback_us(Clock *c);

typedef struct PlayerContext {
    atomic_int state;
    atomic_bool abort_request;

    AVFormatContext *fmt;
    int video_index;
    AVRational video_time_base;
    int audio_index;
    AVRational audio_time_base;
    int64_t duration_us;
    int64_t avg_frame_gap_us; // 平均帧间隔（估算缺 PTS 帧用，未知为 0）
    bool has_audio;

    AVCodecContext *vcodec;
    AVCodecContext *acodec;
    struct SwsContext *sws;

    BlockQueue packet_queue;      // AVPacket*（视频）
    BlockQueue audio_packet_queue; // AVPacket*（音频）
    BlockQueue frame_queue;       // AVFrame*（YUV420P；pts 字段约定存微秒）

    pthread_t demux_thread;
    pthread_t decode_thread;
    pthread_t audio_thread;
    pthread_t render_thread;
    bool demux_started;
    bool decode_started;
    bool audio_started;
    bool render_started;

    atomic_bool video_flush_request; // demux seek 置位，解码线程消费
    atomic_bool audio_flush_request;
    atomic_bool audio_eof;

    atomic_bool seek_request; // demux 线程消费
    int64_t seek_target_us;   // 仅在 seek_request 置位后读写

    // 视频滤镜链（Kotlin setFilterGraph 请求 → 解码线程消费重建）
    FilterState filter;      // 仅解码线程访问
    pthread_mutex_t filter_mu;
    char *filter_pending;    // 待重建链（NULL = 无请求；空串 = 拆图直通）

    // 播放区间（trim 预览）：start 含 / end 不含，INT64_MAX = 无限制
    atomic_llong play_start_us;
    atomic_llong play_end_us;

    pthread_mutex_t surface_mu;
    pthread_cond_t surface_cond;
    ANativeWindow *window; // setSurface 持有的引用

    Clock clock;
    int64_t last_report_us; // 进度回报节流（渲染线程私有）

    PlayerCallbacks callbacks;
    void *user;
} PlayerContext;

PlayerContext *player_create(const PlayerCallbacks *callbacks, void *user);
/** 打开 fd（dup 所得、所有权转移给引擎）并启动工作线程；成功后回调 on_prepared。 */
int player_prepare(PlayerContext *ctx, int fd);
void player_set_window(PlayerContext *ctx, ANativeWindow *window); // 接管引用；NULL 解除
void player_start(PlayerContext *ctx);
void player_pause(PlayerContext *ctx);
/** 请求 seek 到指定毫秒（异步：由 demux 线程执行冲刷与跳转；clamp 到播放区间）。 */
void player_seek(PlayerContext *ctx, int64_t position_ms);

/** 设置预览滤镜链（空串/NULL = 直通）；由解码线程在下帧前重建。 */
void player_set_filter_graph(PlayerContext *ctx, const char *chain);

/** 设置播放区间（trim 预览）；end_ms < 0 表示无限制。seek 将被 clamp 到区间内。 */
void player_set_play_range(PlayerContext *ctx, int64_t start_ms, int64_t end_ms);
/** 停止全部线程并释放资源（含回调 on_destroy）；之后 ctx 由调用方释放。 */
void player_destroy(PlayerContext *ctx);
const char *player_state_name(PlayerState state);

void *demux_thread_func(void *arg);
void *decode_thread_func(void *arg);
void *audio_thread_func(void *arg);
void *render_thread_func(void *arg);

#endif
