# CallShare Android MVP

GitHub Actions-ready Android prototype for a single Sender/Receiver app.

## Build
The included GitHub Actions workflow installs Gradle 8.11.1 in the cloud and builds a debug APK. No Android Studio or Gradle wrapper is required on your phone.

## Current prototype
- Sender / Receiver role selection
- Pairing-code UI placeholder
- Paired receiver/sender status placeholder
- Recording list UI placeholder

## Important
This prototype does not yet implement cellular call-audio capture or automatic cloud transfer. Android does not provide a universal API that guarantees capture of both sides of every cellular call. Those components must be implemented using supported APIs and tested on the target device/Android version, with clear consent and recording indication.
