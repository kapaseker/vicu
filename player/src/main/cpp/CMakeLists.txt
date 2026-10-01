cmake_minimum_required(VERSION 3.22.1)
project(vicuplayer C)

# jniLibs 中预构建 FFmpeg 库目录（AGP 负责 jniLibs 打包，此处仅用于链接）
set(PREBUILT_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
# 预构建库配套头文件（与 ffmpeg-kit-next 构建输出同步）
set(FFMPEG_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/ffmpeg/include)

# 预构建 libav* 动态库（IMPORTED）
foreach(avlib avutil swresample swscale avcodec avformat avfilter)
    add_library(${avlib} SHARED IMPORTED)
    set_target_properties(${avlib} PROPERTIES
            IMPORTED_LOCATION ${PREBUILT_DIR}/lib${avlib}.so)
endforeach()

# 播放器引擎：解封装 → 解码 → [avfilter 插槽] → A/V 同步 → EGL 渲染
add_library(vicuplayer SHARED
        player/jni_bridge.c
        player/player_core.c
        player/demuxer.c
        player/video_decoder.c
        player/audio_decoder.c
        player/filter_graph.c
        player/frame_upload.c
        player/renderer.c)

target_include_directories(vicuplayer PRIVATE ${FFMPEG_INCLUDE_DIR})

target_link_libraries(vicuplayer
        avformat
        avcodec
        avutil
        swresample
        swscale
        avfilter
        android
        log
        EGL
        GLESv2)

if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_executable(frame_upload_test
            ../../test/cpp/frame_upload_test.c
            player/frame_upload.c)
    target_include_directories(frame_upload_test PRIVATE ${FFMPEG_INCLUDE_DIR})
    target_link_libraries(frame_upload_test avutil)
endif()
