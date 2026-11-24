package app.tapcard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.Intents.Insert
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import app.tapcard.nfc.Ndef
import org.json.JSONObject

/**
 * Shows the Tap Card screens (bundled from the web/ folder) and connects them to
 * the phone's NFC through the `TapCardNative` JavaScript bridge.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val nfc: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var tapMode = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "TapCardNative")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    if (url.scheme == "file") return false // the app's own pages
                    openExternal(url); return true         // websites, tel:, mailto:, maps
                }
            }
        }
        // Android 15 draws apps edge to edge: keep content clear of the status bar, nav bar and keyboard.
        web.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            }
            insets
        }
        setContentView(web)
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("file:///android_asset/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (tapMode) startListening()
    }

    override fun onPause() {
        // Android requires releasing the preferred service and reader mode when the screen leaves.
        if (tapMode) stopListening()
        super.onPause()
    }

    @Deprecated("Needed for Android versions before 13")
    override fun onBackPressed() {
        web.evaluateJavascript("window.tapcardBack ? window.tapcardBack() : false") { handled ->
            if (handled != "true") {
                @Suppress("DEPRECATION") super.onBackPressed()
            }
        }
    }

    /**
     * While tap mode is on, Android sends taps to Tap Card first, even if another app
     * also answers NFC tag reads, and the screen stays on.
     *
     * Don't call enableReaderMode() here: Android stops routing taps to card-emulation
     * services (CardHceService) while any app has reader mode on, so the phone could no
     * longer be read as a tag.
     */
    private fun startListening() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val adapter = nfc ?: return
        runCatching {
            CardEmulation.getInstance(adapter)
                .setPreferredService(this, ComponentName(this, CardHceService::class.java))
        }
    }

    private fun stopListening() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val adapter = nfc ?: return
        runCatching { CardEmulation.getInstance(adapter).unsetPreferredService(this) }
    }

    private fun openExternal(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (e: ActivityNotFoundException) { toast("No app can open this link") }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Methods the web page calls as `TapCardNative.xxx()`. They run on a background thread. */
    private inner class Bridge {

        @JavascriptInterface
        fun hceSupported(): Boolean =
            nfc != null && packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

        @JavascriptInterface
        fun nfcEnabled(): Boolean = nfc?.isEnabled == true

        @JavascriptInterface
        fun openNfcSettings() = runOnUiThread {
            try { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            catch (e: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
        }

        /**
         * What a tap sends. With a web address, a link to your card page (works on iPhone and Android).
         * Without one, the contact itself as a vCard (Android phones import it; iPhones ignore it).
         */
        @JavascriptInterface
        fun setCard(url: String, vcard: String) {
            val message = when {
                url.isNotBlank() -> Ndef.uriMessage(url)
                vcard.isNotBlank() -> Ndef.mimeMessage("text/vcard", vcard.toByteArray(Charsets.UTF_8))
                else -> null
            }
            CardStore.update(this@MainActivity, message)
        }

        @JavascriptInterface
        fun enterTapMode() = runOnUiThread { tapMode = true; startListening() }

        @JavascriptInterface
        fun exitTapMode() = runOnUiThread { tapMode = false; stopListening() }

        @JavascriptInterface
        fun share(text: String) = runOnUiThread {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(send, null))
        }

        /** Opens the phone's "new contact" screen filled in with the card's details. */
        @JavascriptInterface
        fun saveContact(json: String) = runOnUiThread {
            val p = runCatching { JSONObject(json) }.getOrNull() ?: return@runOnUiThread
            fun s(k: String) = p.optString(k).trim()
            val intent = Intent(Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                s("n").takeIf { it.isNotEmpty() }?.let { putExtra(Insert.NAME, it) }
                s("p").takeIf { it.isNotEmpty() }?.let { putExtra(Insert.PHONE, it) }
                s("e").takeIf { it.isNotEmpty() }?.let { putExtra(Insert.EMAIL, it) }
                s("o").takeIf { it.isNotEmpty() }?.let { putExtra(Insert.COMPANY, it) }
                s("t").takeIf { it.isNotEmpty() }?.let { putExtra(Insert.JOB_TITLE, it) }
                s("a").takeIf { it.isNotEmpty() }?.let { putExtra(Insert.POSTAL, it) }
                s("b").takeIf { it.isNotEmpty() }?.let { putExtra(Insert.NOTES, it) }
                val sites = ArrayList<ContentValues>()
                fun site(url: String) = sites.add(ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Website.URL, url)
                })
                s("w").takeIf { it.isNotEmpty() }?.let { site(it) }
                s("li").takeIf { it.isNotEmpty() }?.let { site("https://www.linkedin.com/in/" + it.substringAfterLast("/in/").trim('/', '@')) }
                s("ig").takeIf { it.isNotEmpty() }?.let { site("https://instagram.com/" + it.substringAfterLast("/").trim('@')) }
                if (sites.isNotEmpty()) putParcelableArrayListExtra(Insert.DATA, sites)
            }
            try { startActivity(intent) } catch (e: ActivityNotFoundException) { toast("No contacts app found") }
        }
    }
}
