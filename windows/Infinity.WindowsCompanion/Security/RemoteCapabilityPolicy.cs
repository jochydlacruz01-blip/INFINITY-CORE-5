namespace Infinity.WindowsCompanion.Security;

/// <summary>Server-side administrator switches for capabilities that remain disabled by default.</summary>
public sealed record RemoteCapabilityPolicy(
    bool RemoteDisplayEnabled,
    bool RemoteInputEnabled,
    bool FileTransferEnabled)
{
    public static RemoteCapabilityPolicy LoadFromEnvironment() => FromValues(
        Environment.GetEnvironmentVariable("INFINITY_ENABLE_REMOTE_DISPLAY"),
        Environment.GetEnvironmentVariable("INFINITY_ENABLE_REMOTE_INPUT"),
        Environment.GetEnvironmentVariable("INFINITY_ENABLE_FILE_TRANSFER"));

    public static RemoteCapabilityPolicy FromValues(
        string? remoteDisplay,
        string? remoteInput,
        string? fileTransfer)
    {
        var displayEnabled = IsExplicitlyEnabled(remoteDisplay);
        var inputEnabled = IsExplicitlyEnabled(remoteInput);
        var filesEnabled = IsExplicitlyEnabled(fileTransfer);

        // RDP is bidirectional: display and input are an inseparable authorization pair.
        if (!displayEnabled || !inputEnabled)
        {
            displayEnabled = false;
            inputEnabled = false;
        }

        return new RemoteCapabilityPolicy(displayEnabled, inputEnabled, filesEnabled);
    }

    public IReadOnlySet<string> GetAllowedCapabilities()
    {
        var allowed = new HashSet<string>(StringComparer.Ordinal) { "identity.read" };
        if (RemoteDisplayEnabled && RemoteInputEnabled)
        {
            allowed.Add("remote.display");
            allowed.Add("remote.input");
        }
        if (FileTransferEnabled) allowed.Add("file.transfer");
        return allowed;
    }

    private static bool IsExplicitlyEnabled(string? value) =>
        string.Equals(value?.Trim(), "true", StringComparison.OrdinalIgnoreCase);
}
