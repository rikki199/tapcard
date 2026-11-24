# Tap Card

Share your business card by tapping phones, an NFC sticker, or a QR code.

| Folder | What it is |
| --- | --- |
| `web/` | The website and installable web app. Works on any phone. Fill in your details, show your QR code, write NFC stickers (Chrome on Android). |
| `android/` | The Android app. Same screens, plus **Tap phones**: your phone acts as an NFC tag, so another phone (iPhone XS or newer, or Android) opens your card when the backs touch. |

## How sharing works

- Your details are packed into the link itself (`…/#c=…`). There is no server or account.
- **Tap phones (Android app):** with your Tap Card web address set under *Share*, a tap sends that link, which iPhones and Android phones both open. Without an address, a tap sends the contact file itself, which only Android phones accept.
- **QR code:** holds your link, or the contact itself when there is no web address.
- **iPhone as the sender:** Apple doesn't let apps share over NFC. iPhone users show the QR code instead.

## Put the website online (GitHub Pages)

1. Push this folder to a GitHub repository.
2. In the repository: **Settings → Pages → Source: GitHub Actions**.
3. The *Publish website* workflow publishes `web/` to `https://<user>.github.io/<repo>/`.

## Get the Android app

- **Download:** on your Android phone open `https://github.com/<user>/<repo>/releases/latest/download/tapcard.apk`, then open the file. Android asks you to allow installs from your browser once. Every push to `main` rebuilds it.
- **Android Studio:** open the `android/` folder and press Run.

Then in the app: fill in your details, enter your web address under *Share*, and press **Tap phones**.

## Tests

`cd android/nfc-core && ../gradlew test` checks the NFC tag emulation (the commands a reading phone sends, and the bytes sent back) without needing the Android SDK.
