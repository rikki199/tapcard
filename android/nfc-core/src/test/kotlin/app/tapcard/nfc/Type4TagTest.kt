package app.tapcard.nfc

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Type4TagTest {

    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.sw() = takeLast(2).toByteArray()
    private fun ByteArray.data() = copyOfRange(0, size - 2)

    private val selectApp = hex("00 A4 04 00 07 D2760000850101 00")
    private val selectCc = hex("00 A4 00 0C 02 E103")
    private val selectNdef = hex("00 A4 00 0C 02 E104")
    private fun read(offset: Int, le: Int) = byteArrayOf(0x00, 0xB0.toByte(), (offset ushr 8).toByte(), offset.toByte(), le.toByte())

    /** Reads the tag exactly like a phone does, in chunks no bigger than the tag's MLe. */
    private fun readLikeAPhone(tag: Type4Tag): ByteArray {
        assertContentEquals(Type4Tag.SW_OK, tag.process(selectApp))
        assertContentEquals(Type4Tag.SW_OK, tag.process(selectCc))
        val cc = tag.process(read(0, 15))
        assertContentEquals(Type4Tag.SW_OK, cc.sw())
        val ccData = cc.data()
        assertEquals(15, ccData.size)
        assertEquals(0x20, ccData[2].toInt())                 // version 2.0
        val mle = ((ccData[3].toInt() and 0xFF) shl 8) or (ccData[4].toInt() and 0xFF)
        assertContentEquals(hex("E104"), ccData.copyOfRange(9, 11))
        assertEquals(0x00, ccData[13].toInt())                // readable
        assertEquals(0xFF, ccData[14].toInt() and 0xFF)       // not writable

        assertContentEquals(Type4Tag.SW_OK, tag.process(selectNdef))
        val nlenResp = tag.process(read(0, 2))
        val nlen = ((nlenResp[0].toInt() and 0xFF) shl 8) or (nlenResp[1].toInt() and 0xFF)
        val out = java.io.ByteArrayOutputStream()
        var off = 2
        while (off < 2 + nlen) {
            val chunk = minOf(mle, 2 + nlen - off)
            val r = tag.process(read(off, chunk))
            assertContentEquals(Type4Tag.SW_OK, r.sw())
            assertEquals(chunk, r.size - 2)
            out.write(r.data()); off += chunk
        }
        return out.toByteArray()
    }

    @Test fun shortUrlIsEncodedWithHttpsPrefix() {
        val msg = Ndef.uriMessage("https://example.com/#c=abc")
        // MB|ME|SR|well-known, type length 1, payload length, 'U', prefix 0x04 (https://), then the rest
        assertContentEquals(hex("D1 01 13 55 04") + "example.com/#c=abc".toByteArray(), msg)
    }

    @Test fun wwwPrefixIsUsed() {
        val msg = Ndef.uriMessage("https://www.acme.com/")
        assertEquals(0x02, msg[4].toInt())
        assertContentEquals("acme.com/".toByteArray(), msg.copyOfRange(5, msg.size))
    }

    @Test fun longRecordUsesFourByteLength() {
        val payload = ByteArray(300) { 'a'.code.toByte() }
        val msg = Ndef.mimeMessage("text/vcard", payload)
        assertEquals(0xC2, msg[0].toInt() and 0xFF)            // MB|ME, no SR, MIME
        assertEquals(10, msg[1].toInt())                         // "text/vcard"
        assertContentEquals(hex("0000012C"), msg.copyOfRange(2, 6))
        assertContentEquals("text/vcard".toByteArray(), msg.copyOfRange(6, 16))
        assertEquals(16 + 300, msg.size)
    }

    @Test fun phoneReadsBackTheExactUrlMessage() {
        val url = "https://someone.github.io/tapcard/#c=" + "x".repeat(280) // a realistic, multi-chunk card link
        val msg = Ndef.uriMessage(url)
        val tag = Type4Tag(msg)
        assertEquals(Type4Tag.Progress.NOTHING, tag.progress)
        assertContentEquals(msg, readLikeAPhone(tag))
        assertEquals(Type4Tag.Progress.MESSAGE_READ, tag.progress)
    }

    @Test fun progressStopsWhereTheReaderStopped() {
        val tag = Type4Tag(Ndef.uriMessage("https://a.b/"))
        tag.process(selectApp); assertEquals(Type4Tag.Progress.APP_SELECTED, tag.progress)
        tag.process(selectCc); tag.process(read(0, 15)); assertEquals(Type4Tag.Progress.APP_SELECTED, tag.progress)
        tag.process(selectNdef); tag.process(read(0, 2)); assertEquals(Type4Tag.Progress.NDEF_SELECTED, tag.progress)
    }

    @Test fun phoneReadsBackALargeVcard() {
        val vcard = ("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane Doe\r\nNOTE:" + "é".repeat(400) + "\r\nEND:VCARD\r\n").toByteArray()
        val msg = Ndef.mimeMessage("text/vcard", vcard)
        assertContentEquals(msg, readLikeAPhone(Type4Tag(msg)))
    }

    @Test fun selectWithoutLeAndWithFciAlsoWork() {
        val tag = Type4Tag(Ndef.uriMessage("https://a.b/"))
        assertContentEquals(Type4Tag.SW_OK, tag.process(hex("00 A4 04 00 07 D2760000850101")))
        assertContentEquals(Type4Tag.SW_OK, tag.process(hex("00 A4 00 00 02 E103")))
    }

    @Test fun rejectsUnknownAppsFilesAndWrites() {
        val tag = Type4Tag(Ndef.uriMessage("https://a.b/"))
        assertContentEquals(Type4Tag.SW_FILE_NOT_FOUND, tag.process(hex("00 A4 04 00 07 A0000000041010 00"))) // a payment AID
        assertContentEquals(Type4Tag.SW_FILE_NOT_FOUND, tag.process(selectNdef))   // file before app
        assertContentEquals(Type4Tag.SW_FILE_NOT_FOUND, tag.process(read(0, 2)))   // read before select
        tag.process(selectApp)
        assertContentEquals(Type4Tag.SW_FILE_NOT_FOUND, tag.process(hex("00 A4 00 0C 02 E105")))
        tag.process(selectNdef)
        assertContentEquals(Type4Tag.SW_SECURITY_NOT_SATISFIED, tag.process(hex("00 D6 00 00 02 0000")))
        assertContentEquals(Type4Tag.SW_WRONG_P1P2, tag.process(read(0x7000, 2)))
        assertContentEquals(Type4Tag.SW_INS_NOT_SUPPORTED, tag.process(hex("00 CA 00 00 00")))
        assertContentEquals(Type4Tag.SW_CLA_NOT_SUPPORTED, tag.process(hex("80 A4 04 00 00")))
        assertContentEquals(Type4Tag.SW_WRONG_LENGTH, tag.process(hex("00 A4")))
    }
}
