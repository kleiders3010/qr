# QR: read and make QR codes, no ads, no account

An Android app that reads QR codes and barcodes and makes QR codes.

- **Scan** with the camera, or read a code from a picture or a shared screenshot.
- **See where a link goes before opening it.** The whole name of the place it goes is shown in bold (with its
  ASCII form when it has letters outside a-z), with warnings for: no https, text before an `@`, a bare number address
  in any notation, letters outside a-z, hidden characters, well-known short links and QR redirectors (the list is not
  complete), unusual ports, very long links. Only web links open from the app, and a link written in a way the strict
  check can't read doesn't open at all.
- **Each kind of code gets its own screen:** Wi-Fi (join, copy the password), contacts, calendar events, phone, text
  message, email, places, product numbers, plain text.
- **Make codes** for links, Wi-Fi, contacts, events, phone numbers, text messages, email and plain text. You can pick
  colours (with a contrast check) and put a picture in the middle. Every code made is read back before it is offered.
  Save it as PNG or SVG, or share it. Codes made here never expire and don't go through any server.
- **History** stays on the phone. You can search it, delete items, clear it, turn it off, or save it as a spreadsheet.
- English and Spanish.

Codes are read with [ZXing](https://github.com/zxing/zxing). For many more barcode formats, see
[Binary Eye](https://github.com/markusfisch/BinaryEye).

## Download
- Android: [Google Play](https://play.google.com/store/apps/details?id=com.jurisgames.qr) or the APK on the
  [releases page](https://github.com/kleiders3010/qr/releases)

## Build
- `cd app && ./gradlew :android:testDebugUnitTest :android:assembleDebug`

## Problems or ideas

In the app: Send feedback (in the menu). If the app doesn't open at all, open an issue at
https://github.com/kleiders3010/qr/issues or email hello@jurisgames.com.

## Privacy
[apps.jurisgames.com/privacy](https://apps.jurisgames.com/privacy/)

## License
Apache-2.0. Uses ZXing (Apache-2.0) and AndroidX CameraX (Apache-2.0).
