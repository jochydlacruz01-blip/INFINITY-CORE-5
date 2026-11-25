using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.Text.Json;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Per-device, bounded file storage. Client filenames are metadata only; disk paths use GUIDs.
/// </summary>
public sealed class SecureFileTransferStore
{
    public const long MaximumFileBytes = 50L * 1024 * 1024;
    private const int MaximumFileNameLength = 128;
    private readonly string rootPath;
    private readonly SemaphoreSlim writeGate = new(1, 1);
    private const int MaximumFilesPerDevice = 100;
    private const long MaximumBytesPerDevice = 500L * 1024 * 1024;

    public SecureFileTransferStore(string? storageRoot = null)
    {
        if (!OperatingSystem.IsWindows())
            throw new PlatformNotSupportedException("Secure transfer storage requires Windows filesystem ACLs.");
        rootPath = Path.GetFullPath(storageRoot ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
            "INFINITY",
            "WindowsCompanion",
            "Transfers"));
        EnsurePrivateDirectory(rootPath);
    }

    public async Task<FileReceipt> SaveAsync(
        Stream source,
        string clientCertificateSha256,
        string fileName,
        long contentLength,
        string expectedSha256,
        CancellationToken cancellationToken)
    {
        await writeGate.WaitAsync(cancellationToken);
        try
        {
            return await SaveCoreAsync(
                source, clientCertificateSha256, fileName, contentLength, expectedSha256, cancellationToken);
        }
        finally
        {
            writeGate.Release();
        }
    }

    private async Task<FileReceipt> SaveCoreAsync(
        Stream source,
        string clientCertificateSha256,
        string fileName,
        long contentLength,
        string expectedSha256,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(source);
        var fingerprint = CertificatePinning.NormalizeSha256Pin(clientCertificateSha256)
            ?? throw new ArgumentException("A valid device certificate fingerprint is required.");
        ValidateFileName(fileName);
        if (contentLength is < 0 or > MaximumFileBytes)
            throw new InvalidDataException("File size exceeds the 50 MiB transfer limit.");
        var expectedHash = CertificatePinning.NormalizeSha256Pin(expectedSha256)
            ?? throw new ArgumentException("A valid SHA-256 file digest is required.");

        var deviceDirectory = Path.Combine(rootPath, fingerprint);
        EnsurePrivateDirectory(deviceDirectory);
        var existingFiles = Directory.EnumerateFiles(deviceDirectory, "*.bin").Take(MaximumFilesPerDevice + 1).ToArray();
        if (existingFiles.Length >= MaximumFilesPerDevice ||
            existingFiles.Sum(path => new FileInfo(path).Length) > MaximumBytesPerDevice - contentLength)
            throw new InvalidDataException("Per-device transfer storage quota has been reached.");
        var fileId = Guid.NewGuid();
        var temporaryPath = Path.Combine(deviceDirectory, fileId.ToString("N") + ".tmp");
        var finalPath = Path.Combine(deviceDirectory, fileId.ToString("N") + ".bin");
        var metadataPath = Path.Combine(deviceDirectory, fileId.ToString("N") + ".json");
        long written = 0;
        string actualHash;

        try
        {
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            await using (var output = new FileStream(
                temporaryPath,
                FileMode.CreateNew,
                FileAccess.Write,
                FileShare.None,
                64 * 1024,
                FileOptions.Asynchronous | FileOptions.WriteThrough))
            {
                var buffer = new byte[64 * 1024];
                while (true)
                {
                    var count = await source.ReadAsync(buffer, cancellationToken);
                    if (count == 0) break;
                    written += count;
                    if (written > contentLength || written > MaximumFileBytes)
                        throw new InvalidDataException("Uploaded file exceeds its declared size or transfer limit.");
                    hash.AppendData(buffer, 0, count);
                    await output.WriteAsync(buffer.AsMemory(0, count), cancellationToken);
                }
                await output.FlushAsync(cancellationToken);
                output.Flush(flushToDisk: true);
            }

            if (written != contentLength)
                throw new InvalidDataException("Uploaded file length does not match Content-Length.");

            var digest = hash.GetHashAndReset();
            actualHash = Convert.ToHexString(digest);
            var expectedBytes = Convert.FromHexString(expectedHash);
            try
            {
                if (!CryptographicOperations.FixedTimeEquals(digest, expectedBytes))
                    throw new CryptographicException("Uploaded file SHA-256 does not match the declared digest.");
            }
            finally
            {
                CryptographicOperations.ZeroMemory(digest);
                CryptographicOperations.ZeroMemory(expectedBytes);
            }

            cancellationToken.ThrowIfCancellationRequested();
            File.Move(temporaryPath, finalPath);
            cancellationToken.ThrowIfCancellationRequested();
            var createdAt = DateTimeOffset.UtcNow;
            var metadata = new FileMetadata(
                fileId.ToString("D"),
                fingerprint,
                fileName,
                written,
                actualHash,
                createdAt);
            var metadataJson = JsonSerializer.Serialize(metadata);
            var metadataTemporaryPath = metadataPath + ".tmp";
            await File.WriteAllTextAsync(metadataTemporaryPath, metadataJson, Encoding.UTF8, cancellationToken);
            cancellationToken.ThrowIfCancellationRequested();
            File.Move(metadataTemporaryPath, metadataPath);
            cancellationToken.ThrowIfCancellationRequested();
            return new FileReceipt(metadata.FileId, metadata.FileName, metadata.Length, metadata.Sha256, metadata.CreatedAtUtc);
        }
        catch
        {
            TryDelete(temporaryPath);
            TryDelete(finalPath);
            TryDelete(metadataPath);
            TryDelete(metadataPath + ".tmp");
            throw;
        }
    }

    public FileMetadata? Find(string fileId, string clientCertificateSha256)
    {
        if (!Guid.TryParseExact(fileId, "D", out var parsedId)) return null;
        var fingerprint = CertificatePinning.NormalizeSha256Pin(clientCertificateSha256);
        if (fingerprint is null) return null;
        var path = Path.Combine(rootPath, fingerprint, parsedId.ToString("N") + ".json");
        if (!File.Exists(path) || new FileInfo(path).Length > 8 * 1024) return null;
        try
        {
            var metadata = JsonSerializer.Deserialize<FileMetadata>(File.ReadAllText(path, Encoding.UTF8));
            if (metadata is null ||
                metadata.FileId != parsedId.ToString("D") ||
                metadata.ClientCertificateSha256 != fingerprint ||
                metadata.Length is < 0 or > MaximumFileBytes)
                return null;
            ValidateFileName(metadata.FileName);
            var normalizedHash = CertificatePinning.NormalizeSha256Pin(metadata.Sha256);
            if (normalizedHash is null) return null;
            return metadata with { Sha256 = normalizedHash };
        }
        catch (JsonException)
        {
            return null;
        }
        catch (IOException)
        {
            return null;
        }
        catch (InvalidDataException)
        {
            return null;
        }
    }

    public IReadOnlyList<FileMetadata> List(string clientCertificateSha256)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(clientCertificateSha256);
        if (fingerprint is null) return Array.Empty<FileMetadata>();
        var directory = Path.Combine(rootPath, fingerprint);
        if (!Directory.Exists(directory)) return Array.Empty<FileMetadata>();

        return Directory.EnumerateFiles(directory, "*.json")
            .Take(MaximumFilesPerDevice)
            .Select(path => Path.GetFileNameWithoutExtension(path))
            .Where(name => Guid.TryParseExact(name, "N", out _))
            .Select(name => Find(Guid.ParseExact(name, "N").ToString("D"), fingerprint))
            .Where(metadata => metadata is not null)
            .Select(metadata => metadata!)
            .OrderByDescending(metadata => metadata.CreatedAtUtc)
            .ToArray();
    }

    public FileStream? OpenRead(FileMetadata metadata)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(metadata.ClientCertificateSha256);
        if (fingerprint is null || !Guid.TryParseExact(metadata.FileId, "D", out var id)) return null;
        var path = Path.Combine(rootPath, fingerprint, id.ToString("N") + ".bin");
        if (!File.Exists(path)) return null;
        var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, 64 * 1024, FileOptions.Asynchronous | FileOptions.SequentialScan);
        if (stream.Length != metadata.Length)
        {
            stream.Dispose();
            return null;
        }
        return stream;
    }

    public async Task<FileStream?> OpenReadVerifiedAsync(
        FileMetadata metadata,
        CancellationToken cancellationToken)
    {
        var stream = OpenRead(metadata);
        if (stream is null) return null;
        try
        {
            var actual = await SHA256.HashDataAsync(stream, cancellationToken);
            var normalizedHash = CertificatePinning.NormalizeSha256Pin(metadata.Sha256);
            if (normalizedHash is null)
            {
                await stream.DisposeAsync();
                return null;
            }
            var expected = Convert.FromHexString(normalizedHash);
            try
            {
                if (!CryptographicOperations.FixedTimeEquals(actual, expected))
                {
                    await stream.DisposeAsync();
                    return null;
                }
            }
            finally
            {
                CryptographicOperations.ZeroMemory(actual);
                CryptographicOperations.ZeroMemory(expected);
            }
            stream.Position = 0;
            return stream;
        }
        catch
        {
            await stream.DisposeAsync();
            throw;
        }
    }

    public bool Delete(string fileId, string clientCertificateSha256)
    {
        var metadata = Find(fileId, clientCertificateSha256);
        if (metadata is null) return false;
        var fingerprint = metadata.ClientCertificateSha256;
        var id = Guid.ParseExact(metadata.FileId, "D").ToString("N");
        var directory = Path.Combine(rootPath, fingerprint);
        TryDelete(Path.Combine(directory, id + ".bin"));
        TryDelete(Path.Combine(directory, id + ".json"));
        return true;
    }

    private static void EnsurePrivateDirectory(string path)
    {
        Directory.CreateDirectory(path);

        var security = new DirectorySecurity();
        security.SetAccessRuleProtection(isProtected: true, preserveInheritance: false);
        var inheritance = InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit;
        var principals = new Dictionary<string, SecurityIdentifier>(StringComparer.Ordinal)
        {
            [new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null).Value] =
                new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null),
            [new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null).Value] =
                new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null)
        };

        var currentUser = WindowsIdentity.GetCurrent().User;
        if (currentUser is not null) principals[currentUser.Value] = currentUser;

        foreach (var principal in principals.Values)
        {
            security.AddAccessRule(new FileSystemAccessRule(
                principal,
                FileSystemRights.FullControl,
                inheritance,
                PropagationFlags.None,
                AccessControlType.Allow));
        }

        new DirectoryInfo(path).SetAccessControl(security);
    }

    private static void ValidateFileName(string fileName)
    {
        if (string.IsNullOrWhiteSpace(fileName) ||
            fileName.Length > MaximumFileNameLength ||
            fileName is "." or ".." ||
            fileName != Path.GetFileName(fileName) ||
            fileName.Any(character => char.IsControl(character) || character is '/' or '\\' or ':' or '<' or '>' or '"' or '|' or '?' or '*') ||
            fileName.EndsWith(' ') ||
            fileName.EndsWith('.'))
            throw new InvalidDataException("Filename is invalid or contains path/control characters.");

        var stem = fileName.Split('.')[0].TrimEnd(' ', '.');
        if (new[] { "CON", "PRN", "AUX", "NUL" }.Contains(stem, StringComparer.OrdinalIgnoreCase) ||
            (stem.Length == 4 && (stem.StartsWith("COM", StringComparison.OrdinalIgnoreCase) ||
                                  stem.StartsWith("LPT", StringComparison.OrdinalIgnoreCase)) &&
             stem[3] is >= '1' and <= '9'))
            throw new InvalidDataException("Filename is reserved by Windows.");
    }

    private static void TryDelete(string path)
    {
        try { if (File.Exists(path)) File.Delete(path); }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
    }

    public sealed record FileReceipt(
        string FileId,
        string FileName,
        long Length,
        string Sha256,
        DateTimeOffset CreatedAtUtc);

    public sealed record FileMetadata(
        string FileId,
        string ClientCertificateSha256,
        string FileName,
        long Length,
        string Sha256,
        DateTimeOffset CreatedAtUtc);
}
