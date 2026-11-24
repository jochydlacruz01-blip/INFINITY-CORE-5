using System.Security.Cryptography;
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

        var metadata = store.Find(receipt.FileId, DevicePin)!;
        await using var downloaded = store.OpenRead(metadata)!;
        using var output = new MemoryStream();
        await downloaded.CopyToAsync(output);
        Assert.Equal(bytes, output.ToArray());
        Assert.False(store.Delete(receipt.FileId, OtherDevicePin));
        Assert.True(store.Delete(receipt.FileId, DevicePin));
        Assert.Null(store.Find(receipt.FileId, DevicePin));
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

    public void Dispose()
    {
        if (Directory.Exists(root)) Directory.Delete(root, recursive: true);
    }
}
