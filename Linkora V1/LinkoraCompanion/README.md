# Linkora companion apps

This repository contains two independent projects:

- `LinkoraAndroid/` — the uploaded Android project copied without source changes.
- `LinkoraCompanion/` — the new Windows 11 WPF companion and the shared protocol/test library.

## Android source inspection

The Android app is the WebSocket client. It uses:

- protocol version `1`
- `wss://<host>:43127/linkora/v1`
- JSON envelopes with `type` and `protocolVersion`
- pairing messages `pair_request`, `pair_challenge`, `pair_confirm`, and `pair_complete`
- certificate leaf DER SHA-256 pinning
- binary file frame `0x01 + UUID (16 bytes, RFC 4122 big endian) + uint32 big endian chunk index + payload`
- maximum binary payload `262144` bytes

The exact Android handshake is preserved: the phone sends `pair_request`, receives a challenge and verification code, then sends `pair_confirm` with the literal challenge in `challengeResponse` after the user confirms the code. The Windows app waits for that confirmation **and** for a Windows user approval.

The uploaded Android model accepts `expiresAt` as a string but its `PairingPayload.isValid()` does not compare it with the current time. The Windows server enforces the five-minute expiry and consumes the token at the first valid request.

## Windows milestone 1

Implemented:

- native .NET 8 WPF dark UI
- LAN-only binding to a selected private IPv4 address
- TLS 1.2/1.3 with a locally generated self-signed certificate
- certificate SHA-256 fingerprint from the DER certificate bytes
- QR payload with the exact Android field names: `protocolVersion`, `host`, `port`, `certificateSha256`, `pairingToken`, `expiresAt`
- cryptographically random five-minute one-time token
- WebSocket path and protocol validation
- Android verification code plus explicit approval on Windows
- encrypted Windows user storage using DPAPI for the certificate and trusted device
- disconnect/revoke controls
- redacted diagnostic messages
- protocol unit tests and a Windows CI workflow

Not implemented in this milestone: file transfer, clipboard sharing, notifications, battery status, transfer history, cloud/relay, telemetry, arbitrary commands, or remote PC control. The server rejects those feature messages with an explicit unsupported response.

### Important security limitation in the uploaded Android client

The current Android reconnection path sends only `device_info`; it does not send a proof derived from a stored secret. The Windows app therefore does not silently accept that reconnection path. Every connection in this milestone must use a fresh QR token and the two-sided approval flow. A later reconnection milestone should first add a mutually authenticated device key protocol to both apps.

## Build on Windows 11

Prerequisites:

1. Windows 11.
2. .NET 8 SDK.
3. Visual Studio 2022 with the **.NET desktop development** workload, or the .NET CLI.
4. Android Studio for `LinkoraAndroid` and an Android phone running Android 7.0 or newer.

Build and test Windows:

```powershell
cd LinkoraCompanion
 dotnet restore .\LinkoraCompanion.sln
 dotnet test .\tests\Linkora.Windows.Tests\Linkora.Windows.Tests.csproj -c Release
 dotnet build .\src\Linkora.Windows\Linkora.Windows.csproj -c Release
 dotnet run --project .\src\Linkora.Windows\Linkora.Windows.csproj -c Release
```

NuGet packages used by the Windows app are `QRCoder 1.6.0` and `System.Security.Cryptography.ProtectedData 8.0.0`. The shared protocol library has no external package.

Build Android in Android Studio:

1. Open the `LinkoraAndroid` folder.
2. Let Android Studio sync the existing Gradle project.
3. Select the `app` run configuration and a physical Android phone.
4. Build and run. The original Android source was not rewritten by this repository.

## Pairing test

1. Put the phone and Windows PC on the same private Wi-Fi network.
2. Start the Windows app and click **Start server**.
3. Allow the app through Windows Defender Firewall on **Private networks** only when prompted. If Windows Firewall was previously blocking it, create an inbound TCP rule for local port `43127`, scoped to the Private profile.
4. In Android Linkora, open Pairing, scan the Windows QR code, and tap Connect.
5. Compare the six-digit code shown on both screens. Approve on Windows and confirm on Android.
6. The Windows app should show the connected device. Use **Disconnect** or **Revoke trust** to remove it.

The app binds to a private LAN IPv4 address rather than `0.0.0.0`. Do not create a Public-profile firewall rule and do not port-forward `43127` on a router.
