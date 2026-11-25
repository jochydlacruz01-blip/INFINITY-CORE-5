using System.Net.Sockets;
using System.Net.WebSockets;

namespace Infinity.WindowsCompanion.Security;

/// <summary>Closes both ends of an active RDP tunnel when its operation token is cancelled.</summary>
public static class TunnelRevocationBinding
{
    public static CancellationTokenRegistration Bind(
        CancellationToken cancellationToken,
        WebSocket webSocket,
        TcpClient rdpClient)
    {
        ArgumentNullException.ThrowIfNull(webSocket);
        ArgumentNullException.ThrowIfNull(rdpClient);
        return cancellationToken.Register(static state =>
        {
            var resources = ((WebSocket WebSocket, TcpClient TcpClient))state!;
            try { resources.WebSocket.Abort(); }
            finally { resources.TcpClient.Close(); }
        }, (webSocket, rdpClient));
    }
}
