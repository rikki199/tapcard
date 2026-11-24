package app.tapcard.nfc

/**
 * Emulates a read-only NFC Forum Type 4 Tag holding one NDEF message.
 *
 * A phone that reads NFC tags (Android, or iPhone XS and newer) talks to this
 * through ISO 7816-4 commands (APDUs):
 *   1. SELECT the NDEF tag application (AID D2760000850101)
 *   2. SELECT the capability container file (E103) and READ BINARY it
 *   3. SELECT the NDEF file (E104), READ BINARY its 2-byte length, then the message
 *
 * Create a new instance for each tap (each time the reader connects).
 */
class Type4Tag(ndefMessage: ByteArray) {

    private val ndefFile: ByteArray
    private val ccFile: ByteArray
    private var appSelected = false
    private var selected: ByteArray? = null

    init {
        require(ndefMessage.size <= MAX_NDEF_SIZE - 2) { "NDEF message too large: ${ndefMessage.size} bytes" }
        ndefFile = byteArrayOf((ndefMessage.size ushr 8).toByte(), ndefMessage.size.toByte()) + ndefMessage
        val maxFile = ndefFile.size
        ccFile = byteArrayOf(
            0x00, 0x0F,                                   // CC length: 15 bytes
            0x20,                                         // mapping version 2.0
            0x00, 0x3B,                                   // MLe: max bytes per READ BINARY response
            0x00, 0x34,                                   // MLc: max bytes per UPDATE BINARY command
            0x04, 0x06,                                   // NDEF File Control TLV (tag 04, length 6)
            0xE1.toByte(), 0x04,                          //   NDEF file id
            (maxFile ushr 8).toByte(), maxFile.toByte(),  //   max NDEF file size
            0x00,                                         //   read access: granted
            0xFF.toByte(),                                //   write access: denied
        )
    }

    /** Handles one command APDU and returns the response APDU (data + status word). */
    fun process(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_WRONG_LENGTH
        val cla = apdu[0].toInt() and 0xFF
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        if (cla != 0x00) return SW_CLA_NOT_SUPPORTED

        return when (ins) {
            INS_SELECT -> select(apdu, p1)
            INS_READ_BINARY -> readBinary(apdu, (p1 shl 8) or p2)
            INS_UPDATE_BINARY -> SW_SECURITY_NOT_SATISFIED // read-only tag
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(apdu: ByteArray, p1: Int): ByteArray {
        val data = commandData(apdu) ?: return SW_WRONG_LENGTH
        return when (p1) {
            0x04 -> { // select by application id
                if (data.contentEquals(NDEF_AID)) {
                    appSelected = true; selected = null; SW_OK
                } else SW_FILE_NOT_FOUND
            }
            0x00 -> { // select by file id
                if (!appSelected) return SW_FILE_NOT_FOUND
                selected = when {
                    data.contentEquals(CC_FILE_ID) -> ccFile
                    data.contentEquals(NDEF_FILE_ID) -> ndefFile
                    else -> return SW_FILE_NOT_FOUND
                }
                SW_OK
            }
            else -> SW_WRONG_P1P2
        }
    }

    private fun readBinary(apdu: ByteArray, offset: Int): ByteArray {
        val file = selected ?: return SW_FILE_NOT_FOUND
        if (offset > file.size) return SW_WRONG_P1P2
        // Le is the last byte for a short APDU; 0 means 256.
        val le = if (apdu.size >= 5) (apdu[apdu.size - 1].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        val end = minOf(file.size, offset + le)
        return file.copyOfRange(offset, end) + SW_OK
    }

    /** Extracts the data field of a short command APDU: CLA INS P1 P2 Lc data [Le]. */
    private fun commandData(apdu: ByteArray): ByteArray? {
        if (apdu.size < 5) return null
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return null
        return apdu.copyOfRange(5, 5 + lc)
    }

    companion object {
        /** The NDEF Tag Application id that readers select first. Register this in apduservice.xml. */
        const val NDEF_AID_HEX = "D2760000850101"

        const val MAX_NDEF_SIZE = 0x7FFF

        private const val INS_SELECT = 0xA4
        private const val INS_READ_BINARY = 0xB0
        private const val INS_UPDATE_BINARY = 0xD6

        val NDEF_AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
        private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)
        val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)
        val SW_SECURITY_NOT_SATISFIED = byteArrayOf(0x69, 0x82.toByte())
    }
}
