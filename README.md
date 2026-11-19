# PhoneBridge for Android

PhoneBridge transfers files directly between Android phones on the same trusted Wi-Fi network. The sender chooses exactly one folder to share; the controller connects using the sender's LAN address and pairing token.

## APK variants

GitHub Actions builds two installable variants:
- **Controller** — connect to a target phone, browse its shared folder, upload, download, rename and delete items.
- **Target** — select a folder, start the local sharing server and display the pairing token.

## Security and prototype status

This is an early prototype. Transfers use local HTTP, not TLS. Use only a trusted private Wi-Fi network and never expose port 8765 to the internet. Keep the app open while sharing. Android storage providers may differ in their support for rename/delete. Test with non-sensitive sample files first.

## Build locally

Use JDK 17 and Android SDK 35, then run `./gradlew assembleControllerDebug assembleTargetDebug`.

## GitHub Actions

Pushes to `main` build both APK variants and publish them as separate downloadable workflow artifacts. See the Actions tab for the latest successful run.

CI uses Android SDK platform 35 and build-tools 35.0.0.
