using System.Net.Sockets;
using System.Net.WebSockets;
using Microsoft.AspNetCore.Http;

namespace Infinity.WindowsCompanion.Security;

/// <summary>Closes both ends of an active RDP tunnel when its operation token is cancelled.</summary>
public static class TunnelRevocationBinding
{
    /// <summary>
    /// Cancels a pending WebSocket upgrade as well as the already-open loopback RDP socket.
    /// The regular Bind method takes over after the WebSocket has been accepted.
    /// </summary>
    public static CancellationTokenRegistration BindPendingHandshake(
        CancellationToken cancellationToken,
        HttpContext httpContext,
        TcpClient rdpClient)
    {
        ArgumentNullException.ThrowIfNull(httpContext);
        ArgumentNullException.ThrowIfNull(rdpClient);
        return cancellationToken.Register(static state =>
        {
            var resources = ((HttpContext HttpContext, TcpClient TcpClient))state!;
            try { resources.HttpContext.Abort(); }
            catch (ObjectDisposedException) { }
            finally
            {
                try { resources.TcpClient.Close(); }
                catch (ObjectDisposedException) { }
            }
        }, (httpContext, rdpClient));
    }

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
            catch (ObjectDisposedException) { }
            finally
            {
                try { resources.TcpClient.Close(); }
                catch (ObjectDisposedException) { }
            }
        }, (webSocket, rdpClient));
    }
}
