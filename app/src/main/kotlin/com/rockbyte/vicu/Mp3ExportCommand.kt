package com.rockbyte.vicu

object Mp3ExportCommand {

    fun build(inputUrl: String, outputUrl: String): Array<String> = arrayOf(
        "-hide_banner",
        "-i",
        inputUrl,
        "-map",
        "0:a:0",
        "-vn",
        "-c:a",
        "libmp3lame",
        "-b:a",
        "192k",
        "-f",
        "mp3",
        outputUrl
    )
}
