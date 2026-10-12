using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Linkora.Protocol;
using Linkora.Windows.Security;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
namespace Linkora.Windows.Networking;
public sealed record PairingPrompt(string Name, string Code);
public sealed class CompanionServer
{
    private readonly LocalIdentity identity;
    private readonly PairingTokenService tokens = new();
    private readonly SemaphoreSlim sessionGate = new(1, 1);
    private WebApplication? app;
    private WebSocket? active;
    private TaskCompletionSource<bool>? approval;
    public event Action<string>? Log;
    public event Action<PairingPrompt?>? Prompt;
    public event Action<string?>? Connected;
    public event Action? TokenConsumed;
    public PairingPayload? Payload { get; private set; }
    public CompanionServer(LocalIdentity identity) => this.identity = identity;
    public static bool IsPrivate(IPAddress ip) {
        var b = ip.GetAddressBytes();
        return b.Length == 4 && (b[0] == 10 || b[0] == 172 && b[1] >= 16 && b[1] <= 31 || b[0] == 192 && b[1] == 168);
    }
    public static IPAddress[] Addresses() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
        .SelectMany(n => n.GetIPProperties().UnicastAddresses).Select(x => x.Address)
        .Where(IsPrivate).Distinct().ToArray();
    public PairingPayload NewCode(IPAddress address) {
        if (active != null) throw new InvalidOperationException("Disconnect the current session before generating a new QR code.");
        var lease = tokens.Create(DateTimeOffset.UtcNow);
        return Payload = new(1, address.ToString(), 43127, identity.Id, lease.Token, lease.ExpiresAt.ToString("O"));
    }
    public async Task StartAsync(IPAddress address) {
        if (!Addresses().Contains(address)) throw new InvalidOperationException("Select an active private LAN IPv4 address.");
        if (app != null) return;
        var builder = WebApplication.CreateSlimBuilder(new WebApplicationOptions { Args = Array.Empty<string>() });
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(k => {
            k.Limits.MaxConcurrentConnections = 8;
            k.Limits.MaxConcurrentUpgradedConnections = 2;
            k.Limits.RequestHeadersTimeout = TimeSpan.FromSeconds(10);
            k.Listen(address, 43127, o => o.UseHttps(identity.Certificate, h => h.SslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13));
        });
        var host = builder.Build();
        host.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(20) });
        host.Run(HandleAsync);
        try { await host.StartAsync(); app = host; NewCode(address); Log?.Invoke("TLS server started on selected private LAN address."); }
        catch { await host.DisposeAsync(); throw; }
    }
    public void Decide(bool accepted) => approval?.TrySetResult(accepted);
    public void Disconnect() { approval?.TrySetResult(false); active?.Abort(); }
    public async Task StopAsync() {
        tokens.Invalidate(); Payload = null; Disconnect();
        if (app is {} host) { app = null; using var stop = new CancellationTokenSource(TimeSpan.FromSeconds(3)); await host.StopAsync(stop.Token); await host.DisposeAsync(); }
        Log?.Invoke("Server stopped.");
    }
    private static async Task Send(WebSocket ws, object message, CancellationToken ct) =>
        await ws.SendAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(message, message.GetType(), JsonCodec.Options)), WebSocketMessageType.Text, true, ct);
    private static async Task<JsonDocument> Receive(WebSocket ws, CancellationToken ct) {
        using var output = new MemoryStream(); var buffer = new byte[4096];
        WebSocketReceiveResult result;
        do {
            result = await ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct);
            if (result.MessageType != WebSocketMessageType.Text || output.Length + result.Count > 16384) throw new InvalidDataException();
            output.Write(buffer, 0, result.Count);
        } while (!result.EndOfMessage);
        var document = JsonDocument.Parse(output.ToArray(), new JsonDocumentOptions { MaxDepth = 8 });
        try {
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object || root.EnumerateObject().GroupBy(x => x.Name).Any(g => g.Count() > 1)
                || root.GetProperty("protocolVersion").GetInt32() != 1 || root.GetProperty("type").ValueKind != JsonValueKind.String) throw new InvalidDataException();
            return document;
        } catch { document.Dispose(); throw; }
    }
    private static string Field(JsonElement root, string name, int maximum = 256) {
        var s = root.GetProperty(name).GetString();
        if (string.IsNullOrWhiteSpace(s) || s.Length > maximum || s.Any(char.IsControl)) throw new InvalidDataException();
        return s;
    }
    private async Task HandleAsync(HttpContext context) {
        if (context.Request.Path != ProtocolConstants.WebSocketPath || !context.WebSockets.IsWebSocketRequest
            || context.Connection.RemoteIpAddress is not {} remote || !IsPrivate(remote)) { context.Response.StatusCode = 403; return; }
        if (!await sessionGate.WaitAsync(0)) { context.Response.StatusCode = 409; return; }
        try {
            using var ws = await context.WebSockets.AcceptWebSocketAsync(); active = ws;
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted);
            timeout.CancelAfter(TimeSpan.FromSeconds(20));
            using var first = await Receive(ws, timeout.Token);
            var root = first.RootElement;
            if (Field(root, "type") != "pair_request") {
                await Send(ws, new ErrorProtocolMessage("PAIRING_REQUIRED", "Scan a new QR code. Device ID alone cannot authenticate reconnection."), timeout.Token);
                return;
            }
            var id = Field(root, "deviceId"); var name = Field(root, "deviceName", 100);
            if (!tokens.TryConsume(Field(root, "pairingToken", 128), DateTimeOffset.UtcNow, out var lease)) {
                await Send(ws, new PairCompleteMessage(false, null, null), timeout.Token); return;
            }
            Payload = null; TokenConsumed?.Invoke();
            var remaining = lease!.ExpiresAt - DateTimeOffset.UtcNow;
            if (remaining <= TimeSpan.Zero) return;
            timeout.CancelAfter(remaining);
            var challenge = Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();
            var digits = RandomNumberGenerator.GetInt32(1000000).ToString("D6");
            var code = digits[..3] + "-" + digits[3..];
            approval = new(TaskCreationOptions.RunContinuationsAsynchronously);
            Prompt?.Invoke(new(name, code)); Log?.Invoke("Pairing request awaiting approval on both devices.");
            await Send(ws, new PairChallengeMessage(challenge, code), timeout.Token);
            var phoneConfirmed = false;
            while (!phoneConfirmed) {
                using var message = await Receive(ws, timeout.Token); var m = message.RootElement;
                var type = Field(m, "type");
                if (type == "ping") { await Send(ws, new PongMessage(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()), timeout.Token); continue; }
                if (type != "pair_confirm" || Field(m, "deviceId") != id || Field(m, "challengeResponse") != challenge) throw new InvalidDataException();
                phoneConfirmed = true;
            }
            if (!await approval.Task.WaitAsync(timeout.Token) || DateTimeOffset.UtcNow >= lease.ExpiresAt) {
                await Send(ws, new PairCompleteMessage(false, null, null), timeout.Token); return;
            }
            identity.SaveTrust(new(id, name, DateTimeOffset.UtcNow));
            await Send(ws, new PairCompleteMessage(true, identity.Id, Environment.MachineName), timeout.Token);
            Prompt?.Invoke(null); Connected?.Invoke(name); Log?.Invoke("Pairing completed with approval on both devices.");
            while (ws.State == WebSocketState.Open) {
                timeout.CancelAfter(TimeSpan.FromSeconds(65));
                using var message = await Receive(ws, timeout.Token); var m = message.RootElement;
                switch (Field(m, "type")) {
                    case "ping": await Send(ws, new PongMessage(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()), timeout.Token); break;
                    case "pong": case "battery_status": case "device_info": break;
                    case "file_offer":
                        await Send(ws, new { type = "file_reject", protocolVersion = 1, transferId = Field(m, "transferId"), reason = "File transfer is not implemented in this pairing milestone." }, timeout.Token); break;
                    case "clipboard_send":
                        await Send(ws, new { type = "clipboard_result", protocolVersion = 1, success = false, message = "Clipboard sharing is not implemented in this milestone." }, timeout.Token); break;
                    default: await Send(ws, new ErrorProtocolMessage("UNSUPPORTED", "Feature not implemented in this pairing milestone."), timeout.Token); break;
                }
            }
        } catch (Exception ex) when (ex is not OutOfMemoryException) { Log?.Invoke("Session ended or rejected. Check pairing expiry, protocol and network connection."); }
        finally { active = null; approval = null; Prompt?.Invoke(null); Connected?.Invoke(null); sessionGate.Release(); }
    }
}
