package app.tapcard

import android.os.SystemClock

/** What happened on the most recent tap, so the app can tell the person why a tap didn't work. */
object TapLog {
    /** NO_CARD, or a Type4Tag.Progress name: APP_SELECTED, NDEF_SELECTED, MESSAGE_READ. */
    @Volatile var stage: String = ""
        private set
    @Volatile private var at: Long = 0
    /** Every NFC command another phone has sent to Tap Card since the app started. */
    @Volatile var commands: Int = 0
        private set

    fun record(stage: String) {
        val now = SystemClock.elapsedRealtime()
        commands++
        // Readers often reconnect right after reading; don't let that hide a successful read.
        if (this.stage == "MESSAGE_READ" && stage != "MESSAGE_READ" && now - at < 4000) return
        this.stage = stage; at = now
    }

    /** Seconds since the last tap, or -1 if there hasn't been one since the app started. */
    fun secondsAgo(): Long = if (at == 0L) -1 else (SystemClock.elapsedRealtime() - at) / 1000
}
