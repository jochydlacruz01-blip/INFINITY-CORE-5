using System.Security.Cryptography;

namespace Infinity.WindowsCompanion.Security;

/// <summary>Short-lived in-memory sessions bound to one approved client certificate.</summary>
public sealed class AuthorizedSessionStore
{
    private const int MaximumSessions = 256;
    private static readonly TimeSpan SessionLifetime = TimeSpan.FromMinutes(15);
    private readonly object gate = new();
    private readonly Dictionary<Guid, SessionRecord> sessions = new();
    private readonly Queue<ExpiredSession> expiredSessions = new();
    private readonly Func<DateTimeOffset> utcNow;

    public AuthorizedSessionStore(Func<DateTimeOffset>? utcNow = null)
    {
        this.utcNow = utcNow ?? (() => DateTimeOffset.UtcNow);
    }

    public SessionGrant Create(
        string clientCertificateSha256,
        bool explicitConsent,
        IReadOnlyCollection<string> requestedCapabilities,
        IReadOnlySet<string>? allowedCapabilities = null)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(clientCertificateSha256)
            ?? throw new ArgumentException("A valid client certificate fingerprint is required.");
        if (!explicitConsent)
            throw new UnauthorizedAccessException("Explicit user consent is required to create a session.");
        var allowed = allowedCapabilities ??
            new HashSet<string>(StringComparer.Ordinal) { "identity.read" };
        if (requestedCapabilities.Count is < 1 or > 4 ||
            !requestedCapabilities.Contains("identity.read", StringComparer.Ordinal) ||
            requestedCapabilities.Distinct(StringComparer.Ordinal).Count() != requestedCapabilities.Count ||
            requestedCapabilities.Any(capability => !allowed.Contains(capability)))
            throw new UnauthorizedAccessException("One or more requested capabilities are not enabled by Windows policy.");
        var hasRemoteDisplay = requestedCapabilities.Contains("remote.display", StringComparer.Ordinal);
        var hasRemoteInput = requestedCapabilities.Contains("remote.input", StringComparer.Ordinal);
        if (hasRemoteDisplay != hasRemoteInput)
            throw new UnauthorizedAccessException("Remote display and input must be granted together.");

        lock (gate)
        {
            var now = utcNow();
            PurgeExpired(now);
            if (sessions.Count >= MaximumSessions ||
                sessions.Values.Count(session => string.Equals(
                    session.ClientFingerprint, fingerprint, StringComparison.Ordinal)) >= 4)
                throw new InvalidOperationException("The active session limit has been reached.");

            var sessionId = Guid.NewGuid();
            var tokenBytes = RandomNumberGenerator.GetBytes(32);
            var token = Base64UrlEncode(tokenBytes);
            var tokenHash = SHA256.HashData(tokenBytes);
            CryptographicOperations.ZeroMemory(tokenBytes);
            var expiresAt = now.Add(SessionLifetime);
            sessions.Add(sessionId, new SessionRecord(
                fingerprint,
                tokenHash,
                expiresAt,
                new HashSet<string>(requestedCapabilities, StringComparer.Ordinal)));
            return new SessionGrant(sessionId.ToString("D"), token, expiresAt, requestedCapabilities.ToArray());
        }
    }

    public bool IsAuthorized(
        string sessionId,
        string bearerToken,
        string clientCertificateSha256,
        string requiredCapability)
    {
        if (!Guid.TryParseExact(sessionId, "D", out var parsedId)) return false;
        var fingerprint = CertificatePinning.NormalizeSha256Pin(clientCertificateSha256);
        if (fingerprint is null || string.IsNullOrWhiteSpace(bearerToken)) return false;

        byte[] suppliedHash;
        try
        {
            var suppliedTokenBytes = Base64UrlDecode(bearerToken);
            try { suppliedHash = SHA256.HashData(suppliedTokenBytes); }
            finally { CryptographicOperations.ZeroMemory(suppliedTokenBytes); }
        }
        catch (FormatException)
        {
            return false;
        }

        lock (gate)
        {
            PurgeExpired(utcNow());
            if (!sessions.TryGetValue(parsedId, out var session)) {
                CryptographicOperations.ZeroMemory(suppliedHash);
                return false;
            }
            var authorized =
                string.Equals(session.ClientFingerprint, fingerprint, StringComparison.Ordinal) &&
                session.Capabilities.Contains(requiredCapability) &&
                CryptographicOperations.FixedTimeEquals(session.TokenHash, suppliedHash);
            CryptographicOperations.ZeroMemory(suppliedHash);
            return authorized;
        }
    }

    public bool Revoke(string sessionId, string clientCertificateSha256)
    {
        if (!Guid.TryParseExact(sessionId, "D", out var parsedId)) return false;
        var fingerprint = CertificatePinning.NormalizeSha256Pin(clientCertificateSha256);
        if (fingerprint is null) return false;
        lock (gate)
        {
            if (!sessions.TryGetValue(parsedId, out var session) ||
                !string.Equals(session.ClientFingerprint, fingerprint, StringComparison.Ordinal))
                return false;
            sessions.Remove(parsedId);
            session.Clear();
            return true;
        }
    }

    public int RevokeDevice(string clientCertificateSha256)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(clientCertificateSha256);
        if (fingerprint is null) return 0;
        lock (gate)
        {
            var matches = sessions.Where(pair =>
                string.Equals(pair.Value.ClientFingerprint, fingerprint, StringComparison.Ordinal))
                .Select(pair => pair.Key).ToArray();
            foreach (var id in matches)
            {
                var record = sessions[id];
                sessions.Remove(id);
                record.Clear();
            }
            return matches.Length;
        }
    }

    public IReadOnlyList<string> ActiveDeviceFingerprints
    {
        get
        {
            lock (gate)
                return sessions.Values.Select(session => session.ClientFingerprint)
                    .Distinct(StringComparer.Ordinal).ToArray();
        }
    }

    /// <summary>
    /// Returns sessions that expired since the last drain. The caller must revoke the matching
    /// DeviceOperationRegistry lease so long-lived tunnels are closed at the session deadline.
    /// </summary>
    public IReadOnlyList<ExpiredSession> DrainExpiredSessions()
    {
        lock (gate)
        {
            PurgeExpired(utcNow());
            var expired = expiredSessions.ToArray();
            expiredSessions.Clear();
            return expired;
        }
    }

    public int ActiveCount
    {
        get
        {
            lock (gate)
            {
                PurgeExpired(utcNow());
                return sessions.Count;
            }
        }
    }

    private void PurgeExpired(DateTimeOffset now)
    {
        foreach (var id in sessions.Where(pair => pair.Value.ExpiresAt <= now)
                     .Select(pair => pair.Key).ToArray())
        {
            var record = sessions[id];
            sessions.Remove(id);
            expiredSessions.Enqueue(new ExpiredSession(id.ToString("D"), record.ClientFingerprint));
            record.Clear();
        }
    }

    private static string Base64UrlEncode(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    private static byte[] Base64UrlDecode(string value)
    {
        if (value.Length != 43 ||
            value.Any(character => !(character is >= 'A' and <= 'Z' or >= 'a' and <= 'z' or >= '0' and <= '9' or '-' or '_')))
            throw new FormatException("Malformed session token.");
        var normalized = value.Replace('-', '+').Replace('_', '/');
        normalized += new string('=', (4 - normalized.Length % 4) % 4);
        return Convert.FromBase64String(normalized);
    }

    private sealed class SessionRecord(
        string clientFingerprint,
        byte[] tokenHash,
        DateTimeOffset expiresAt,
        HashSet<string> capabilities)
    {
        public string ClientFingerprint { get; } = clientFingerprint;
        public byte[] TokenHash { get; } = tokenHash;
        public DateTimeOffset ExpiresAt { get; } = expiresAt;
        public HashSet<string> Capabilities { get; } = capabilities;
        public void Clear() => CryptographicOperations.ZeroMemory(TokenHash);
    }

    public sealed record ExpiredSession(string SessionId, string ClientFingerprint);

    public sealed record SessionGrant(
        string SessionId,
        string Token,
        DateTimeOffset ExpiresAtUtc,
        IReadOnlyCollection<string> Capabilities);
}
