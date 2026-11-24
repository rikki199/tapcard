package app.tapcard.nfc

/**
 * Builds NDEF messages (the data format NFC tags use) without any Android dependency,
 * so the bytes can be unit-tested on a normal JVM.
 */
object Ndef {
    private const val TNF_WELL_KNOWN = 0x01
    private const val TNF_MIME = 0x02

    private const val FLAG_MB = 0x80 // message begin
    private const val FLAG_ME = 0x40 // message end
    private const val FLAG_SR = 0x10 // short record (payload < 256 bytes)

    // URI identifier codes from the NFC Forum URI Record Type Definition.
    private val URI_PREFIXES = listOf(
        "https://www." to 0x02,
        "http://www." to 0x01,
        "https://" to 0x04,
        "http://" to 0x03,
    )

    /** A one-record message holding a link. Phones open it in the browser (iPhone shows a banner). */
    fun uriMessage(uri: String): ByteArray {
        val (prefix, code) = URI_PREFIXES.firstOrNull { uri.startsWith(it.first, ignoreCase = true) } ?: ("" to 0x00)
        val rest = uri.substring(prefix.length).toByteArray(Charsets.UTF_8)
        val payload = byteArrayOf(code.toByte()) + rest
        return record(TNF_WELL_KNOWN, "U".toByteArray(Charsets.US_ASCII), payload)
    }

    /** A one-record message holding a file of the given MIME type, e.g. a text/vcard contact. */
    fun mimeMessage(mimeType: String, payload: ByteArray): ByteArray =
        record(TNF_MIME, mimeType.toByteArray(Charsets.US_ASCII), payload)

    private fun record(tnf: Int, type: ByteArray, payload: ByteArray): ByteArray {
        require(type.size <= 255) { "NDEF type too long" }
        val short = payload.size < 256
        val out = java.io.ByteArrayOutputStream()
        out.write(FLAG_MB or FLAG_ME or (if (short) FLAG_SR else 0) or tnf)
        out.write(type.size)
        if (short) {
            out.write(payload.size)
        } else {
            val n = payload.size
            out.write(n ushr 24 and 0xFF); out.write(n ushr 16 and 0xFF); out.write(n ushr 8 and 0xFF); out.write(n and 0xFF)
        }
        out.write(type)
        out.write(payload)
        return out.toByteArray()
    }
}
