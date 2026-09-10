package com.rockbyte.vicu.repo

/** FFmpeg 音频导出命令构建（纯函数，便于单测）。 */
object AudioExportCommand {

    fun build(
        inputUrl: String,
        outputUrl: String,
        format: AudioExportFormat,
        target: EncodeTarget,
    ): Array<String> {
        val encoderArgs = when (target) {
            EncodeTarget.Copy -> arrayOf("-c:a", "copy")
            is EncodeTarget.Bitrate -> when (format) {
                AudioExportFormat.MP3 -> arrayOf("-c:a", "libmp3lame", "-b:a", "${target.kbps}k")
                AudioExportFormat.ORIGINAL, AudioExportFormat.M4A ->
                    arrayOf("-c:a", "aac", "-b:a", "${target.kbps}k")
            }
        }
        val muxer = when (format) {
            AudioExportFormat.MP3 -> "mp3"
            // ffkitsaf: 输出 URL 无扩展名可推断，需显式指定 m4a 复用器
            AudioExportFormat.ORIGINAL, AudioExportFormat.M4A -> "ipod"
        }
        return arrayOf(
            "-hide_banner",
            "-i",
            inputUrl,
            "-map",
            "0:a:0",
            "-vn",
            *encoderArgs,
            "-f",
            muxer,
            outputUrl,
        )
    }
}
