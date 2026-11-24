package app.tapcard

import android.content.Context
import android.util.Base64

/**
 * Holds the NDEF message the phone serves when tapped. Saved to disk so taps keep
 * working after the app is closed (Android routes taps to CardHceService on its own).
 */
object CardStore {
    private const val PREFS = "tapcard"
    private const val KEY = "ndef"

    @Volatile private var cached: ByteArray? = null

    fun message(context: Context): ByteArray? {
        cached?.let { return it }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return Base64.decode(saved, Base64.NO_WRAP).also { cached = it }
    }

    fun update(context: Context, message: ByteArray?) {
        cached = message
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (message == null) remove(KEY) else putString(KEY, Base64.encodeToString(message, Base64.NO_WRAP))
        }.apply()
    }
}
