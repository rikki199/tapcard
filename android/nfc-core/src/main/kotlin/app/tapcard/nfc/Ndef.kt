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

    /** One record of a message: its type name format, type and payload. */
    class Record internal constructor(val tnf: Int, val type: ByteArray, val payload: ByteArray)

    /** A link. Phones open it in the browser (iPhone shows a banner). */
    fun uriRecord(uri: String): Record {
        val (prefix, code) = URI_PREFIXES.firstOrNull { uri.startsWith(it.first, ignoreCase = true) } ?: ("" to 0x00)
        val rest = uri.substring(prefix.length).toByteArray(Charsets.UTF_8)
        return Record(TNF_WELL_KNOWN, "U".toByteArray(Charsets.US_ASCII), byteArrayOf(code.toByte()) + rest)
    }

    /** A file of the given MIME type, e.g. a text/vcard contact that Android opens in Contacts. */
    fun mimeRecord(mimeType: String, payload: ByteArray): Record =
        Record(TNF_MIME, mimeType.toByteArray(Charsets.US_ASCII), payload)

    /** A message made of the given records, in order. Android acts on the first record. */
    fun message(vararg records: Record): ByteArray {
        require(records.isNotEmpty()) { "An NDEF message needs at least one record" }
        val out = java.io.ByteArrayOutputStream()
        records.forEachIndexed { i, r ->
            require(r.type.size <= 255) { "NDEF type too long" }
            val short = r.payload.size < 256
            var header = r.tnf
            if (i == 0) header = header or FLAG_MB
            if (i == records.lastIndex) header = header or FLAG_ME
            if (short) header = header or FLAG_SR
            out.write(header)
            out.write(r.type.size)
            if (short) {
                out.write(r.payload.size)
            } else {
                val n = r.payload.size
                out.write(n ushr 24 and 0xFF); out.write(n ushr 16 and 0xFF); out.write(n ushr 8 and 0xFF); out.write(n and 0xFF)
            }
            out.write(r.type)
            out.write(r.payload)
        }
        return out.toByteArray()
    }

    /** A one-record message holding a link. */
    fun uriMessage(uri: String): ByteArray = message(uriRecord(uri))

    /** A one-record message holding a file of the given MIME type. */
    fun mimeMessage(mimeType: String, payload: ByteArray): ByteArray = message(mimeRecord(mimeType, payload))
}
