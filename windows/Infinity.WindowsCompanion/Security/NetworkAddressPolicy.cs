using System.Net;
using System.Net.Sockets;

namespace Infinity.WindowsCompanion.Security;

public static class NetworkAddressPolicy
{
    public static bool IsAllowedBindAddress(IPAddress address)
    {
        if (IPAddress.IsLoopback(address)) return true;

        if (address.AddressFamily == AddressFamily.InterNetworkV6)
        {
            if (address.IsIPv4MappedToIPv6)
                return IsAllowedBindAddress(address.MapToIPv4());

            var bytes = address.GetAddressBytes();
            return address.IsIPv6LinkLocal || (bytes[0] & 0xFE) == 0xFC;
        }

        if (address.AddressFamily != AddressFamily.InterNetwork) return false;

        var octets = address.GetAddressBytes();
        return octets[0] == 10 ||
               (octets[0] == 172 && octets[1] is >= 16 and <= 31) ||
               (octets[0] == 192 && octets[1] == 168) ||
               (octets[0] == 100 && octets[1] is >= 64 and <= 127);
    }
}
