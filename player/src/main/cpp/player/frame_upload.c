#include "frame_upload.h"

#include <errno.h>
#include <libavutil/error.h>
#include <libavutil/imgutils.h>
#include <stdlib.h>
#include <string.h>

int frame_upload_prepare(FrameUploadBuffer *upload, const AVFrame *frame) {
    if (!upload || !frame || frame->format != AV_PIX_FMT_YUV420P) return AVERROR(EINVAL);
    int size = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, frame->width, frame->height, 1);
    if (size < 0) return size;
    if (size > upload->capacity) {
        uint8_t *data = realloc(upload->data, size);
        if (!data) return AVERROR(ENOMEM);
        upload->data = data;
        upload->capacity = size;
    }
    int rc = av_image_copy_to_buffer(upload->data, upload->capacity,
                                     (const uint8_t *const *) frame->data, frame->linesize,
                                     AV_PIX_FMT_YUV420P, frame->width, frame->height, 1);
    if (rc < 0) return rc;
    int linesize[4];
    rc = av_image_fill_arrays(upload->planes, linesize, upload->data,
                              AV_PIX_FMT_YUV420P, frame->width, frame->height, 1);
    return rc < 0 ? rc : 0;
}

void frame_upload_release(FrameUploadBuffer *upload) {
    free(upload->data);
    memset(upload, 0, sizeof(*upload));
}
