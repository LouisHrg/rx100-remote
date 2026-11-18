package fr.rx100remote.protocol

import java.io.EOFException
import java.io.InputStream
import java.io.PushbackInputStream

class LiveviewFrame(val sequence: Int, val timestamp: Long, val jpeg: ByteArray)

/**
 * Parser for the Sony liveview stream:
 *   common header  (8 bytes)  : 0xFF, payload type, sequence (u16 BE), timestamp (u32 BE)
 *   payload header (128 bytes): 0x24 0x35 0x68 0x79, data size (u24 BE), padding size (u8), reserved
 *   payload data, then padding.
 * Corrupted bytes are skipped by scanning for the next 0xFF + valid start code.
 * Partial reads are handled by readFully. Not thread-safe; one reader thread.
 */
class LiveviewParser(input: InputStream) {
    private val input = PushbackInputStream(input, HEADER_SIZE)
    var resyncs = 0
        private set

    /** Returns the next JPEG frame, skipping non-image payloads. Throws EOFException at end of stream. */
    fun nextFrame(): LiveviewFrame {
        while (true) {
            if (readByte() != 0xFF) { resyncs++; continue }
            val header = ByteArray(HEADER_SIZE - 1)
            readFully(header)
            val type = header[0].toInt() and 0xFF
            val validStart = START_CODE.indices.all { header[7 + it] == START_CODE[it] }
            val size = u(header, 11, 3)
            val padding = u(header, 14, 1)
            if (!validStart || type !in KNOWN_TYPES || size > MAX_PAYLOAD) {
                // Not a real frame: rescan from the byte right after this 0xFF.
                input.unread(header)
                resyncs++
                continue
            }
            val seq = u(header, 1, 2)
            val ts = u(header, 3, 4).toLong() and 0xFFFFFFFFL
            if (type != TYPE_IMAGE) {
                skipFully(size.toLong() + padding)
                continue
            }
            val jpeg = ByteArray(size)
            readFully(jpeg)
            skipFully(padding.toLong())
            if (size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) { resyncs++; continue }
            return LiveviewFrame(seq, ts, jpeg)
        }
    }

    private fun readByte(): Int = input.read().also { if (it < 0) throw EOFException() }

    private fun readFully(buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }

    private fun skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val n = input.skip(left)
            if (n <= 0) { readByte(); left-- } else left -= n
        }
    }

    private fun u(b: ByteArray, off: Int, len: Int): Int {
        var v = 0
        for (i in 0 until len) v = (v shl 8) or (b[off + i].toInt() and 0xFF)
        return v
    }

    companion object {
        const val HEADER_SIZE = 8 + 128
        const val TYPE_IMAGE = 0x01
        const val TYPE_FRAME_INFO = 0x02
        private val KNOWN_TYPES = setOf(TYPE_IMAGE, TYPE_FRAME_INFO)
        private const val MAX_PAYLOAD = 4 * 1024 * 1024 // liveview JPEGs are ~20-200 KB
        val START_CODE = byteArrayOf(0x24, 0x35, 0x68, 0x79)
    }
}
