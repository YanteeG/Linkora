namespace Linkora.Windows.Models;

public sealed record PendingPairingView(string DeviceId, string DeviceName, string VerificationCode, DateTimeOffset ExpiresAt);

public sealed record ConnectedDeviceView(string DeviceId, string DeviceName, DateTimeOffset PairedAt);

public sealed record ServerViewState(
    bool IsRunning,
    string? Host,
    int Port,
    string? CertificateFingerprint,
    string? PairingJson,
    PendingPairingView? Pending,
    ConnectedDeviceView? Connected,
    string Status);
