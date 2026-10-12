using System.IO;
using System.Net;
using System.Windows;
using System.Windows.Media.Imaging;
using Linkora.Protocol;
using Linkora.Windows.Networking;
using Linkora.Windows.Security;
using QRCoder;
namespace Linkora.Windows;
public partial class MainWindow : Window
{
    private readonly LocalIdentity identity;
    private readonly CompanionServer server;
    private PairingPayload? payload;
    public MainWindow()
    {
        InitializeComponent();
        identity = new LocalIdentity(); server = new CompanionServer(identity);
        server.Log += message => Dispatcher.Invoke(() => Log(message));
        server.Prompt += prompt => Dispatcher.Invoke(() => ShowPrompt(prompt));
        server.Connected += name => Dispatcher.Invoke(() => SetConnected(name));
        server.TokenConsumed += () => Dispatcher.Invoke(() => { CopyJsonButton.IsEnabled = false; });
        Closed += async (_, _) => { await server.StopAsync(); identity.Dispose(); };
    }
    private async void StartButton_Click(object sender, RoutedEventArgs e)
    {
        try { var address = CompanionServer.Addresses().FirstOrDefault() ?? throw new InvalidOperationException("No private LAN IPv4 address was found. Connect this PC and phone to the same Wi-Fi first."); await server.StartAsync(address); payload = server.Payload; RenderPayload(); StartButton.IsEnabled = false; StopButton.IsEnabled = true; StatusText.Text = "Running"; StatusText.Foreground = (System.Windows.Media.Brush)FindResource("Success"); Log("Ready. Scan the QR code from Android."); }
        catch (Exception ex) { MessageBox.Show(ex.Message, "Linkora", MessageBoxButton.OK, MessageBoxImage.Error); Log("Could not start the server."); }
    }
    private async void StopButton_Click(object sender, RoutedEventArgs e) { await server.StopAsync(); payload = null; QrImage.Source = null; HostText.Text = "Not running"; FingerprintText.Text = "—"; CopyJsonButton.IsEnabled = false; StartButton.IsEnabled = true; StopButton.IsEnabled = false; StatusText.Text = "Stopped"; StatusText.Foreground = (System.Windows.Media.Brush)FindResource("TextMuted"); ShowPrompt(null); SetConnected(null); }
    private void RenderPayload() { if (payload is null) return; HostText.Text = $"{payload.Host}:{payload.Port}"; FingerprintText.Text = payload.CertificateSha256; CopyJsonButton.IsEnabled = true; var bytes = new PngByteQRCode(QRCodeGenerator.GenerateQrCode(JsonCodec.Serialize(payload), QRCodeGenerator.ECCLevel.Q)).GetGraphic(8); using var stream = new MemoryStream(bytes); var image = new BitmapImage(); image.BeginInit(); image.CacheOption = BitmapCacheOption.OnLoad; image.StreamSource = stream; image.EndInit(); QrImage.Source = image; }
    private void CopyJsonButton_Click(object sender, RoutedEventArgs e) { if (payload is not null) Clipboard.SetText(JsonCodec.Serialize(payload)); }
    private void ApproveButton_Click(object sender, RoutedEventArgs e) { server.Decide(true); Log("Approval sent. Waiting for Android confirmation if needed."); }
    private void RejectButton_Click(object sender, RoutedEventArgs e) { server.Decide(false); Log("Pairing rejected by user."); }
    private void DisconnectButton_Click(object sender, RoutedEventArgs e) { server.Disconnect(); }
    private void RevokeButton_Click(object sender, RoutedEventArgs e) { if (MessageBox.Show("Revoke the trusted Android device? It must pair again.", "Linkora", MessageBoxButton.YesNo, MessageBoxImage.Warning) == MessageBoxResult.Yes) { identity.Revoke(); server.Disconnect(); SetConnected(null); Log("Trusted device revoked."); } }
    private void ShowPrompt(PairingPrompt? prompt) { PendingBorder.Visibility = prompt is null ? Visibility.Collapsed : Visibility.Visible; if (prompt is not null) { PendingDeviceText.Text = $"Android device: {prompt.Name}"; VerificationCodeText.Text = prompt.Code; } }
    private void SetConnected(string? name) { ConnectedText.Text = name is null ? "No trusted Android device connected" : $"Connected: {name}"; DisconnectButton.IsEnabled = name is not null; RevokeButton.IsEnabled = name is not null; }
    private void Log(string message) { LogText.AppendText($"[{DateTime.Now:HH:mm:ss}] {message}{Environment.NewLine}"); LogText.ScrollToEnd(); }
}
