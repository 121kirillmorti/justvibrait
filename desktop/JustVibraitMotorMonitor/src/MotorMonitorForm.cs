using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;
using WinTimer = System.Windows.Forms.Timer;

namespace JustVibrait.Monitor
{
    public sealed class MotorMonitorForm : Form
    {
        private const int TelemetryPort = 45454;

        [DllImport("dwmapi.dll", PreserveSig = true)]
        private static extern int DwmSetWindowAttribute(
            IntPtr hwnd,
            int attribute,
            ref int value,
            int size
        );

        private readonly UdpTelemetryListener listener;
        private readonly HeaderBar header;
        private readonly StatusBar status;
        private readonly ArcGauge gauge;
        private readonly MetricsPanel metrics;
        private readonly TimelineGraph timeline;
        private readonly EmptyStateCard emptyCard;
        private readonly WinTimer uiTimer;

        private readonly List<MotorCard> motorCards = new List<MotorCard>();
        private readonly Dictionary<int, float> motorDisplay = new Dictionary<int, float>();
        private readonly object pendingLock = new object();
        private readonly Random random = new Random(7);
        private readonly Stopwatch demoClock = new Stopwatch();

        private readonly string pcAddress;

        private TelemetryPacket pendingPacket;
        private TelemetryPacket lastPacket;
        private long lastPacketTick;
        private bool connected;
        private bool demoMode;
        private int demoSequence;
        private long packetsSinceRate;
        private int rate;
        private long lastRateTick;
        private float measuredShown;
        private float commandedShown;
        private float beatFlash;
        private string listenerError;
        private string emptyTitle = "";
        private string emptySubtitle = "";

        public MotorMonitorForm()
        {            Text = Loc.T("form.title");
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(1320, 860);
            MinimumSize = new Size(1080, 740);
            BackColor = Palette.Background;
            ForeColor = Palette.Text;
            Font = new Font("Segoe UI", 9f);
            AutoScaleMode = AutoScaleMode.None;
            DoubleBuffered = true;
            KeyPreview = true;

            pcAddress = GetLocalAddress();

            header = new HeaderBar();
            header.DemoClicked += ToggleDemo;
            header.LanguageClicked += ToggleLanguage;

            status = new StatusBar();
            gauge = new ArcGauge();
            metrics = new MetricsPanel();
            timeline = new TimelineGraph();
            emptyCard = new EmptyStateCard();

            Controls.Add(header);
            Controls.Add(status);
            Controls.Add(gauge);
            Controls.Add(metrics);
            Controls.Add(timeline);
            Controls.Add(emptyCard);

            listener = new UdpTelemetryListener(TelemetryPort);
            listener.PacketReceived += OnPacketReceived;
            listener.ReceiveError += OnReceiveError;

            uiTimer = new WinTimer();
            uiTimer.Interval = 33;
            uiTimer.Tick += OnTick;

            LayoutDashboard();
            ApplyDemoState();
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            ApplyInitialBounds();
            StartListener();
            uiTimer.Start();
        }

        private void ApplyInitialBounds()
        {
            Screen screen = Screen.FromControl(this);
            if (screen == null) screen = Screen.PrimaryScreen;
            Rectangle work = screen.WorkingArea;
            int width = Math.Min(1400, Math.Max(980, work.Width - 72));
            int height = Math.Min(900, Math.Max(680, work.Height - 64));
            ClientSize = new Size(width, height);
            Location = new Point(
                work.Left + Math.Max(0, (work.Width - Width) / 2),
                work.Top + Math.Max(0, (work.Height - Height) / 2)
            );
            LayoutDashboard();
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            try
            {
                int enabled = 1;
                if (DwmSetWindowAttribute(Handle, 20, ref enabled, sizeof(int)) != 0)
                {
                    DwmSetWindowAttribute(Handle, 19, ref enabled, sizeof(int));
                }
            }
            catch (Exception)
            {
            }
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            uiTimer.Stop();
            listener.Dispose();
            base.OnFormClosing(e);
        }

        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);
            LayoutDashboard();
        }

        private void StartListener()
        {
            try
            {
                listener.Start();
            }
            catch (Exception ex)
            {
                listenerError = ex.Message;
            }
        }

        private void OnPacketReceived(TelemetryPacket packet)
        {
            lock (pendingLock)
            {
                pendingPacket = packet;
            }
            Interlocked.Increment(ref packetsSinceRate);
            listenerError = null;
        }

        private void OnReceiveError(string message)
        {
            listenerError = message;
        }

        private void OnTick(object sender, EventArgs e)
        {
            try
            {
                long now = Stopwatch.GetTimestamp();
                if (demoMode)
                {
                    GenerateDemoPacket(now);
                }
                else
                {
                    ConsumePending(now);
                }

                connected = lastPacket != null && MillisecondsSince(lastPacketTick, now) < 2600.0;

                if (now - lastRateTick >= Stopwatch.Frequency)
                {
                    long collected = Interlocked.Exchange(ref packetsSinceRate, 0);
                    rate = (int)collected;
                    lastRateTick = now;
                }

                UpdateVisuals(now);
            }
            catch (Exception ex)
            {
                listenerError = ex.Message;
            }
        }

        private void ConsumePending(long now)
        {
            TelemetryPacket packet = null;
            lock (pendingLock)
            {
                if (pendingPacket != null)
                {
                    packet = pendingPacket;
                    pendingPacket = null;
                }
            }
            if (packet == null) return;

            ApplyPacket(packet, now);
        }

        private void ApplyPacket(TelemetryPacket packet, long now)
        {
            lastPacket = packet;
            lastPacketTick = now;
            if (packet.Beat) beatFlash = 1f;
            EnsureMotorCards(packet);
        }

        private void GenerateDemoPacket(long now)
        {
            double seconds = demoClock.Elapsed.TotalSeconds;
            double period = 60.0 / 126.0;
            double phase = seconds % period;
            float envelope = (float)Math.Exp(-phase * 7.2);
            float jitter = (float)((random.NextDouble() - 0.5) * 0.035);

            float bass = Clamp01(0.16f + 0.74f * envelope + jitter);
            float level = Clamp01(0.22f + 0.55f * envelope);
            bool beat = phase < 0.06;
            float measured = Clamp01(0.10f + envelope * 0.84f + (float)random.NextDouble() * 0.05f);

            TelemetryPacket packet = new TelemetryPacket();
            packet.Sequence = demoSequence++;
            packet.UptimeMs = (long)(seconds * 1000.0);
            packet.Device = Loc.T("form.demoDevice");
            packet.AndroidVersion = "15";
            packet.Sdk = 35;
            packet.Source = "SYSTEM";
            packet.Profile = "DYNAMIC";
            packet.Intensity = 0.72f;
            packet.Threshold = 0.34f;
            packet.Bass = bass;
            packet.Level = level;
            packet.Bpm = 126;
            packet.Beat = beat;
            packet.Measured = measured;
            packet.MeasuredMs2 = measured * 19f;
            packet.HasSensor = true;
            packet.DefaultMotorId = 0;
            packet.ReceivedAtTicks = DateTime.UtcNow.Ticks;

            MotorState main = new MotorState();
            main.Id = 0;
            main.HasAmplitudeControl = true;
            main.IsDefault = true;
            main.Amplitude = envelope > 0.30f ? (int)(70f + envelope * 180f) : 0;
            main.DurationMs = main.Amplitude > 0 ? 24 : 0;

            MotorState secondary = new MotorState();
            secondary.Id = 1;
            secondary.HasAmplitudeControl = true;
            secondary.IsDefault = false;
            secondary.Amplitude = envelope > 0.42f ? (int)(52f + envelope * 150f) : 0;
            secondary.DurationMs = secondary.Amplitude > 0 ? 20 : 0;

            packet.Motors.Add(main);
            packet.Motors.Add(secondary);

            lastPacket = packet;
            lastPacketTick = now;
            if (beat) beatFlash = 1f;
            Interlocked.Increment(ref packetsSinceRate);
            EnsureMotorCards(packet);
        }

        private void EnsureMotorCards(TelemetryPacket packet)
        {
            bool same = motorCards.Count == packet.Motors.Count;
            if (same)
            {
                for (int i = 0; i < motorCards.Count; i++)
                {
                    if (motorCards[i].MotorId != packet.Motors[i].Id)
                    {
                        same = false;
                        break;
                    }
                }
            }
            if (same) return;

            for (int i = 0; i < motorCards.Count; i++)
            {
                Controls.Remove(motorCards[i]);
                motorCards[i].Dispose();
            }
            motorCards.Clear();
            motorDisplay.Clear();

            for (int i = 0; i < packet.Motors.Count; i++)
            {
                MotorCard card = new MotorCard();
                card.SetMotor(packet.Motors[i]);
                motorDisplay[packet.Motors[i].Id] = 0f;
                motorCards.Add(card);
                Controls.Add(card);
            }

            LayoutDashboard();
        }

        private void UpdateVisuals(long now)
        {
            TelemetryPacket packet = lastPacket;
            bool live = connected && packet != null;
            bool hasSensor = packet == null || packet.HasSensor;

            float targetMeasured = live && hasSensor ? packet.Measured : 0f;
            if (targetMeasured > measuredShown)
            {
                measuredShown += (targetMeasured - measuredShown) * 0.60f;
            }
            else
            {
                measuredShown *= 0.90f;
            }
            if (measuredShown < 0.002f) measuredShown = 0f;

            float bestLevel = 0f;
            int bestId = -1;
            int bestAmplitude = 0;
            long bestDuration = 0;

            for (int i = 0; i < motorCards.Count; i++)
            {
                MotorCard card = motorCards[i];
                int id = card.MotorId;
                MotorState state = null;
                if (packet != null)
                {
                    for (int j = 0; j < packet.Motors.Count; j++)
                    {
                        if (packet.Motors[j].Id == id)
                        {
                            state = packet.Motors[j];
                            break;
                        }
                    }
                }

                if (state != null) card.SetMotor(state);
                card.SetConnected(live);

                float target = live && state != null ? state.Level : 0f;
                float displayValue;
                if (!motorDisplay.TryGetValue(id, out displayValue)) displayValue = 0f;
                if (target > displayValue)
                {
                    displayValue = target;
                }
                else
                {
                    displayValue *= 0.84f;
                }
                if (displayValue < 0.005f) displayValue = 0f;
                motorDisplay[id] = displayValue;

                card.SetDisplay(displayValue);
                if (displayValue > bestLevel) bestLevel = displayValue;
                if (state != null && state.Amplitude > bestAmplitude)
                {
                    bestAmplitude = state.Amplitude;
                    bestDuration = state.DurationMs;
                    bestId = state.Id;
                }
            }

            if (bestLevel > commandedShown)
            {
                commandedShown += (bestLevel - commandedShown) * 0.70f;
            }
            else
            {
                commandedShown *= 0.86f;
            }
            if (commandedShown < 0.004f) commandedShown = 0f;

            gauge.SetValues(
                measuredShown,
                commandedShown,
                live && hasSensor ? packet.MeasuredMs2 : 0f,
                hasSensor,
                live
            );

            if (live && bestAmplitude > 0)
            {
                gauge.SetEvent(
                    Loc.F("gauge.pulseFmt", bestId.ToString(), bestDuration.ToString(), bestAmplitude.ToString())
                );
            }
            else if (live)
            {
                gauge.SetEvent(Loc.T("gauge.waitingNext"));
            }
            else if (packet != null)
            {
                gauge.SetEvent(Loc.T("gauge.noAnswer"));
            }
            else
            {
                gauge.SetEvent(Loc.T("gauge.noPulses"));
            }

            string device = packet == null ? Loc.T("header.noPhone") : packet.Device;
            string detail = Loc.F("header.pcFmt", pcAddress, TelemetryPort.ToString());
            if (live)
            {
                detail += " · " + Ui.SourceLabel(packet.Source) +
                    " · " + Ui.ProfileLabel(packet.Profile);
            }
            if (demoMode) detail += Loc.T("header.demoSuffix");
            header.SetState(device, detail, connected, demoMode);
            header.SetDemo(demoMode);

            if (motorCards.Count == 0)
            {
                emptyCard.Visible = true;
                if (live)
                {
                    SetEmptyMessage(
                        Loc.T("empty.noMotorsTitle"),
                        Loc.T("empty.noMotorsBody")
                    );
                }
                else
                {
                    SetEmptyMessage(
                        Loc.T("empty.waitTitle"),
                        Loc.T("empty.waitBody")
                    );
                }
            }
            else
            {
                emptyCard.Visible = false;
            }

            if (live)
            {
                metrics.SetValues(
                    packet.Bass,
                    packet.Level,
                    packet.Bpm,
                    packet.Threshold,
                    packet.Intensity,
                    beatFlash > 0.35f,
                    true
                );
            }
            else
            {
                metrics.SetValues(0f, 0f, 0, 0f, 0f, false, false);
            }

            string left;
            Color dot;
            if (demoMode)
            {
                left = Loc.T("status.demo");
                dot = Palette.Magenta;
            }
            else if (live)
            {
                left = Loc.F(
                    "status.liveFmt",
                    Ui.SourceLabel(packet.Source),
                    Ui.ProfileLabel(packet.Profile)
                );
                dot = Palette.Green;
            }
            else if (!string.IsNullOrEmpty(listenerError))
            {
                left = Loc.F("status.udpErrorFmt", TelemetryPort.ToString(), listenerError);
                dot = Palette.Red;
            }
            else
            {
                left = Loc.F("status.waitingFmt", TelemetryPort.ToString());
                dot = Palette.Yellow;
            }

            string right;
            if (live)
            {
                double age = MillisecondsSince(lastPacketTick, now);
                right = Loc.F("status.rateFmt", rate.ToString(), ((int)age).ToString());
            }
            else
            {
                right = Loc.F("status.pcFmt", pcAddress, TelemetryPort.ToString());
            }
            status.SetState(left, right, dot);

            timeline.AddSample(measuredShown, commandedShown, beatFlash > 0.35f);

            beatFlash *= 0.88f;
            if (beatFlash < 0.01f) beatFlash = 0f;
        }

        private void SetEmptyMessage(string title, string subtitle)
        {
            if (emptyTitle == title && emptySubtitle == subtitle) return;
            emptyTitle = title;
            emptySubtitle = subtitle;
            emptyCard.SetMessage(title, subtitle);
        }

        private void LayoutDashboard()
        {
            if (header == null || ClientSize.Width <= 0) return;

            float scale = DeviceDpi / 96f;
            int margin = (int)Math.Round(16f * scale);
            int headerHeight = (int)Math.Round(84f * scale);
            int statusHeight = (int)Math.Round(28f * scale);
            int gap = (int)Math.Round(12f * scale);

            header.SetBounds(
                margin,
                margin,
                Math.Max(200, ClientSize.Width - margin * 2),
                headerHeight
            );

            int statusY = ClientSize.Height - margin - statusHeight;
            status.SetBounds(
                margin,
                statusY,
                Math.Max(200, ClientSize.Width - margin * 2),
                statusHeight
            );

            int contentY = margin + headerHeight + gap;
            int contentBottom = statusY - gap;
            int contentHeight = contentBottom - contentY;
            int contentWidth = ClientSize.Width - margin * 2;
            if (contentHeight < 180 || contentWidth < 500) return;

            int leftWidth = (int)(contentWidth * 0.37f);
            int minLeft = (int)Math.Round(340f * scale);
            int maxLeft = (int)Math.Round(510f * scale);
            if (leftWidth < minLeft) leftWidth = minLeft;
            if (leftWidth > maxLeft) leftWidth = maxLeft;
            int maxAllowed = contentWidth - (int)Math.Round(470f * scale);
            if (leftWidth > maxAllowed) leftWidth = Math.Max((int)Math.Round(300f * scale), maxAllowed);

            int metricsHeight = (int)Math.Round(198f * scale);
            int gaugeHeight = contentHeight - metricsHeight - gap;
            int minGauge = (int)Math.Round(350f * scale);
            if (gaugeHeight < minGauge)
            {
                metricsHeight = (int)Math.Round(176f * scale);
                if (metricsHeight > contentHeight / 3) metricsHeight = contentHeight / 3;
                gaugeHeight = contentHeight - metricsHeight - gap;
            }
            if (gaugeHeight < 200) gaugeHeight = Math.Max(160, contentHeight - gap - 140);

            gauge.SetBounds(margin, contentY, leftWidth, gaugeHeight);
            metrics.SetBounds(
                margin,
                contentY + gaugeHeight + gap,
                leftWidth,
                contentHeight - gaugeHeight - gap
            );

            int rightX = margin + leftWidth + gap;
            int rightWidth = ClientSize.Width - margin - rightX;
            if (rightWidth < 200) return;

            int count = motorCards.Count;
            int columns = count <= 1 ? 1 : 2;
            int rows = count == 0 ? 1 : (count + columns - 1) / columns;
            int cardHeight = (int)Math.Round(118f * scale);
            int cardGap = (int)Math.Round(10f * scale);
            int motorsHeight = rows * cardHeight + (rows - 1) * cardGap;

            int maxMotorsHeight = (int)(contentHeight * 0.56f);
            if (motorsHeight > maxMotorsHeight)
            {
                cardHeight = Math.Max(
                    (int)Math.Round(86f * scale),
                    (maxMotorsHeight - (rows - 1) * cardGap) / rows
                );
                motorsHeight = rows * cardHeight + (rows - 1) * cardGap;
            }

            int cardWidth = (rightWidth - (columns - 1) * cardGap) / columns;
            for (int i = 0; i < count; i++)
            {
                int column = i % columns;
                int row = i / columns;
                motorCards[i].SetBounds(
                    rightX + column * (cardWidth + cardGap),
                    contentY + row * (cardHeight + cardGap),
                    cardWidth,
                    cardHeight
                );
            }

            if (count == 0)
            {
                int h = Math.Min(cardHeight, Math.Max(90, contentHeight / 3));
                emptyCard.SetBounds(rightX, contentY, rightWidth, h);
                emptyCard.Visible = true;
                motorsHeight = h;
            }
            else
            {
                emptyCard.Visible = false;
            }

            int timelineY = contentY + motorsHeight + gap;
            int timelineHeight = contentBottom - timelineY;
            if (timelineHeight < 90) timelineHeight = 90;
            timeline.SetBounds(rightX, timelineY, rightWidth, timelineHeight);
        }

        private void ToggleDemo(object sender, EventArgs e)
        {
            demoMode = !demoMode;
            ApplyDemoState();
        }

        private void ToggleLanguage(object sender, EventArgs e)
        {
            Loc.Toggle();
            ApplyLanguage();
        }

        public void SetLanguage(MonitorLanguage language)
        {
            Loc.SetLanguage(language);
            ApplyLanguage();
        }

        private void ApplyLanguage()
        {
            Text = Loc.T("form.title");
            header.UpdateLangButton();
            header.SetDemo(demoMode);
            emptyTitle = "";
            emptySubtitle = "";
            emptyCard.SetMessage(Loc.T("empty.waitTitle"), Loc.T("empty.waitBody"));
            for (int i = 0; i < Controls.Count; i++)
            {
                Controls[i].Invalidate(true);
            }
        }

        public void StartInDemoMode()
        {
            demoMode = true;
            ApplyDemoState();
        }

        private void ApplyDemoState()
        {
            header.SetDemo(demoMode);
            if (demoMode)
            {
                ClearTelemetry();
                demoClock.Reset();
                demoClock.Start();
                demoSequence = 0;
            }
            else
            {
                demoClock.Stop();
                ClearTelemetry();
            }
        }

        private void ClearTelemetry()
        {
            lock (pendingLock)
            {
                pendingPacket = null;
            }
            lastPacket = null;
            lastPacketTick = 0;
            connected = false;
            measuredShown = 0f;
            commandedShown = 0f;
            beatFlash = 0f;
            motorDisplay.Clear();

            for (int i = 0; i < motorCards.Count; i++)
            {
                Controls.Remove(motorCards[i]);
                motorCards[i].Dispose();
            }
            motorCards.Clear();
            timeline.Clear();
            gauge.SetValues(0f, 0f, 0f, true, false);
            LayoutDashboard();
        }

        private static double MillisecondsSince(long since, long now)
        {
            if (since == 0) return double.MaxValue;
            return (now - since) * 1000.0 / Stopwatch.Frequency;
        }

        private static float Clamp01(float value)
        {
            if (value < 0f) return 0f;
            if (value > 1f) return 1f;
            return value;
        }

        private static string GetLocalAddress()
        {
            try
            {
                IPHostEntry host = Dns.GetHostEntry(Dns.GetHostName());
                string fallback = null;
                for (int i = 0; i < host.AddressList.Length; i++)
                {
                    IPAddress address = host.AddressList[i];
                    if (address.AddressFamily != AddressFamily.InterNetwork) continue;
                    if (IPAddress.IsLoopback(address)) continue;
                    string text = address.ToString();
                    if (text.StartsWith("192.168.") || text.StartsWith("10.") ||
                        text.StartsWith("172."))
                    {
                        return text;
                    }
                    if (fallback == null) fallback = text;
                }
                if (fallback != null) return fallback;
            }
            catch (Exception)
            {
            }
            return "127.0.0.1";
        }
    }
}
