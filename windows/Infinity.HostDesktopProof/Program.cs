using System.ComponentModel;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

namespace Infinity.HostDesktopProof;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        if (!OperatingSystem.IsWindows())
            throw new PlatformNotSupportedException("This proof runs only on Windows.");
        ApplicationConfiguration.Initialize();
        Application.Run(new ProofForm());
    }
}

internal sealed class ProofForm : Form
{
    private readonly CheckBox _consent = new()
    {
        AutoSize = true,
        Text = "Confirmo que soy el usuario de este equipo y autorizo esta prueba local.",
        Dock = DockStyle.Top
    };
    private readonly PictureBox _preview = new()
    {
        Dock = DockStyle.Fill,
        SizeMode = PictureBoxSizeMode.Zoom,
        BorderStyle = BorderStyle.FixedSingle,
        BackColor = Color.FromArgb(22, 26, 34)
    };
    private readonly Label _status = new() { AutoSize = true, Dock = DockStyle.Fill };
    private readonly NumericUpDown _x = new() { Minimum = -100000, Maximum = 100000, Width = 90 };
    private readonly NumericUpDown _y = new() { Minimum = -100000, Maximum = 100000, Width = 90 };
    private readonly TextBox _text = new() { Width = 220, PlaceholderText = "Texto de prueba" };
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 500 };
    private bool _captureRunning;

    internal ProofForm()
    {
        Text = "INFINITY HOST AGENT — Desktop Proof (local only)";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(900, 650);
        Size = new Size(1100, 760);

        var start = new Button { Text = "Iniciar captura", AutoSize = true };
        var stop = new Button { Text = "Detener captura", AutoSize = true, Enabled = false };
        var click = new Button { Text = "Enviar clic", AutoSize = true };
        var type = new Button { Text = "Enviar texto (3 s)", AutoSize = true };
        var coordinates = new FlowLayoutPanel { Dock = DockStyle.Top, AutoSize = true, WrapContents = true };
        coordinates.Controls.AddRange(new Control[]
        {
            new Label { Text = "X:", AutoSize = true, Padding = new Padding(0, 7, 0, 0) }, _x,
            new Label { Text = "Y:", AutoSize = true, Padding = new Padding(0, 7, 0, 0) }, _y,
            click, _text, type
        });
        var controls = new FlowLayoutPanel { Dock = DockStyle.Top, AutoSize = true, WrapContents = true };
        controls.Controls.AddRange(new Control[] { start, stop });
        var header = new Panel { Dock = DockStyle.Top, AutoSize = true };
        header.Controls.Add(coordinates);
        header.Controls.Add(controls);
        header.Controls.Add(_consent);
        Controls.Add(_preview);
        Controls.Add(_status);
        Controls.Add(header);

        start.Click += (_, _) => StartCapture(stop, start);
        stop.Click += (_, _) => StopCapture(stop, start);
        click.Click += (_, _) => SendClick();
        type.Click += async (_, _) => await SendTextAsync();
        _timer.Tick += (_, _) => CaptureFrame();
        FormClosing += (_, _) => StopCapture(stop, start);
        _status.Text = "Inactivo. Sin conexiones de red; requiere consentimiento local.";
    }

    private void StartCapture(Button stop, Button start)
    {
        if (!_consent.Checked)
        {
            MessageBox.Show(this, "Confirma el consentimiento local antes de iniciar.", "INFINITY", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        var bounds = SystemInformation.VirtualScreen;
        if (bounds.Width <= 0 || bounds.Height <= 0)
        {
            _status.Text = "No se pudo determinar el área de escritorio.";
            return;
        }

        _x.Minimum = bounds.Left;
        _x.Maximum = bounds.Right - 1;
        _y.Minimum = bounds.Top;
        _y.Maximum = bounds.Bottom - 1;
        _captureRunning = true;
        start.Enabled = false;
        stop.Enabled = true;
        _timer.Start();
        CaptureFrame();
    }

    private void StopCapture(Button stop, Button start)
    {
        _timer.Stop();
        _captureRunning = false;
        if (!start.IsDisposed) start.Enabled = true;
        if (!stop.IsDisposed) stop.Enabled = false;
        _status.Text = "Captura detenida. No hay sesión remota ni listener de red.";
    }

    private void CaptureFrame()
    {
        if (!_captureRunning || IsDisposed) return;
        try
        {
            var bounds = SystemInformation.VirtualScreen;
            using var bitmap = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format24bppRgb);
            using (var graphics = Graphics.FromImage(bitmap))
                graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size, CopyPixelOperation.SourceCopy);

            var replacement = (Bitmap)bitmap.Clone();
            var previous = _preview.Image;
            _preview.Image = replacement;
            previous?.Dispose();
            _status.Text = $"Captura local activa · {bounds.Width} × {bounds.Height} · {DateTime.Now:T}";
        }
        catch (Exception ex) when (ex is Win32Exception or ArgumentException)
        {
            _timer.Stop();
            _captureRunning = false;
            _status.Text = $"Captura bloqueada o no disponible: {ex.Message}";
        }
    }

    private void SendClick()
    {
        if (!RequireConsentAndCapture()) return;
        var bounds = SystemInformation.VirtualScreen;
        var screenX = checked((int)_x.Value);
        var screenY = checked((int)_y.Value);
        if (!bounds.Contains(screenX, screenY))
        {
            _status.Text = "Coordenadas fuera del escritorio virtual.";
            return;
        }

        var normalizedX = (int)Math.Round((screenX - bounds.Left) * 65535d / Math.Max(1, bounds.Width - 1));
        var normalizedY = (int)Math.Round((screenY - bounds.Top) * 65535d / Math.Max(1, bounds.Height - 1));
        var inputs = new[]
        {
            Input.MouseMove(normalizedX, normalizedY),
            Input.MouseButton(MouseEvent.LeftDown),
            Input.MouseButton(MouseEvent.LeftUp)
        };
        if (Native.SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>()) != (uint)inputs.Length)
            _status.Text = $"SendInput no completó el clic. Win32={Marshal.GetLastWin32Error()}";
        else
            _status.Text = $"Clic local enviado a ({screenX}, {screenY}).";
    }

    private async Task SendTextAsync()
    {
        if (!RequireConsentAndCapture()) return;
        var value = _text.Text;
        if (string.IsNullOrEmpty(value))
        {
            _status.Text = "Escribe un texto de prueba primero.";
            return;
        }

        _status.Text = "La ventana se ocultará durante 3 segundos. Selecciona el campo de prueba en otra aplicación.";
        Hide();
        await Task.Delay(TimeSpan.FromSeconds(3));
        try
        {
            var inputs = new List<Input>();
            foreach (var character in value)
            {
                inputs.Add(Input.Unicode(character, KeyEvent.None));
                inputs.Add(Input.Unicode(character, KeyEvent.KeyUp));
            }
            var sent = Native.SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<Input>());
            Show();
            Activate();
            _status.Text = sent == (uint)inputs.Count
                ? "Eventos Unicode enviados. Comprueba el campo de prueba seleccionado."
                : $"Entrada de teclado incompleta. Win32={Marshal.GetLastWin32Error()}";
        }
        catch
        {
            Show();
            throw;
        }
    }

    private bool RequireConsentAndCapture()
    {
        if (_consent.Checked && _captureRunning) return true;
        MessageBox.Show(this, "La captura debe estar activa y el consentimiento local confirmado.", "INFINITY", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        return false;
    }
}

internal static class Native
{
    [DllImport("user32.dll", SetLastError = true)]
    internal static extern uint SendInput(uint numberOfInputs, Input[] inputs, int size);
}

[StructLayout(LayoutKind.Sequential)]
internal struct Input
{
    internal uint Type;
    internal InputUnion Data;

    internal static Input MouseMove(int x, int y) => new()
    {
        Type = 0,
        Data = new InputUnion { Mouse = new MouseInput { X = x, Y = y, Flags = MouseEvent.Move | MouseEvent.Absolute | MouseEvent.VirtualDesk } }
    };

    internal static Input MouseButton(MouseEvent flags) => new()
    {
        Type = 0,
        Data = new InputUnion { Mouse = new MouseInput { Flags = flags } }
    };

    internal static Input Unicode(char value, KeyEvent flags) => new()
    {
        Type = 1,
        Data = new InputUnion { Keyboard = new KeyboardInput { Scan = value, Flags = KeyEvent.Unicode | flags } }
    };
}

[StructLayout(LayoutKind.Explicit)]
internal struct InputUnion
{
    [FieldOffset(0)] internal MouseInput Mouse;
    [FieldOffset(0)] internal KeyboardInput Keyboard;
}

[StructLayout(LayoutKind.Sequential)]
internal struct MouseInput
{
    internal int X;
    internal int Y;
    internal uint MouseData;
    internal MouseEvent Flags;
    internal uint Time;
    internal nint ExtraInfo;
}

[StructLayout(LayoutKind.Sequential)]
internal struct KeyboardInput
{
    internal ushort VirtualKey;
    internal ushort Scan;
    internal KeyEvent Flags;
    internal uint Time;
    internal nint ExtraInfo;
}

[Flags]
internal enum MouseEvent : uint
{
    Move = 0x0001,
    LeftDown = 0x0002,
    LeftUp = 0x0004,
    Absolute = 0x8000,
    VirtualDesk = 0x4000
}

[Flags]
internal enum KeyEvent : uint
{
    None = 0,
    KeyUp = 0x0002,
    Unicode = 0x0004
}
