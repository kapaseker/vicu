#include "../../main/cpp/player/frame_upload.h"

#include <assert.h>
#include <libavutil/frame.h>
#include <string.h>

static void check_frame(FrameUploadBuffer *upload, int width, int height) {
    AVFrame *frame = av_frame_alloc();
    assert(frame);
    frame->format = AV_PIX_FMT_YUV420P;
    frame->width = width;
    frame->height = height;
    assert(av_frame_get_buffer(frame, 32) == 0);

    int plane_width[] = {width, (width + 1) / 2, (width + 1) / 2};
    int plane_height[] = {height, (height + 1) / 2, (height + 1) / 2};
    for (int plane = 0; plane < 3; plane++) {
        for (int row = 0; row < plane_height[plane]; row++) {
            memset(frame->data[plane] + row * frame->linesize[plane],
                   0xee, frame->linesize[plane]);
            for (int col = 0; col < plane_width[plane]; col++) {
                frame->data[plane][row * frame->linesize[plane] + col] =
                        (uint8_t) (plane * 70 + row * 7 + col);
            }
        }
    }

    assert(frame_upload_prepare(upload, frame) == 0);
    assert(upload->planes[1] == upload->planes[0] + width * height);
    assert(upload->planes[2] == upload->planes[1] + plane_width[1] * plane_height[1]);
    for (int plane = 0; plane < 3; plane++) {
        for (int row = 0; row < plane_height[plane]; row++) {
            for (int col = 0; col < plane_width[plane]; col++) {
                assert(upload->planes[plane][row * plane_width[plane] + col] ==
                       (uint8_t) (plane * 70 + row * 7 + col));
            }
        }
    }
    av_frame_free(&frame);
}

int main(void) {
    FrameUploadBuffer upload = {0};
    check_frame(&upload, 556, 268);
    check_frame(&upload, 734, 416);
    int capacity = upload.capacity;
    check_frame(&upload, 5, 3);
    assert(upload.capacity == capacity);
    frame_upload_release(&upload);
    return 0;
}
