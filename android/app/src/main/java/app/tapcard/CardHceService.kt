package app.tapcard

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import app.tapcard.nfc.Type4Tag

/** Answers another phone's NFC reader as if this phone were an NFC tag holding your card. */
class CardHceService : HostApduService() {

    private var tag: Type4Tag? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val current = tag ?: CardStore.message(this)?.let { Type4Tag(it) }?.also { tag = it }
        if (current == null) { // no card set up yet
            TapLog.record("NO_CARD")
            return Type4Tag.SW_FILE_NOT_FOUND
        }
        val response = current.process(commandApdu)
        TapLog.record(current.progress.name)
        return response
    }

    override fun onDeactivated(reason: Int) {
        tag = null // start fresh on the next tap, with the latest card
    }
}
