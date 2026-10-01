#ifndef VICU_FRAME_UPLOAD_H
#define VICU_FRAME_UPLOAD_H

#include <libavutil/frame.h>

typedef struct FrameUploadBuffer {
    uint8_t *data;
    uint8_t *planes[4];
    int capacity;
} FrameUploadBuffer;

int frame_upload_prepare(FrameUploadBuffer *upload, const AVFrame *frame);
void frame_upload_release(FrameUploadBuffer *upload);

#endif
