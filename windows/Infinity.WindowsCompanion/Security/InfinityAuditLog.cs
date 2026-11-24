using System.Text.Json;

namespace Infinity.WindowsCompanion.Security;

/// <summary>Bounded persistent audit trail; never records tokens, keys, screen data or file contents.</summary>
public sealed class InfinityAuditLog
{
    private const long MaximumLogBytes = 2 * 1024 * 1024;
    private static readonly object Gate = new();
    private readonly string directoryPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
        "INFINITY",
        "WindowsCompanion");
    private string LogPath => Path.Combine(directoryPath, "audit.jsonl");
    private string PreviousLogPath => Path.Combine(directoryPath, "audit.previous.jsonl");

    public void Write(string eventCode, string? certificateSha256, string? sessionId, string outcome)
    {
        if (eventCode.Length is < 1 or > 48 ||
            eventCode.Any(character => !(character is >= 'a' and <= 'z' or '.' or '-')))
            throw new ArgumentException("Audit event code is invalid.", nameof(eventCode));
        if (outcome is not ("allowed" or "denied" or "revoked" or "failed"))
            throw new ArgumentException("Audit outcome is invalid.", nameof(outcome));

        var normalizedFingerprint = certificateSha256 is null
            ? null
            : CertificatePinning.NormalizeSha256Pin(certificateSha256)
                ?? throw new ArgumentException("Audit certificate fingerprint is invalid.", nameof(certificateSha256));
        var normalizedSession = sessionId is null
            ? null
            : Guid.TryParseExact(sessionId, "D", out var parsed)
                ? parsed.ToString("D")
                : throw new ArgumentException("Audit session ID is invalid.", nameof(sessionId));

        var record = new AuditRecord(
            DateTimeOffset.UtcNow,
            eventCode,
            normalizedFingerprint,
            normalizedSession,
            outcome);
        var json = JsonSerializer.Serialize(record);

        lock (Gate)
        {
            Directory.CreateDirectory(directoryPath);
            if (File.Exists(LogPath) && new FileInfo(LogPath).Length >= MaximumLogBytes)
            {
                if (File.Exists(PreviousLogPath)) File.Delete(PreviousLogPath);
                File.Move(LogPath, PreviousLogPath);
            }

            using var stream = new FileStream(
                LogPath,
                FileMode.Append,
                FileAccess.Write,
                FileShare.Read,
                4096,
                FileOptions.WriteThrough);
            using var writer = new StreamWriter(stream);
            writer.WriteLine(json);
            writer.Flush();
            stream.Flush(flushToDisk: true);
        }
    }

    private sealed record AuditRecord(
        DateTimeOffset TimestampUtc,
        string EventCode,
        string? CertificateSha256,
        string? SessionId,
        string Outcome);
}
