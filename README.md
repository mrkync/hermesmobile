# Hermes Mobile

Native Android/Jetpack Compose V1 client for a self-hosted Hermes Agent `hermes serve` backend.

## Current V1
- ChatGPT-like dark chat UI
- Remote gateway URL
- Basic username/password login
- WebSocket `/api/ws` with single-use ticket
- Session creation
- Prompt streaming (`message.delta`)
- Stop request
- Tailscale-compatible HTTP URL

## Target gateway
`http://100.82.236.41:9119`

## Build
Requires Android Studio/SDK with API 37 and JDK 21. The project uses AGP 9.1.1, Kotlin 2.4.10 and Compose BOM 2026.08.00.

`./gradlew :app:assembleDebug`

Note: this workspace does not contain the Android SDK/Gradle distribution, so APK compilation must be performed in an Android build environment.
