using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace JustVibrait.Monitor
{
    public sealed class HeaderBar : Control
    {
        public event EventHandler DemoClicked;
        public event EventHandler LanguageClicked;

        private string device = "";
        private string detail = "UDP 45454";
        private bool connected;
        private bool demo;

        private readonly Font brandFont;
        private readonly Font subtitleFont;
        private readonly Font deviceFont;
        private readonly Font detailFont;
        private readonly Font logoFont;
        private readonly Button demoButton;
        private readonly Button langButton;

        public HeaderBar()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.UserPaint |
                ControlStyles.ResizeRedraw,
                true
            );
            BackColor = Palette.Background;

            brandFont = new Font("Segoe UI", 15f, FontStyle.Bold);
            subtitleFont = new Font("Segoe UI", 7.5f, FontStyle.Bold);
            deviceFont = new Font("Segoe UI", 10.5f, FontStyle.Bold);
            detailFont = new Font("Segoe UI", 8.5f);
            logoFont = new Font("Segoe UI", 11f, FontStyle.Bold);

            demoButton = new Button();
            demoButton.FlatStyle = FlatStyle.Flat;
            demoButton.FlatAppearance.BorderSize = 1;
            demoButton.FlatAppearance.BorderColor = Palette.CardBorder;
            demoButton.FlatAppearance.MouseOverBackColor = Palette.Card;
            demoButton.BackColor = Palette.Card;
            demoButton.ForeColor = Palette.Text;
            demoButton.Font = new Font("Segoe UI", 8f, FontStyle.Bold);
            demoButton.Text = "ДЕМО";
            demoButton.Cursor = Cursors.Hand;
            demoButton.TabStop = false;
            demoButton.Click += DemoButtonClick;
            Controls.Add(demoButton);

            langButton = new Button();
            langButton.FlatStyle = FlatStyle.Flat;
            langButton.FlatAppearance.BorderSize = 1;
            langButton.FlatAppearance.BorderColor = Palette.CardBorder;
            langButton.FlatAppearance.MouseOverBackColor = Palette.Card;
            langButton.BackColor = Palette.Card;
            langButton.ForeColor = Palette.Text;
            langButton.Font = new Font("Segoe UI", 8f, FontStyle.Bold);
            langButton.Cursor = Cursors.Hand;
            langButton.TabStop = false;
            langButton.Click += LanguageButtonClick;
            Controls.Add(langButton);
            UpdateLangButton();
            device = Loc.T("header.noPhone");
        }

        private void DemoButtonClick(object sender, EventArgs e)
        {
            EventHandler handler = DemoClicked;
            if (handler != null) handler(this, EventArgs.Empty);
        }

        private void LanguageButtonClick(object sender, EventArgs e)
        {
            EventHandler handler = LanguageClicked;
            if (handler != null) handler(this, EventArgs.Empty);
        }

        public void UpdateLangButton()
        {
            langButton.Text = Loc.IsRussian ? "EN" : "RU";
            langButton.Invalidate();
        }

        public void SetState(string newDevice, string newDetail, bool isConnected, bool isDemo)
        {
            device = newDevice;
            detail = newDetail;
            connected = isConnected;
            demo = isDemo;
            Invalidate();
        }

        protected override void OnResize(EventArgs e)
        {
            base.OnResize(e);
            int buttonW = 92;
            int langW = 64;
            int buttonH = 36;
            int right = Math.Max(10, Width - 16);
            demoButton.SetBounds(
                right - buttonW,
                Math.Max(10, (Height - buttonH) / 2),
                buttonW,
                buttonH
            );
            langButton.SetBounds(
                right - buttonW - 8 - langW,
                Math.Max(10, (Height - buttonH) / 2),
                langW,
                buttonH
            );
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            g.Clear(Palette.Background);

            float w = Width;
            float h = Height;
            RectangleF card = new RectangleF(1f, 1f, w - 2f, h - 2f);
            float radius = Math.Max(14f, h * 0.17f);
            using (SolidBrush bg = new SolidBrush(Palette.Panel))
            {
                Ui.FillRound(g, bg, card, radius);
            }
            using (Pen border = new Pen(Color.FromArgb(160, Palette.CardBorder), 1f))
            {
                Ui.DrawRound(g, border, card, radius);
            }

            float logoSize = Math.Min(40f, h * 0.48f);
            float logoX = 20f;
            float logoY = (h - logoSize) / 2f;
            RectangleF logo = new RectangleF(logoX, logoY, logoSize, logoSize);
            using (LinearGradientBrush gradient = new LinearGradientBrush(
                logo,
                Palette.Cyan,
                Palette.Magenta,
                45f
            ))
            {
                Ui.FillRound(g, gradient, logo, logoSize * 0.3f);
            }
            using (StringFormat centered = new StringFormat())
            {
                centered.Alignment = StringAlignment.Center;
                centered.LineAlignment = StringAlignment.Center;
                using (SolidBrush mark = new SolidBrush(Palette.Background))
                {
                    g.DrawString("JV", logoFont, mark, logo, centered);
                }
            }

            float textX = logoX + logoSize + 14f;
            Ui.DrawLabel(g, "JustVibrait", brandFont, Palette.Text, textX, h * 0.15f);
            Ui.DrawLabel(
                g,
                Loc.T("header.subtitle"),
                subtitleFont,
                Palette.Muted,
                textX + 1f,
                h * 0.55f
            );

            float rightEdge = w - 196f;
            float leftLimit = Math.Min(w * 0.40f, 430f);
            if (leftLimit < textX + 260f) leftLimit = textX + 260f;
            RectangleF deviceRect = new RectangleF(
                leftLimit,
                h * 0.13f,
                Math.Max(80f, rightEdge - leftLimit),
                h * 0.34f
            );
            RectangleF detailRect = new RectangleF(
                leftLimit,
                h * 0.48f,
                Math.Max(80f, rightEdge - leftLimit),
                h * 0.32f
            );

            using (StringFormat far = new StringFormat())
            {
                far.Alignment = StringAlignment.Far;
                far.LineAlignment = StringAlignment.Center;
                far.Trimming = StringTrimming.EllipsisCharacter;
                far.FormatFlags = StringFormatFlags.NoWrap;

                using (SolidBrush deviceBrush = new SolidBrush(
                    connected || demo ? Palette.Text : Palette.Muted
                ))
                {
                    g.DrawString(device, deviceFont, deviceBrush, deviceRect, far);
                }
                using (SolidBrush detailBrush = new SolidBrush(Palette.Muted))
                {
                    g.DrawString(detail, detailFont, detailBrush, detailRect, far);
                }
            }

            float dotX = leftLimit - 16f;
            float dotY = deviceRect.Top + deviceRect.Height / 2f;
            Color dotColor = demo
                ? Palette.Magenta
                : (connected ? Palette.Green : Palette.Yellow);
            using (SolidBrush glow = new SolidBrush(Color.FromArgb(60, dotColor)))
            {
                g.FillEllipse(glow, dotX - 5f, dotY - 5f, 12f, 12f);
            }
            using (SolidBrush dot = new SolidBrush(dotColor))
            {
                g.FillEllipse(dot, dotX, dotY - 2f, 6f, 6f);
            }
        }

        public void SetDemo(bool value)
        {
            demoButton.Text = value ? Loc.T("header.stop") : Loc.T("header.demo");
            demoButton.BackColor = value ? Palette.Magenta : Palette.Card;
            demoButton.ForeColor = value ? Palette.Background : Palette.Text;
            demoButton.FlatAppearance.BorderColor = value ? Palette.Magenta : Palette.CardBorder;
            demoButton.FlatAppearance.MouseOverBackColor = value
                ? Ui.Blend(Palette.Magenta, Color.Black, 0.15f)
                : Palette.Card;
            demoButton.Invalidate();
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                brandFont.Dispose();
                subtitleFont.Dispose();
                deviceFont.Dispose();
                detailFont.Dispose();
                logoFont.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    public sealed class StatusBar : Control
    {
        private string leftText = "";
        private string rightText = "";
        private Color dotColor = Palette.Dim;

        private readonly Font textFont;
        private readonly Font rightFont;

        public StatusBar()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.UserPaint |
                ControlStyles.ResizeRedraw,
                true
            );
            BackColor = Palette.Background;
            textFont = new Font("Segoe UI", 8.5f);
            rightFont = new Font("Segoe UI", 8f, FontStyle.Bold);
        }

        public void SetState(string left, string right, Color color)
        {
            leftText = left;
            rightText = right;
            dotColor = color;
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            g.Clear(Palette.Background);

            float y = Height / 2f;
            using (SolidBrush dot = new SolidBrush(dotColor))
            {
                g.FillEllipse(dot, 2f, y - 3f, 6f, 6f);
            }

            using (StringFormat far = new StringFormat())
            {
                far.Alignment = StringAlignment.Far;
                far.LineAlignment = StringAlignment.Center;
                far.Trimming = StringTrimming.EllipsisCharacter;
                far.FormatFlags = StringFormatFlags.NoWrap;
                using (SolidBrush rightBrush = new SolidBrush(Palette.Muted))
                {
                    g.DrawString(
                        rightText,
                        rightFont,
                        rightBrush,
                        new RectangleF(Width * 0.60f, 0f, Width * 0.40f - 2f, Height),
                        far
                    );
                }
            }

            using (StringFormat near = new StringFormat())
            {
                near.Alignment = StringAlignment.Near;
                near.LineAlignment = StringAlignment.Center;
                near.Trimming = StringTrimming.EllipsisCharacter;
                near.FormatFlags = StringFormatFlags.NoWrap;
                using (SolidBrush brush = new SolidBrush(Palette.Muted))
                {
                    g.DrawString(
                        leftText,
                        textFont,
                        brush,
                        new RectangleF(14f, 0f, Width * 0.60f - 16f, Height),
                        near
                    );
                }
            }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                textFont.Dispose();
                rightFont.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    public sealed class MetricsPanel : Control
    {
        private float bass;
        private float level;
        private int bpm;
        private float threshold;
        private float intensity;
        private bool beat;
        private bool connected;

        private readonly Font titleFont;
        private readonly Font labelFont;
        private readonly Font valueFont;
        private readonly Font subtitleFont;

        public MetricsPanel()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.UserPaint |
                ControlStyles.ResizeRedraw,
                true
            );
            BackColor = Palette.Background;
            titleFont = new Font("Segoe UI", 8.5f, FontStyle.Bold);
            labelFont = new Font("Segoe UI", 8f);
            valueFont = new Font("Segoe UI", 8.5f, FontStyle.Bold);
            subtitleFont = new Font("Segoe UI", 7f, FontStyle.Bold);
        }

        public void SetValues(
            float newBass,
            float newLevel,
            int newBpm,
            float newThreshold,
            float newIntensity,
            bool newBeat,
            bool isConnected
        )
        {
            bass = newBass;
            level = newLevel;
            bpm = newBpm;
            threshold = newThreshold;
            intensity = newIntensity;
            beat = newBeat;
            connected = isConnected;
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            g.Clear(Palette.Background);

            float w = Width;
            float h = Height;
            float radius = Math.Max(12f, h * 0.07f);
            RectangleF card = new RectangleF(1f, 1f, w - 2f, h - 2f);
            using (SolidBrush bg = new SolidBrush(Palette.Panel))
            {
                Ui.FillRound(g, bg, card, radius);
            }
            using (Pen border = new Pen(Palette.CardBorder, 1f))
            {
                Ui.DrawRound(g, border, card, radius);
            }

            float p = Math.Max(14f, w * 0.045f);
            Ui.DrawLabel(g, Loc.T("metrics.signal"), titleFont, Palette.Muted, p, 11f);

            using (StringFormat far = new StringFormat())
            {
                far.Alignment = StringAlignment.Far;
                far.LineAlignment = StringAlignment.Near;
                using (SolidBrush brush = new SolidBrush(Palette.Dim))
                {
                    g.DrawString(
                        Loc.T("metrics.audio"),
                        subtitleFont,
                        brush,
                        new RectangleF(w * 0.5f, 12f, w * 0.5f - p, 16f),
                        far
                    );
                }
            }

            float top = 36f;
            float rowH = (h - top - 12f) / 5f;
            if (rowH < 20f) rowH = 20f;

            DrawRow(g, 0, p, top, w, rowH, Loc.T("metrics.bass"), connected ? Ui.Percent(bass) : "—",
                connected ? bass : 0f, Palette.Cyan);
            DrawRow(g, 1, p, top, w, rowH, Loc.T("metrics.level"), connected ? Ui.Percent(level) : "—",
                connected ? level : 0f, Palette.Green);
            DrawRow(g, 2, p, top, w, rowH, Loc.T("metrics.tempo"),
                connected ? bpm.ToString() + " BPM" : "—",
                connected ? Math.Min(1f, bpm / 180f) : 0f,
                beat ? Palette.Magenta : Palette.Blue);
            DrawRow(g, 3, p, top, w, rowH, Loc.T("metrics.threshold"), connected ? Ui.Percent(threshold) : "—",
                connected ? threshold : 0f, Palette.Yellow);
            DrawRow(g, 4, p, top, w, rowH, Loc.T("metrics.intensity"), connected ? Ui.Percent(intensity) : "—",
                connected ? intensity : 0f, Palette.Magenta);
        }

        private void DrawRow(
            Graphics g,
            int index,
            float p,
            float top,
            float width,
            float rowHeight,
            string label,
            string value,
            float fill,
            Color color
        )
        {
            float y = top + index * rowHeight;
            Ui.DrawLabel(g, label, labelFont, Palette.Muted, p, y + 1f);

            using (StringFormat far = new StringFormat())
            {
                far.Alignment = StringAlignment.Far;
                far.LineAlignment = StringAlignment.Near;
                using (SolidBrush brush = new SolidBrush(
                    connected ? Ui.Blend(Palette.Muted, color, 0.45f) : Palette.Dim
                ))
                {
                    g.DrawString(
                        value,
                        valueFont,
                        brush,
                        new RectangleF(width * 0.45f, y - 1f, width * 0.55f - p, rowHeight * 0.55f),
                        far
                    );
                }
            }

            float barH = Math.Max(4f, rowHeight * 0.16f);
            float barY = y + rowHeight - barH - 3f;
            RectangleF track = new RectangleF(p, barY, width - p * 2f, barH);
            using (SolidBrush trackBrush = new SolidBrush(Color.FromArgb(110, Palette.CardBorder)))
            {
                FillPill(g, trackBrush, track, barH / 2f);
            }
            if (fill > 0.004f)
            {
                float fillW = Math.Max(barH, track.Width * Math.Min(1f, fill));
                RectangleF active = new RectangleF(p, barY, fillW, barH);
                using (SolidBrush activeBrush = new SolidBrush(color))
                {
                    FillPill(g, activeBrush, active, barH / 2f);
                }
            }

            if (index == 2 && beat && connected)
            {
                float dotSize = barH * 2.2f;
                using (SolidBrush glow = new SolidBrush(Color.FromArgb(90, Palette.Magenta)))
                {
                    g.FillEllipse(
                        glow,
                        track.Right - dotSize,
                        barY + barH / 2f - dotSize / 2f,
                        dotSize,
                        dotSize
                    );
                }
            }
        }

        private static void FillPill(Graphics g, Brush brush, RectangleF bounds, float radius)
        {
            if (bounds.Width <= radius * 2.05f)
            {
                g.FillEllipse(brush, bounds);
            }
            else
            {
                Ui.FillRound(g, brush, bounds, radius);
            }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                titleFont.Dispose();
                labelFont.Dispose();
                valueFont.Dispose();
                subtitleFont.Dispose();
            }
            base.Dispose(disposing);
        }
    }
}
