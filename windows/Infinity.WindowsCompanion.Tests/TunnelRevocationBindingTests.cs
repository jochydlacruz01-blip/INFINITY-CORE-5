using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
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


    [Fact]
    public async Task RevocationAbortsLiveLoopbackWebSocketAndClosesRdpSocket()
    {
        const string devicePin = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        const string sessionId = "55555555-5555-4555-8555-555555555555";
        var registry = new DeviceOperationRegistry();
        registry.ActivateDevice(devicePin);
        registry.ActivateSession(devicePin, sessionId);
        using var operation = registry.BeginOperation(devicePin, sessionId);

        var rdpListener = new TcpListener(IPAddress.Loopback, 0);
        rdpListener.Start();
        using var rdpClient = new TcpClient();
        await rdpClient.ConnectAsync((IPEndPoint)rdpListener.LocalEndpoint);
        using var rdpPeer = await rdpListener.AcceptTcpClientAsync(
            new CancellationTokenSource(TimeSpan.FromSeconds(5)).Token);
        rdpListener.Stop();

        var portProbe = new TcpListener(IPAddress.Loopback, 0);
        portProbe.Start();
        var port = ((IPEndPoint)portProbe.LocalEndpoint).Port;
        portProbe.Stop();

        var accepted = new TaskCompletionSource<WebSocket>(TaskCreationOptions.RunContinuationsAsynchronously);
        var ended = new TaskCompletionSource<WebSocketState>(TaskCreationOptions.RunContinuationsAsynchronously);
        var localRdpSocket = rdpClient.Client;
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        var builder = WebApplication.CreateBuilder();
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options => options.Listen(IPAddress.Loopback, port));
        var app = builder.Build();
        app.UseWebSockets();
        app.Map("/ws", async context =>
        {
            using var serverSocket = await context.WebSockets.AcceptWebSocketAsync();
            using var binding = TunnelRevocationBinding.Bind(operation.Token, serverSocket, rdpClient);
            accepted.TrySetResult(serverSocket);
            try
            {
                await serverSocket.ReceiveAsync(new byte[32].AsMemory(), timeout.Token);
            }
            catch (WebSocketException) { }
            catch (OperationCanceledException) { }
            finally
            {
                ended.TrySetResult(serverSocket.State);
            }
        });

        await app.StartAsync(timeout.Token);
        try
        {
            using var clientSocket = new ClientWebSocket();
            await clientSocket.ConnectAsync(new Uri($"ws://127.0.0.1:{port}/ws"), timeout.Token);
            var serverSocket = await accepted.Task.WaitAsync(TimeSpan.FromSeconds(5));
            Assert.Equal(WebSocketState.Open, clientSocket.State);
            Assert.Equal(WebSocketState.Open, serverSocket.State);

            Assert.True(registry.RevokeDevice(devicePin));
            var terminalState = await ended.Task.WaitAsync(TimeSpan.FromSeconds(5));

            Assert.Equal(WebSocketState.Aborted, terminalState);
            Assert.True(localRdpSocket.SafeHandle.IsClosed);
            using var peerTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            Assert.Equal(0, await rdpPeer.GetStream().ReadAsync(new byte[1], peerTimeout.Token));
            await Assert.ThrowsAnyAsync<WebSocketException>(async () =>
                await clientSocket.ReceiveAsync(new byte[1].AsMemory(), timeout.Token));
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }
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
