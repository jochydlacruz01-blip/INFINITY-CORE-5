using System.Net;

namespace Infinity.WindowsCompanion.Security;

public sealed record CompanionSettings(
    IPAddress BindAddress,
    int Port,
    string DeviceId,
    string ServerCertificatePath,
    string ServerCertificatePassword)
{
    public static CompanionSettings LoadFromEnvironment()
    {
        var bindText = Required("INFINITY_BIND_ADDRESS");
        if (!IPAddress.TryParse(bindText, out var bindAddress) ||
            !NetworkAddressPolicy.IsAllowedBindAddress(bindAddress))
        {
            throw new InvalidOperationException(
                "INFINITY_BIND_ADDRESS must be loopback or a private/VPN interface address. Public addresses are rejected.");
        }

        var portText = Environment.GetEnvironmentVariable("INFINITY_PORT") ?? "7443";
        if (!int.TryParse(portText, out var port) || port is < 1024 or > 65535)
        {
            throw new InvalidOperationException("INFINITY_PORT must be between 1024 and 65535.");
        }

        var deviceId = Required("INFINITY_DEVICE_ID");
        if (!Guid.TryParse(deviceId, out var parsedDeviceId) || parsedDeviceId == Guid.Empty)
        {
            throw new InvalidOperationException("INFINITY_DEVICE_ID must be a non-empty GUID.");
        }

        var certificatePath = Path.GetFullPath(Required("INFINITY_SERVER_CERTIFICATE_PFX"));
        if (!File.Exists(certificatePath))
        {
            throw new FileNotFoundException("The configured TLS certificate file was not found.", certificatePath);
        }

        var certificatePassword = Required("INFINITY_SERVER_CERTIFICATE_PASSWORD");
        return new CompanionSettings(
            bindAddress,
            port,
            parsedDeviceId.ToString("D"),
            certificatePath,
            certificatePassword);
    }

    private static string Required(string name)
    {
        var value = Environment.GetEnvironmentVariable(name);
        return string.IsNullOrWhiteSpace(value)
            ? throw new InvalidOperationException($"Required environment variable {name} is missing.")
            : value.Trim();
    }
}
