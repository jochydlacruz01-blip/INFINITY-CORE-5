using System.Diagnostics;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using Infinity.WindowsCompanion.Security;
using Microsoft.Win32;

namespace Infinity.WindowsCompanion.Desktop;

public sealed class MainWindow : Window
{
    private const string ServiceName = "INFINITY Windows Companion";
    private readonly DataGrid devices = new()
    {
        AutoGenerateColumns = true,
        IsReadOnly = true,
        SelectionMode = DataGridSelectionMode.Single,
        Margin = new Thickness(0, 12, 0, 0),
        Background = new SolidColorBrush(Color.FromRgb(17, 27, 45)),
        Foreground = Brushes.White,
        RowBackground = new SolidColorBrush(Color.FromRgb(23, 32, 51)),
        AlternatingRowBackground = new SolidColorBrush(Color.FromRgb(27, 41, 64)),
        BorderThickness = new Thickness(0)
    };
    private readonly TextBlock serviceState = new() { FontSize = 16, FontWeight = FontWeights.SemiBold };
    private readonly TextBlock configState = new() { Foreground = Brushes.LightGray, Margin = new Thickness(0, 5, 0, 0) };
    private readonly TextBlock feedback = new() { Foreground = Brushes.LightGray, Margin = new Thickness(0, 12, 0, 0), TextWrapping = TextWrapping.Wrap };
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(8) };
    private bool refreshing;

    public MainWindow()
    {
        Title = "INFINITY Windows Companion";
        Width = 1180;
        Height = 760;
        MinWidth = 900;
        MinHeight = 620;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        Background = new SolidColorBrush(Color.FromRgb(11, 18, 32));
        Foreground = Brushes.White;

        var root = new Grid { Margin = new Thickness(22) };
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var header = new DockPanel();
        var titleStack = new StackPanel();
        titleStack.Children.Add(new TextBlock { Text = "INFINITY", Foreground = Brushes.DeepSkyBlue, FontWeight = FontWeights.Bold, FontSize = 13 });
        titleStack.Children.Add(new TextBlock { Text = "WINDOWS COMPANION", FontSize = 27, FontWeight = FontWeights.SemiBold });
        titleStack.Children.Add(new TextBlock { Text = "Panel local de seguridad y administración del equipo", Foreground = Brushes.LightGray });
        header.Children.Add(titleStack);
        var statusStack = new StackPanel { HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center };
        statusStack.Children.Add(serviceState);
        statusStack.Children.Add(configState);
        DockPanel.SetDock(statusStack, Dock.Right);
        header.Children.Add(statusStack);
        root.Children.Add(header);

        var toolbar = new WrapPanel { Margin = new Thickness(0, 18, 0, 14), Background = new SolidColorBrush(Color.FromRgb(23, 32, 51)) };
        AddButton(toolbar, "Actualizar", RefreshAsync);
        AddButton(toolbar, "Iniciar servicio", StartServiceAsync);
        AddButton(toolbar, "Detener servicio", StopServiceAsync);
        AddButton(toolbar, "Aprobar certificado…", ApproveAsync);
        AddButton(toolbar, "Bloquear", BlockAsync);
        AddButton(toolbar, "Habilitar", EnableAsync);
        AddButton(toolbar, "Revocar definitivamente", RevokeAsync, Color.FromRgb(115, 42, 52));
        Grid.SetRow(toolbar, 1);
        root.Children.Add(toolbar);

        Grid.SetRow(devices, 2);
        root.Children.Add(devices);
        feedback.Text = "La administración local requiere privilegios de administrador. Las funciones remotas permanecen deshabilitadas hasta superar la aceptación física.";
        Grid.SetRow(feedback, 3);
        root.Children.Add(feedback);
        Content = root;

        timer.Tick += async (_, _) => await RefreshAsync();
        Loaded += async (_, _) => { timer.Start(); await RefreshAsync(); };
        Closed += (_, _) => timer.Stop();
    }

    private static void AddButton(Panel panel, string label, Func<Task> action, Color? background = null)
    {
        var button = new Button
        {
            Content = label,
            Margin = new Thickness(4),
            Padding = new Thickness(12, 8, 12, 8),
            Foreground = Brushes.White,
            Background = new SolidColorBrush(background ?? Color.FromRgb(38, 54, 80)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(64, 85, 117)),
            Cursor = System.Windows.Input.Cursors.Hand
        };
        button.Click += async (_, _) => await action();
        panel.Children.Add(button);
    }

    private async Task RefreshAsync()
    {
        if (refreshing) return;
        refreshing = true;
        try
        {
            var deviceTask = Task.Run(() => new AuthorizedDeviceStore().ListAuthorizedDevices());
            var serviceTask = QueryServiceAsync();
            var currentDevices = await deviceTask;
            var service = await serviceTask;
            devices.ItemsSource = currentDevices;
            serviceState.Text = $"Servicio: {service.State}";
            configState.Text = ConfigurationSummary();
            feedback.Text = $"Dispositivos: {currentDevices.Count} · Última actualización: {DateTimeOffset.Now:T}";
        }
        catch (Exception error)
        {
            feedback.Text = $"No se pudo actualizar el panel: {error.Message}";
        }
        finally
        {
            refreshing = false;
        }
    }

    private static string ConfigurationSummary()
    {
        var address = Environment.GetEnvironmentVariable("INFINITY_BIND_ADDRESS");
        var port = Environment.GetEnvironmentVariable("INFINITY_PORT") ?? "7443";
        var deviceId = Environment.GetEnvironmentVariable("INFINITY_DEVICE_ID");
        var certificate = Environment.GetEnvironmentVariable("INFINITY_SERVER_CERTIFICATE_PFX");
        var secretFile = Environment.GetEnvironmentVariable("INFINITY_SERVER_CERTIFICATE_PASSWORD_FILE");
        var configured = !string.IsNullOrWhiteSpace(address) &&
                         !string.IsNullOrWhiteSpace(deviceId) &&
                         !string.IsNullOrWhiteSpace(certificate) &&
                         !string.IsNullOrWhiteSpace(secretFile);
        var capabilities = string.Join(", ",
            RemoteCapabilityPolicy.LoadFromEnvironment().GetAllowedCapabilities()
                .OrderBy(value => value, StringComparer.Ordinal));
        return configured
            ? $"Configuración presente · {address}:{port} · ID {deviceId} · secreto protegido por DPAPI · permisos activos: {capabilities}"
            : $"Configuración incompleta · ejecutar Install-INFINITYCompanion.ps1 como administrador · permisos actuales: {capabilities}";
    }

    private static async Task<(string State, string Output)> QueryServiceAsync()
    {
        var result = await RunScAsync("query", ServiceName);
        if (result.ExitCode != 0) return ("No instalado / inaccesible", result.Output);
        var match = Regex.Match(result.Output, @"STATE\s*:\s*\d+\s+(\w+)", RegexOptions.IgnoreCase);
        return (match.Success ? match.Groups[1].Value : "desconocido", result.Output);
    }

    private static async Task<(int ExitCode, string Output)> RunScAsync(params string[] arguments)
    {
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = "sc.exe",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true
            }
        };
        foreach (var argument in arguments) process.StartInfo.ArgumentList.Add(argument);
        if (!process.Start()) throw new InvalidOperationException("No se pudo iniciar Service Control Manager.");
        var stdout = process.StandardOutput.ReadToEndAsync();
        var stderr = process.StandardError.ReadToEndAsync();
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        try { await process.WaitForExitAsync(timeout.Token); }
        catch (OperationCanceledException)
        {
            try { process.Kill(entireProcessTree: true); } catch (InvalidOperationException) { }
            throw new TimeoutException("La operación del servicio excedió 15 segundos.");
        }
        return (process.ExitCode, (await stdout) + (await stderr));
    }

    private AuthorizedDeviceStore.AuthorizedDeviceInfo? SelectedDevice() =>
        devices.SelectedItem as AuthorizedDeviceStore.AuthorizedDeviceInfo;

    private async Task RunAdminActionAsync(string success, Func<Task> action)
    {
        try
        {
            await action();
            feedback.Text = success;
            await RefreshAsync();
        }
        catch (Exception error)
        {
            feedback.Text = $"Operación no completada: {error.Message}";
            MessageBox.Show(this, error.Message, "INFINITY Windows Companion", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private Task ApproveAsync()
    {
        var dialog = new OpenFileDialog
        {
            Title = "Seleccionar certificado cliente emitido por INFINITY",
            Filter = "Certificados X.509 (*.cer)|*.cer",
            CheckFileExists = true,
            Multiselect = false
        };
        if (dialog.ShowDialog(this) != true) return Task.CompletedTask;
        var path = dialog.FileName;
        return RunAdminActionAsync(
            "Certificado aprobado. La emisión y la autorización son operaciones separadas.",
            () => Task.Run(() => AuthorizedDeviceStore.ApproveFromLocalConsole(path)));
    }

    private Task BlockAsync()
    {
        var device = SelectedDevice();
        if (device is null) { feedback.Text = "Selecciona un dispositivo."; return Task.CompletedTask; }
        if (MessageBox.Show(this, $"¿Bloquear temporalmente {device.DeviceId}?", "Confirmar bloqueo", MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes)
            return Task.CompletedTask;
        return RunAdminActionAsync("Dispositivo bloqueado; las sesiones activas se cancelan.", () =>
            Task.Run(() => AuthorizedDeviceStore.BlockFromLocalConsole(device.Fingerprint)));
    }

    private Task EnableAsync()
    {
        var device = SelectedDevice();
        if (device is null) { feedback.Text = "Selecciona un dispositivo."; return Task.CompletedTask; }
        return RunAdminActionAsync("Dispositivo habilitado. Los certificados revocados no se pueden reactivar.", () =>
            Task.Run(() => AuthorizedDeviceStore.EnableFromLocalConsole(device.Fingerprint)));
    }

    private Task RevokeAsync()
    {
        var device = SelectedDevice();
        if (device is null) { feedback.Text = "Selecciona un dispositivo."; return Task.CompletedTask; }
        if (MessageBox.Show(this, $"La revocación es definitiva para este certificado. ¿Revocar {device.DeviceId}?", "Confirmar revocación", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes)
            return Task.CompletedTask;
        return RunAdminActionAsync("Certificado revocado; las sesiones y operaciones activas se cancelan.", () =>
            Task.Run(() => AuthorizedDeviceStore.RevokeFromLocalConsole(device.Fingerprint)));
    }

    private Task StartServiceAsync() => RunAdminActionAsync(
        "Solicitud de inicio enviada al Service Control Manager.",
        async () =>
        {
            var result = await RunScAsync("start", ServiceName);
            if (result.ExitCode != 0 && !result.Output.Contains("1056", StringComparison.Ordinal))
                throw new InvalidOperationException(result.Output);
        });

    private Task StopServiceAsync()
    {
        if (MessageBox.Show(this, "Detener el servicio desconectará las sesiones activas.", "Confirmar cierre", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes)
            return Task.CompletedTask;
        return RunAdminActionAsync(
            "Solicitud de detención enviada; el servicio cerrará sus sesiones activas.",
            async () =>
            {
                var result = await RunScAsync("stop", ServiceName);
                if (result.ExitCode != 0 && !result.Output.Contains("1062", StringComparison.Ordinal))
                    throw new InvalidOperationException(result.Output);
            });
    }
}
