using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class SecureFileTransferStoreTests : IDisposable
{
    private const string DevicePin = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private const string OtherDevicePin = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private readonly string root = Path.Combine(Path.GetTempPath(), "infinity-transfer-tests-" + Guid.NewGuid().ToString("N"));
    private readonly SecureFileTransferStore store;

    public SecureFileTransferStoreTests()
    {
        store = new SecureFileTransferStore(root);
    }

    [Fact]
    public async Task SavesVerifiedFileAndRestrictsReadToOwningDevice()
    {
        var bytes = "INFINITY secure transfer payload"u8.ToArray();
        var digest = Convert.ToHexString(SHA256.HashData(bytes));
        await using var source = new MemoryStream(bytes);

        var receipt = await store.SaveAsync(source, DevicePin, "report.txt", bytes.Length, digest, CancellationToken.None);

        Assert.Equal(bytes.Length, receipt.Length);
        Assert.Equal(digest, receipt.Sha256);
        Assert.NotNull(store.Find(receipt.FileId, DevicePin));
        Assert.Null(store.Find(receipt.FileId, OtherDevicePin));
        Assert.Single(store.List(DevicePin));
        Assert.Empty(store.List(OtherDevicePin));

        var metadata = store.Find(receipt.FileId, DevicePin)!;
        using (var downloaded = (await store.OpenReadVerifiedAsync(metadata, CancellationToken.None))!)
        using (var output = new MemoryStream())
        {
            await downloaded.CopyToAsync(output);
            Assert.Equal(bytes, output.ToArray());
        }
        Assert.False(store.Delete(receipt.FileId, OtherDevicePin));
        Assert.True(store.Delete(receipt.FileId, DevicePin));
        Assert.Null(store.Find(receipt.FileId, DevicePin));
    }

    [Fact]
    public async Task StartupReconciliationRemovesInterruptedArtifactsAndPreservesCompleteTransfers()
    {
        var bytes = "durable transfer"u8.ToArray();
        var digest = Convert.ToHexString(SHA256.HashData(bytes));
        await using var source = new MemoryStream(bytes);
        var receipt = await store.SaveAsync(
            source, DevicePin, "complete.txt", bytes.Length, digest, CancellationToken.None);

        var deviceDirectory = Path.Combine(root, DevicePin);
        var staleId = Guid.NewGuid().ToString("N");
        var orphanDataId = Guid.NewGuid().ToString("N");
        var orphanMetadataId = Guid.NewGuid().ToString("N");
        await File.WriteAllBytesAsync(Path.Combine(deviceDirectory, staleId + ".tmp"), new byte[] { 1 });
        await File.WriteAllBytesAsync(Path.Combine(deviceDirectory, orphanDataId + ".bin"), new byte[] { 2 });
        await File.WriteAllTextAsync(Path.Combine(deviceDirectory, orphanMetadataId + ".json"), "{}");

        var restartedStore = new SecureFileTransferStore(root);

        Assert.NotNull(restartedStore.Find(receipt.FileId, DevicePin));
        Assert.False(File.Exists(Path.Combine(deviceDirectory, staleId + ".tmp")));
        Assert.False(File.Exists(Path.Combine(deviceDirectory, orphanDataId + ".bin")));
        Assert.False(File.Exists(Path.Combine(deviceDirectory, orphanMetadataId + ".json")));
    }

    [Fact]
    public async Task DeleteReportsLockedFileFailureAndPreservesItsMetadata()
    {
        var bytes = "locked file"u8.ToArray();
        var digest = Convert.ToHexString(SHA256.HashData(bytes));
        await using var source = new MemoryStream(bytes);
        var receipt = await store.SaveAsync(
            source, DevicePin, "locked.bin", bytes.Length, digest, CancellationToken.None);
        var dataPath = Path.Combine(root, DevicePin, Guid.Parse(receipt.FileId).ToString("N") + ".bin");

        using (var locked = new FileStream(dataPath, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            Assert.False(store.Delete(receipt.FileId, DevicePin));
            Assert.NotNull(store.Find(receipt.FileId, DevicePin));
        }

        Assert.True(store.Delete(receipt.FileId, DevicePin));
        Assert.Null(store.Find(receipt.FileId, DevicePin));
    }

    [Fact]
    public async Task DeleteAsyncHonorsCancellationBeforeMutatingStoredFile()
    {
        var bytes = "delete cancellation"u8.ToArray();
        var digest = Convert.ToHexString(SHA256.HashData(bytes));
        await using var source = new MemoryStream(bytes);
        var receipt = await store.SaveAsync(
            source, DevicePin, "keep-until-authorized.bin", bytes.Length, digest, CancellationToken.None);

        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            store.DeleteAsync(receipt.FileId, DevicePin, cancellation.Token));
        Assert.NotNull(store.Find(receipt.FileId, DevicePin));
    }

    [Fact]
    public async Task TransferDirectoriesUseProtectedAclWithoutBroadUserGroups()
    {
        var bytes = new byte[] { 1, 2, 3 };
        var digest = Convert.ToHexString(SHA256.HashData(bytes));
        await using var source = new MemoryStream(bytes);
        await store.SaveAsync(source, DevicePin, "private.bin", bytes.Length, digest, CancellationToken.None);

        var broadPrincipals = new HashSet<string>(StringComparer.Ordinal)
        {
            new SecurityIdentifier(WellKnownSidType.WorldSid, null).Value,
            new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null).Value,
            new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null).Value
        };
        var requiredPrincipals = new HashSet<string>(StringComparer.Ordinal)
        {
            new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null).Value,
            new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null).Value
        };

        foreach (var directoryPath in new[] { root, Path.Combine(root, DevicePin) })
        {
            var security = new DirectoryInfo(directoryPath).GetAccessControl();
            Assert.True(security.AreAccessRulesProtected);
            var rules = security.GetAccessRules(true, true, typeof(SecurityIdentifier))
                .Cast<FileSystemAccessRule>()
                .ToArray();
            var allowedSids = rules
                .Where(rule => rule.AccessControlType == AccessControlType.Allow)
                .Select(rule => ((SecurityIdentifier)rule.IdentityReference).Value)
                .ToHashSet(StringComparer.Ordinal);

            Assert.Empty(allowedSids.Intersect(broadPrincipals));
            Assert.True(requiredPrincipals.IsSubsetOf(allowedSids));
        }
    }


    [Fact]
    public async Task GuidCollisionOnTemporaryPathPreservesPreexistingFile()
    {
        var id = Guid.NewGuid();
        var store = new SecureFileTransferStore(root, () => id);
        var deviceDirectory = Path.Combine(root, DevicePin);
        Directory.CreateDirectory(deviceDirectory);
        var collisionPath = Path.Combine(deviceDirectory, id.ToString("N") + ".tmp");
        var original = "pre-existing temporary data"u8.ToArray();
        await File.WriteAllBytesAsync(collisionPath, original);
        var payload = "new upload"u8.ToArray();

        await using var source = new MemoryStream(payload);
        await Assert.ThrowsAsync<IOException>(() => store.SaveAsync(
            source, DevicePin, "collision.txt", payload.Length,
            Convert.ToHexString(SHA256.HashData(payload)), CancellationToken.None));

        Assert.Equal(original, await File.ReadAllBytesAsync(collisionPath));
    }

    [Fact]
    public async Task GuidCollisionOnFinalPathPreservesPreexistingFile()
    {
        var id = Guid.NewGuid();
        var store = new SecureFileTransferStore(root, () => id);
        var deviceDirectory = Path.Combine(root, DevicePin);
        Directory.CreateDirectory(deviceDirectory);
        var collisionPath = Path.Combine(deviceDirectory, id.ToString("N") + ".bin");
        var original = "existing stored content"u8.ToArray();
        await File.WriteAllBytesAsync(collisionPath, original);
        var payload = "new upload"u8.ToArray();

        await using var source = new MemoryStream(payload);
        await Assert.ThrowsAsync<IOException>(() => store.SaveAsync(
            source, DevicePin, "collision.bin", payload.Length,
            Convert.ToHexString(SHA256.HashData(payload)), CancellationToken.None));

        Assert.Equal(original, await File.ReadAllBytesAsync(collisionPath));
        Assert.False(File.Exists(Path.Combine(deviceDirectory, id.ToString("N") + ".tmp")));
    }

    [Fact]
    public async Task GuidCollisionOnMetadataPathPreservesPreexistingMetadataAndRemovesOnlyOwnedPayload()
    {
        var id = Guid.NewGuid();
        var store = new SecureFileTransferStore(root, () => id);
        var deviceDirectory = Path.Combine(root, DevicePin);
        Directory.CreateDirectory(deviceDirectory);
        var collisionPath = Path.Combine(deviceDirectory, id.ToString("N") + ".json");
        var original = "{\"owner\":\"existing\"}";
        await File.WriteAllTextAsync(collisionPath, original);
        var payload = "new upload"u8.ToArray();

        await using var source = new MemoryStream(payload);
        await Assert.ThrowsAsync<IOException>(() => store.SaveAsync(
            source, DevicePin, "collision.json", payload.Length,
            Convert.ToHexString(SHA256.HashData(payload)), CancellationToken.None));

        Assert.Equal(original, await File.ReadAllTextAsync(collisionPath));
        Assert.False(File.Exists(Path.Combine(deviceDirectory, id.ToString("N") + ".bin")));
        Assert.False(File.Exists(Path.Combine(deviceDirectory, id.ToString("N") + ".json.tmp")));
    }

    [Fact]
    public async Task RejectsTraversalNamesAndDigestMismatch()
    {
        var bytes = new byte[] { 1, 2, 3 };
        var digest = Convert.ToHexString(SHA256.HashData(bytes));

        await using (var source = new MemoryStream(bytes))
        {
            await Assert.ThrowsAsync<InvalidDataException>(() =>
                store.SaveAsync(source, DevicePin, "../escape.txt", bytes.Length, digest, CancellationToken.None));
        }

        await using (var source = new MemoryStream(bytes))
        {
            await Assert.ThrowsAsync<CryptographicException>(() =>
                store.SaveAsync(source, DevicePin, "payload.bin", bytes.Length, new string('0', 64), CancellationToken.None));
        }

        Assert.Empty(Directory.Exists(root) ? Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories) : Array.Empty<string>());
    }

    [Fact]
    public async Task RejectsStoredFileThatWasModifiedAfterUpload()
    {
        var bytes = new byte[] { 7, 8, 9, 10 };
        var digest = Convert.ToHexString(SHA256.HashData(bytes));
        await using var source = new MemoryStream(bytes);
        var receipt = await store.SaveAsync(source, DevicePin, "immutable.bin", bytes.Length, digest, CancellationToken.None);

        var storedPath = Path.Combine(root, DevicePin, Guid.Parse(receipt.FileId).ToString("N") + ".bin");
        await File.WriteAllBytesAsync(storedPath, new byte[] { 1, 1, 1, 1 });
        var metadata = store.Find(receipt.FileId, DevicePin)!;

        var verified = await store.OpenReadVerifiedAsync(metadata, CancellationToken.None);
        Assert.Null(verified);
    }

    [Fact]
    public async Task RejectsFilesAboveConfiguredLimitBeforeReading()
    {
        await using var source = new MemoryStream(Array.Empty<byte>());
        await Assert.ThrowsAsync<InvalidDataException>(() =>
            store.SaveAsync(
                source,
                DevicePin,
                "oversized.bin",
                SecureFileTransferStore.MaximumFileBytes + 1,
                new string('0', 64),
                CancellationToken.None));
    }

    [Fact]
    public async Task DeviceRevocationCancelsActiveUploadAndRemovesPartialFiles()
    {
        var registry = new DeviceOperationRegistry();
        registry.ActivateDevice(DevicePin);
        const string sessionId = "33333333-3333-4333-8333-333333333333";
        registry.ActivateSession(DevicePin, sessionId);
        using var operation = registry.BeginOperation(DevicePin, sessionId);
        using var source = new BlockingReadStream();
        var digest = Convert.ToHexString(SHA256.HashData(new byte[] { 1, 2 }));

        var upload = store.SaveAsync(
            source, DevicePin, "cancelled.bin", 2, digest, operation.Token);
        await source.SecondReadStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));

        var deviceDirectory = Path.Combine(root, DevicePin);
        _ = Assert.Single(Directory.EnumerateFiles(deviceDirectory, "*.tmp"));
        // The stream supplied one byte before blocking on its second read; the open temporary
        // file proves the transfer is in-flight without reopening a FileShare.None writer.

        Assert.True(registry.RevokeDevice(DevicePin));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => upload);

        Assert.Empty(Directory.Exists(deviceDirectory)
            ? Directory.EnumerateFiles(deviceDirectory, "*", SearchOption.AllDirectories)
            : Array.Empty<string>());
        Assert.True(operation.Token.IsCancellationRequested);
    }

    private sealed class BlockingReadStream : Stream
    {
        private int readCount;
        public TaskCompletionSource SecondReadStarted { get; } =
            new(TaskCreationOptions.RunContinuationsAsynchronously);

        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            if (Interlocked.Increment(ref readCount) == 1)
            {
                buffer.Span[0] = 1;
                return 1;
            }
            SecondReadStarted.TrySetResult();
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return 0;
        }

        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override void Flush() => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
    }

    public void Dispose()
    {
        if (Directory.Exists(root)) Directory.Delete(root, recursive: true);
    }
}
