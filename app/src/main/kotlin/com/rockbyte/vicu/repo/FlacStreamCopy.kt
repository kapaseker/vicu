package com.rockbyte.vicu.repo

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** RFC 9639 sections 8.2 and 9: only numbering, CRCs and STREAMINFO change; subframes stay byte-identical. */
internal data class FlacFrameCopy(val bytes: ByteArray, val samples: Int)

internal fun normalizeFlacFrame(frame: ByteArray, frameIndex: Long, sampleIndex: Long): FlacFrameCopy {
    require(frame.size >= 8 && frame[0].toInt() and 255 == 255 && frame[1].toInt() and 254 == 248)
    require(flacCrc16(frame) == 0) { "Invalid FLAC frame CRC" }
    val first = frame[4].toInt() and 255
    val numberLength = when {
        first < 128 -> 1
        first in 0xc0..0xdf -> 2
        first in 0xe0..0xef -> 3
        first in 0xf0..0xf7 -> 4
        first in 0xf8..0xfb -> 5
        first in 0xfc..0xfd -> 6
        first == 0xfe -> 7
        else -> error("Invalid FLAC coded number")
    }
    require(4 + numberLength < frame.size - 2)
    for (i in 5 until 4 + numberLength) require(frame[i].toInt() and 192 == 128)
    var extraEnd = 4 + numberLength
    fun unsignedByte(): Int { require(extraEnd < frame.size - 2); return frame[extraEnd++].toInt() and 255 }
    val blockCode = frame[2].toInt() ushr 4 and 15
    val samples = when (blockCode) {
        0 -> error("Reserved FLAC block size")
        1 -> 192
        in 2..5 -> 576 shl (blockCode - 2)
        6 -> unsignedByte() + 1
        7 -> ((unsignedByte() shl 8) or unsignedByte()) + 1
        else -> 256 shl (blockCode - 8)
    }
    when (frame[2].toInt() and 15) {
        12 -> unsignedByte()
        13, 14 -> { unsignedByte(); unsignedByte() }
        15 -> error("Reserved FLAC sample rate")
    }
    require(extraEnd < frame.size - 2)
    val oldHeader = frame.copyOfRange(0, extraEnd + 1)
    require(flacCrc8(oldHeader) == 0) { "Invalid FLAC header CRC" }
    val variable = frame[1].toInt() and 1 != 0
    require(variable || frameIndex <= 0x7fffffff)
    val header = frame.copyOfRange(0, 4) + flacNumber(if (variable) sampleIndex else frameIndex) +
        frame.copyOfRange(4 + numberLength, extraEnd)
    val body = header + flacCrc8(header).toByte() + frame.copyOfRange(extraEnd + 1, frame.size - 2)
    val crc = flacCrc16(body)
    return FlacFrameCopy(body + byteArrayOf((crc shr 8).toByte(), crc.toByte()), samples)
}

internal fun flacNumber(number: Long): ByteArray {
    require(number in 0..0xfffffffffL)
    val length = when {
        number <= 0x7f -> 1
        number <= 0x7ff -> 2
        number <= 0xffff -> 3
        number <= 0x1fffff -> 4
        number <= 0x3ffffff -> 5
        number <= 0x7fffffff -> 6
        else -> 7
    }
    if (length == 1) return byteArrayOf(number.toByte())
    val result = ByteArray(length)
    var value = number
    for (i in length - 1 downTo 1) { result[i] = (0x80 or (value.toInt() and 63)).toByte(); value = value ushr 6 }
    result[0] = ((0xff shl (8 - length)) or value.toInt()).toByte()
    return result
}

internal fun flacCrc8(bytes: ByteArray): Int = flacCrc(bytes, 8, 0x07)
internal fun flacCrc16(bytes: ByteArray): Int = flacCrc(bytes, 16, 0x8005)
private fun flacCrc(bytes: ByteArray, bits: Int, polynomial: Int): Int {
    var crc = 0
    val highBit = 1 shl (bits - 1)
    val mask = (1 shl bits) - 1
    for (byte in bytes) {
        crc = crc xor ((byte.toInt() and 255) shl (bits - 8))
        repeat(8) { crc = ((crc shl 1) xor if (crc and highBit != 0) polynomial else 0) and mask }
    }
    return crc
}

/** Bounded memory (one encoded packet), with output seekable for the finalized STREAMINFO. */
internal fun normalizeFlacStream(source: SeekableByteChannel, packets: List<AudioPacketInfo>, target: SeekableByteChannel) {
    require(packets.isNotEmpty())
    fun read(size: Int): ByteArray {
        require(size in 0..0xffffff)
        val buffer = ByteBuffer.allocate(size)
        while (buffer.hasRemaining()) check(source.read(buffer) > 0) { "Truncated FLAC output" }
        return buffer.array()
    }
    fun write(bytes: ByteArray) {
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) check(target.write(buffer) > 0)
    }
    source.position(0)
    val magic = read(4)
    require(magic.contentEquals("fLaC".toByteArray(Charsets.US_ASCII)))
    write(magic)
    var streamInfo: ByteArray? = null
    var last = false
    while (!last) {
        val header = read(4)
        val type = header[0].toInt() and 127
        last = header[0].toInt() and 128 != 0
        val size = ((header[1].toInt() and 255) shl 16) or ((header[2].toInt() and 255) shl 8) or (header[3].toInt() and 255)
        val data = read(size)
        if (streamInfo == null) { require(type == 0 && size == 34); streamInfo = data }
        // FFmpeg's raw FLAC muxer emits STREAMINFO, comments and padding, never stale seek tables.
        require(type != 3) { "Unexpected FLAC seek table" }
        write(header); write(data)
    }
    var samples = 0L
    var minFrameSize = Int.MAX_VALUE
    var maxFrameSize = 0
    var nextPosition = source.position()
    for ((index, packet) in packets.withIndex()) {
        require(packet.position == nextPosition) { "Non-contiguous FLAC packets" }
        source.position(packet.position)
        val frame = normalizeFlacFrame(read(packet.size), index.toLong(), samples)
        write(frame.bytes)
        samples += frame.samples
        minFrameSize = minOf(minFrameSize, frame.bytes.size)
        maxFrameSize = maxOf(maxFrameSize, frame.bytes.size)
        nextPosition = source.position()
    }
    require(samples in 1..0xfffffffffL)
    val info = checkNotNull(streamInfo)
    for (i in 0..2) {
        info[4+i] = (minFrameSize shr ((2-i)*8)).toByte()
        info[7+i] = (maxFrameSize shr ((2-i)*8)).toByte()
    }
    info[13] = ((info[13].toInt() and 240) or (samples ushr 32).toInt()).toByte()
    for (i in 0..3) info[14+i] = (samples ushr ((3-i)*8)).toByte()
    info.fill(0, 18, 34) // Original full-file MD5 is obsolete; all-zero means checksum unavailable.
    target.position(8)
    write(info)
}
