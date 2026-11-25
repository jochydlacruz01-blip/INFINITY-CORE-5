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
        using var local = new TcpClient();
        await local.ConnectAsync((IPEndPoint)listener.LocalEndpoint);
        using var remote = await listener.AcceptTcpClientAsync(
            new CancellationTokenSource(TimeSpan.FromSeconds(5)).Token);
        listener.Stop();

        var localSocket = local.Client;
        const string devicePin = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        var registry = new DeviceOperationRegistry();
        registry.ActivateDevice(devicePin);
        const string sessionId = "44444444-4444-4444-8444-444444444444";
        registry.ActivateSession(devicePin, sessionId);
        using var operation = registry.BeginOperation(devicePin, sessionId);
        using var webSocket = new RecordingWebSocket();
        using var binding = TunnelRevocationBinding.Bind(operation.Token, webSocket, local);

        Assert.True(registry.RevokeDevice(devicePin));

        Assert.Equal(WebSocketState.Aborted, webSocket.State);
        Assert.True(localSocket.SafeHandle.IsClosed);
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
            state = WebSocketState.Closed;
            return Task.CompletedTask;
        }
        public override Task<WebSocketReceiveResult> ReceiveAsync(ArraySegment<byte> buffer, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
        public override Task SendAsync(ArraySegment<byte> buffer, WebSocketMessageType messageType, bool endOfMessage, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }
}
