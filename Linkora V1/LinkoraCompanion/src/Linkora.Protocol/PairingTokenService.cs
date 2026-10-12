using System;
using System.Security.Cryptography;

namespace Linkora.Protocol;

public sealed record PairingTokenLease(string Token, DateTimeOffset ExpiresAt, bool Used = false);

public sealed class PairingTokenService
{
    private readonly object _gate = new();
    private PairingTokenLease? _active;
    private readonly TimeSpan _lifetime;

    public PairingTokenService(TimeSpan? lifetime = null)
    {
        _lifetime = lifetime ?? TimeSpan.FromMinutes(5);
    }

    public PairingTokenLease Create(DateTimeOffset now)
    {
        var tokenBytes = RandomNumberGenerator.GetBytes(32);
        var token = Convert.ToBase64String(tokenBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        var lease = new PairingTokenLease(token, now.Add(_lifetime));
        lock (_gate) _active = lease;
        return lease;
    }

    public bool TryConsume(string candidate, DateTimeOffset now, out PairingTokenLease? consumed)
    {
        lock (_gate)
        {
            consumed = _active;
            if (_active is null || _active.Used || now >= _active.ExpiresAt || string.IsNullOrEmpty(candidate))
                return false;

            var candidateBytes = System.Text.Encoding.UTF8.GetBytes(candidate);
            var activeBytes = System.Text.Encoding.UTF8.GetBytes(_active.Token);
            if (candidateBytes.Length != activeBytes.Length ||
                !CryptographicOperations.FixedTimeEquals(candidateBytes, activeBytes))
                return false;

            _active = _active with { Used = true };
            consumed = _active;
            return true;
        }
    }

    public void Invalidate()
    {
        lock (_gate) _active = null;
    }
}
