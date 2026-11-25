using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class TunnelRevocationBindingTests
{
    [Fact]
    public async Task RevocationAbortsWebSocketAndClosesTcpSocket()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        using var remote = await listener.AcceptTcpClientAsync(
            new CancellationTokenSource(TimeSpan.FromSeconds(5)).Token);
        using var local = new TcpClient();
        await local.ConnectAsync((IPEndPoint)listener.LocalEndpoint);
        listener.Stop();

        using var cancellation = new CancellationTokenSource();
        using var webSocket = new RecordingWebSocket();
        using var binding = TunnelRevocationBinding.Bind(cancellation.Token, webSocket, local);

        cancellation.Cancel();

        Assert.Equal(WebSocketState.Aborted, webSocket.State);
        Assert.True(local.Client.SafeHandle.IsClosed);
        using var readTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var read = await remote.GetStream().ReadAsync(new byte[1], readTimeout.Token);
        Assert.Equal(0, read);
    }

    private sealed class RecordingWebSocket : WebSocket
    {
        public override WebSocketCloseStatus? CloseStatus => null;
        public override string? CloseStatusDescription => null;
        private WebSocketState state = WebSocketState.Open;
        public override WebSocketState State => state;
        public override string? SubProtocol => null;
        public override void Abort() => state = WebSocketState.Aborted;
        public override void Dispose() => state = WebSocketState.Closed;
        public override Task CloseAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken)
        {
            state = WebSocketState.Closed;
            return Task.CompletedTask;
        }
        public override Task CloseOutputAsync(WebSocketCloseStatus closeStatus, string? statusDescription, CancellationToken cancellationToken)
        {
            State = WebSocketState.Closed;
            return Task.CompletedTask;
        }
        public override Task<WebSocketReceiveResult> ReceiveAsync(ArraySegment<byte> buffer, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
        public override Task SendAsync(ArraySegment<byte> buffer, WebSocketMessageType messageType, bool endOfMessage, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }
}
