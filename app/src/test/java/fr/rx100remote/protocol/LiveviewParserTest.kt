package fr.rx100remote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

class LiveviewParserTest {

    private fun jpeg(n: Int, fill: Int) = ByteArray(n) { (fill + it).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }

    private fun frame(type: Int, seq: Int, ts: Long, data: ByteArray, padding: Int = 0, startCode: ByteArray = LiveviewParser.START_CODE): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF); out.write(type)
        out.write(seq shr 8); out.write(seq)
        for (s in intArrayOf(24, 16, 8, 0)) out.write((ts shr s).toInt())
        out.write(startCode)
        out.write(data.size shr 16); out.write(data.size shr 8); out.write(data.size)
        out.write(padding)
        out.write(ByteArray(120))
        out.write(data)
        out.write(ByteArray(padding))
        return out.toByteArray()
    }

    /** Delivers at most [chunk] bytes per read() to exercise partial reads. */
    private class Trickle(bytes: ByteArray, val chunk: Int) : InputStream() {
        val src = ByteArrayInputStream(bytes)
        override fun read() = src.read()
        override fun read(b: ByteArray, off: Int, len: Int) = src.read(b, off, minOf(len, chunk))
        override fun skip(n: Long) = src.skip(minOf(n, chunk.toLong()))
    }

    private fun readAll(p: LiveviewParser): List<LiveviewFrame> {
        val list = mutableListOf<LiveviewFrame>()
        try { while (true) list += p.nextFrame() } catch (_: EOFException) {}
        return list
    }

    @Test fun parsesConsecutiveFramesWithPadding() {
        val a = jpeg(500, 1); val b = jpeg(1000, 7)
        val frames = readAll(LiveviewParser(ByteArrayInputStream(frame(1, 1, 100, a, 4) + frame(1, 2, 0xFFFFFFF0L, b, 0))))
        assertEquals(2, frames.size)
        assertArrayEquals(a, frames[0].jpeg)
        assertEquals(1, frames[0].sequence)
        assertEquals(100L, frames[0].timestamp)
        assertArrayEquals(b, frames[1].jpeg)
        assertEquals(0xFFFFFFF0L, frames[1].timestamp)
    }

    @Test fun handlesPartialReads() {
        val a = jpeg(3000, 3)
        for (chunk in listOf(1, 2, 7, 137)) {
            val frames = readAll(LiveviewParser(Trickle(frame(1, 5, 1, a, 3) + frame(1, 6, 2, a, 0), chunk)))
            assertEquals("chunk=$chunk", 2, frames.size)
            assertArrayEquals(a, frames[1].jpeg)
        }
    }

    @Test fun skipsFrameInfoPayloads() {
        val a = jpeg(200, 9)
        val frames = readAll(LiveviewParser(ByteArrayInputStream(frame(2, 1, 0, ByteArray(64) { 0xFF.toByte() }, 8) + frame(1, 2, 0, a))))
        assertEquals(1, frames.size)
        assertEquals(2, frames[0].sequence)
    }

    @Test fun resyncsAfterGarbageAndFakeHeaders() {
        val a = jpeg(300, 2)
        val garbage = byteArrayOf(0x00, 0xFF.toByte(), 0x01, 0x12, 0xFF.toByte(), 0xFF.toByte(), 0x24, 0x35)
        val badStart = frame(1, 9, 0, a, startCode = byteArrayOf(0x24, 0x35, 0x68, 0x00))
        val stream = garbage + badStart + frame(1, 10, 0, a) + ByteArray(50) { 0xFF.toByte() } + frame(1, 11, 0, a)
        val parser = LiveviewParser(Trickle(stream, 5))
        val frames = readAll(parser)
        assertEquals(listOf(10, 11), frames.map { it.sequence })
        assertTrue(parser.resyncs > 0)
    }

    @Test fun truncatedStreamEndsWithEof() {
        val full = frame(1, 1, 0, jpeg(400, 1))
        val frames = readAll(LiveviewParser(ByteArrayInputStream(full + full.copyOf(200))))
        assertEquals(1, frames.size)
    }

    @Test fun dropsPayloadThatIsNotJpeg() {
        val notJpeg = ByteArray(100)
        val frames = readAll(LiveviewParser(ByteArrayInputStream(frame(1, 1, 0, notJpeg) + frame(1, 2, 0, jpeg(10, 0)))))
        assertEquals(listOf(2), frames.map { it.sequence })
    }
}
