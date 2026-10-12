```plaintext
# LINKORA — MASTER PRODUCT VISION 



# 1. PRODUCT DESCRIPTION

## What is Linkora?

Linkora is a private, open-source, local-network connection system that connects my Android phone directly to my Windows 11 laptop.

It should provide a unified way to transfer files, share selected information, and view useful phone status information from my PC, without depending on a cloud account or a third-party relay service.

Think of it as a lightweight, privacy-first phone-to-PC bridge that I control.

The Android and Windows apps are two halves of the same product. They must share one communication protocol, compatible data models, a consistent pairing system, and matching feature behavior.

The intended experience is simple:

1. I open Linkora on my Windows laptop.
2. I open Linkora on my Android phone.
3. I pair the devices by scanning a QR code and confirming the pairing.
4. Both apps remember the trusted device.
5. When both devices are available on the same local network, they can reconnect securely.
6. I can transfer files in either direction, manually share clipboard text, view selected Android notifications, and check my phone's battery status.
7. I can disconnect, revoke trust, delete local history, or remove all paired-device data whenever I want.

Linkora must not require an account, cloud storage, a hosted backend, a subscription, analytics, or telemetry.

## My environment

- Android phone.
- laptop.
- Windows 11.
- Wi-Fi or local-area network connectivity.
- Beginner-level programming experience.
- Browser-based AI coding tools are preferred.
- I am comfortable downloading ZIP files, extracting projects, and following step-by-step instructions.

The first target is my Android phone and Windows laptop. Keep the architecture maintainable enough for future improvements and public open-source distribution.

---

# 2. NON-NEGOTIABLE PRODUCT PRINCIPLES

1. **Local first:** Android and Windows communicate directly over the local network. No cloud relay, remote server, or internet-dependent account system.
2. **Privacy first:** no telemetry, analytics, advertisements, tracking, or unnecessary collection of personal data.
3. **Secure by design:** authenticated encrypted communication, explicit device pairing, validation of untrusted input, and secure secret storage.
4. **Real functionality:** every advertised feature must be implemented and tested, or clearly marked as incomplete.
5. **Two applications, one system:** both applications must implement exactly the same protocol.
6. **Beginner friendly:** clear code organization, documentation, build commands, and troubleshooting instructions.
7. **Minimal permissions:** request only the permissions necessary for the features the user enables.
8. **No unnecessary complexity:** prefer reliable, maintainable technologies over elaborate architecture.
9. **No destructive shortcuts:** never disable certificate validation, weaken operating-system security, or silently trust unknown devices to make a demo work.
10. **Open-source ready:** use appropriately licensed dependencies, include license notices, and document the build process.

---

# 3. TARGET PLATFORMS AND TECHNOLOGY

## Android application

Build a native Android app using:

- Kotlin.
- Jetpack Compose.
- Material 3.
- Android lifecycle-aware architecture.
- Kotlin coroutines for asynchronous work.
- Android's native file picker and Storage Access Framework.
- Android Keystore for sensitive cryptographic material where appropriate.
- A maintained WebSocket/TLS networking implementation.
- A maintained QR scanning and QR generation library where needed.
- Local persistent storage for settings, trusted-device records, and transfer history.

Use a maintainable structure separating UI, application state, networking, protocol models, security, file transfer, notifications, battery status, and persistence.

Use currently supported stable dependencies and document their versions. Do not assume a particular library API without checking that it exists.

## Windows application

Build a native Windows 11 desktop app using:

- C#.
- A currently supported .NET release, preferably an LTS release.
- WPF for the desktop UI unless there is a strong, documented reason to choose another native Windows framework.
- A maintained WebSocket server/client implementation.
- Windows-supported certificate and cryptographic APIs.
- Windows-protected secret storage where appropriate.
- A maintained QR-code generation library.
- Asynchronous I/O and cancellation support.

The Windows app must run as a normal user application wherever possible. Do not require administrator access for normal usage.

Do not use Electron or a browser-only interface unless native Windows implementation is genuinely blocked by the environment.


# 4. FEATURE SPECIFICATION

## Feature A — Secure device pairing

This is the foundation for all other features.

The Windows app should provide a pairing screen containing:

- A QR code.
- A readable local IP address and port.
- A server-running indicator.
- A short explanation of how to pair.
- A pending-pairing approval screen.
- A verification code visible on both devices.
- A list of trusted devices.
- A way to disconnect or revoke a device.

The Android app should provide:

- A QR scanner.
- A way to enter pairing details manually if scanning is unavailable.
- The name of the Windows computer being paired.
- A verification-code confirmation screen.
- Clear success and error states.
- A trusted-device list and a way to forget devices.

### Pairing security

Use an explicit, authenticated pairing handshake.

The QR code should contain only the information required to locate and authenticate the Windows pairing endpoint. It must not contain permanent reusable credentials.

Use a cryptographically random, single-use pairing token with a short expiration period, such as five minutes.

Both applications must validate the pairing session. The Windows user must explicitly approve a new device. The user should compare the verification code on both screens before approving.

Bind the session to the intended device and server identity. Reject expired tokens, reused tokens, malformed payloads, invalid protocol versions, and untrusted certificates.

After pairing, securely store the minimum required identity and authentication material to reconnect the trusted devices. Support revocation.

Never assume that being on the same Wi-Fi network makes a device trustworthy.

## Feature B — Secure local communication

Use a secure WebSocket connection over TLS between the Android and Windows apps.

Requirements:

- WebSocket over TLS only.
- Certificate identity verification and secure certificate pinning or an equivalent properly designed trust mechanism.
- No disabling of certificate validation.
- No plaintext fallback.
- No public internet listener or port forwarding.
- Bind the listener to an appropriate local network interface rather than exposing it unnecessarily.
- Handle multiple local network interfaces carefully.
- Detect network changes and recover gracefully.
- Set timeouts and resource limits.
- Validate all incoming data.
- Use authenticated device identity for reconnecting.
- Provide understandable connection diagnostics without logging secrets.

Use a sensible default port, such as 43127, if compatible with the implementation.

Document the exact certificate lifecycle, certificate fingerprint format, reconnection behavior, and pairing flow.

A certificate fingerprint alone does not authenticate every later message or authorize an unknown device. Design the entire trust model correctly.

Do not use a custom cryptographic protocol when a well-understood standard mechanism can solve the problem.

## Feature C — Bidirectional file transfer

Support file transfers in both directions.

Android to Windows:
- Select files using Android's system file picker.
- Choose files and send them to the paired Windows device.
- Show transfer progress.
- Cancel a transfer.
- Display success or a clear error.

Windows to Android:
- Select files through a standard Windows file dialog.
- Send files to the paired Android device.
- Show progress and cancellation.
- Let the Android user approve incoming files and choose where to save them.

Requirements:

- Explicit acceptance or rejection of incoming transfers.
- Transfer IDs and bounded chunk sizes.
- Streaming rather than loading entire large files into memory.
- File-size limits and configurable limits on concurrent transfers.
- SHA-256 integrity verification.
- Safe filenames and destination handling.
- Protection against path traversal.
- No silent overwriting of existing files.
- Temporary files while a transfer is incomplete.
- Clean up incomplete transfers when appropriate.
- Correct handling of disconnects, cancellation, timeouts, and insufficient storage.
- Clear reporting of transfer status.

Never execute transferred files automatically. Do not silently open or run received files.

## Feature D — Manual clipboard sharing

Support manual text sharing only.

The user explicitly selects a clipboard-sharing action, reviews the text, and sends it to the paired device.

Requirements:

- No continuous clipboard monitoring.
- No background clipboard synchronization.
- No silent collection of clipboard contents.
- Clear confirmation before sending sensitive text.
- Explicit user action before replacing clipboard contents on the receiving device.
- Handle large text and unsupported content gracefully.

Initially support plain text. Rich text, images, and files copied to the clipboard are outside the first version unless implemented safely and deliberately.

## Feature E — Selected Android notifications

Allow the user to forward notifications from selected Android applications to Windows.

Requirements:

- Explain that Android's notification-listener permission is powerful and must be explicitly granted in Android settings.
- Never attempt to bypass the permission screen.
- Let the user select which applications are allowed to forward notifications.
- Forward only notifications matching that selection.
- Include a global pause/disable switch.
- Respect revoked permissions immediately.
- Avoid forwarding notification content from unselected apps.
- Do not persist notification content unnecessarily.
- Do not forward sensitive notification content unless the user has enabled the relevant option.

The Windows app should display incoming notifications in a simple notification center or activity panel. Do not pretend to reproduce all Android notification actions unless they are actually supported.

## Feature F — Phone battery status

Display the Android phone's battery percentage on Windows.

Where available, also show charging state.

Use the Android platform's supported battery APIs and update the Windows display through the authenticated connection.

Do not poll excessively or create unnecessary background activity.

Display an unavailable or disconnected state honestly if the phone cannot report its status.

## Feature G — Drag and drop

On Windows, allow the user to drag files into a Linkora transfer area to begin the sending workflow.

Dragging a file must not automatically bypass the normal confirmation and transfer safety checks.

Use standard Windows drag-and-drop behavior and show a clear target area.

## Feature H — Auto-reconnect

Remember paired devices and reconnect when:

- Linkora is running on both devices.
- Both devices are reachable on the same local network.
- The device identities still match.
- The devices are still trusted.

Auto-reconnect must never mean automatic pairing of unknown devices.

Handle Wi-Fi changes, laptop sleep and resume, phone disconnection, and temporary network failure.

Manual connection controls must remain available.

If discovery is implemented, use a bounded local-network discovery mechanism and do not broadcast permanent credentials, secrets, or personal information.

Do not assume the IP address will remain constant.

## Feature I — Transfer history

Provide a local history of transfers, including:

- Filename.
- Transfer direction.
- Date and time.
- File size.
- Status.
- Error information when appropriate.

History must not store file contents.

Allow the user to clear the history. Explain what is deleted and whether any temporary files remain.

Make history retention configurable where practical.

## Feature J — Privacy and settings

Provide settings for:

- Device name.
- Trusted devices.
- Connection status.
- Auto-reconnect.
- Notification forwarding and selected apps.
- Clipboard sharing.
- Transfer limits.
- History retention and deletion.
- Local data deletion.
- Privacy information.
- Open-source license and version information.

Include a prominent way to disconnect devices, revoke trust, stop the local server, and delete local Linkora data.

Clearly explain that deleting local data may require pairing again.

---

# 5. USER INTERFACE AND DESIGN SYSTEM

Create a modern, restrained, premium-looking interface.

The design should feel like a carefully made desktop/mobile utility rather than a generic generated dashboard.

### Visual direction

- Dark theme by default.
- Near-black or charcoal surfaces.
- Subtle contrast between panels.
- One restrained accent color.
- Clear typography.
- Consistent spacing and corner radii.
- Simple line icons.
- Minimal animation.
- No excessive gradients, glass effects, glowing borders, or decorative clutter.
- English interface throughout the first version.
- Accessible contrast and readable text.
- Consistent visual language across Android and Windows.

Use native platform conventions where they improve usability.

### Android navigation

Include screens or sections for:

1. Dashboard.
2. Pair device.
3. Files.
4. Clipboard.
5. Notifications.
6. Battery and device status.
7. Transfer history.
8. Settings.

The dashboard should prominently display:

- Whether the Windows device is connected.
- The paired device's name.
- A clear connection action.
- Recent transfer activity.
- Phone battery status where useful.
- Shortcuts to file transfer and clipboard sharing.

### Windows navigation

Include:

1. Dashboard.
2. Pair device.
3. File transfer.
4. Clipboard.
5. Notification center.
6. Transfer history.
7. Settings.

The Windows dashboard should show:

- Connection status.
- Paired Android device.
- Battery percentage and charging status.
- File-transfer progress.
- Recent activity.
- Pairing controls.
- Server state.

Both interfaces must provide real loading, empty, disconnected, error, success, and permission-denied states.

Do not populate the interface with fake devices, fake transfers, fake notifications, or fabricated battery data.

Use mock data only in a clearly labeled development/demo mode that is separate from the real application.

---

# 6. SHARED COMMUNICATION PROTOCOL

Design one protocol specification before implementing the networking features.

The Android and Windows applications must use the same versioned message definitions and compatible serialization rules.

Use a maintainable JSON control-message format with a required protocol version and message type. Use binary frames for file chunks where appropriate.

Define:

- Message envelope and schema.
- Device identity.
- Pairing request, challenge, confirmation, and completion.
- Connection status and heartbeats.
- File offers, accept/reject, chunk transfer, progress, completion, cancellation, and errors.
- Clipboard requests and responses.
- Notification events.
- Battery updates.
- Device information.
- Reconnection behavior.
- Version negotiation.
- Message validation.
- Resource limits and timeouts.

For binary transfer frames, define exact byte order, identifier encoding, chunk numbering, payload length, and error handling.

If the existing Android implementation already defines a protocol, inspect it and either preserve it or explicitly migrate both apps together. Do not implement a second incompatible version.

Keep a single human-readable specification and shared test vectors. If practical, generate compatible models or validation tests from a common schema.

Provide protocol examples that can be used in unit tests.

Unknown message types, unsupported versions, invalid fields, oversized payloads, and malformed binary frames must be handled safely.

Never deserialize untrusted data into arbitrary executable types.

---

# 7. SECURITY AND THREAT MODEL

Assume that the local network may contain untrusted devices and that an attacker may attempt to connect to the listening port, replay pairing messages, send malformed data, or impersonate a device.

Protect against:

- Unauthorized device pairing.
- Reuse of expired or consumed pairing tokens.
- Server impersonation.
- Man-in-the-middle attacks.
- Unauthenticated reconnects.
- Replay of sensitive operations.
- Path traversal.
- Malicious filenames.
- Oversized payloads and memory exhaustion.
- Denial-of-service attempts through excessive connections or transfers.
- Accidental exposure of secrets in logs.
- Unexpected file overwrites.
- Notification and clipboard leakage.
- Unintended public-network exposure.

Requirements:

- Authenticated encryption in transit.
- Secure random token generation.
- Explicit trust decisions.
- Proper certificate verification.
- Secure local secret storage.
- Least privilege.
- Bounded queues and transfer sizes.
- Input validation.
- Cancellation and timeout handling.
- No hardcoded production secrets.
- No unnecessary permissions.
- No unsafe fallback to plaintext.
- No remote execution or remote PC control.
- No silent weakening of Windows Firewall or Android security.

Do not claim that Linkora is completely secure. Document its threat model, known limitations, and the distinction between tested behavior and assumptions.

Do not expose the server to the
```
