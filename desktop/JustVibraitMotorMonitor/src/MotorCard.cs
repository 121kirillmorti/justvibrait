using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace JustVibrait.Monitor
{
    /// <summary>
    /// One card per vibrator reported by the phone. Android never exposes the
    /// amplitude a motor really produced, so the card draws the value that the
    /// app commanded for that specific motor id.
    /// </summary>
    public sealed class MotorCard : Control
    {
        private int motorId = -1;
        private bool hasAmplitudeControl;
        private bool isDefault;
        private long durationMs;
        private float level;
        private float display;
        private bool connected;

        private readonly Font labelFont;
        private readonly Font tagFont;
        private readonly Font percentFont;
        private readonly Font commandFont;
        private readonly Font tinyFont;
        private readonly Font rightFont;

        public int MotorId
        {
            get { return motorId; }
        }

        public MotorCard()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.UserPaint |
                ControlStyles.ResizeRedraw,
                true
            );
            BackColor = Palette.Background;
            labelFont = new Font("Segoe UI", 8.5f, FontStyle.Bold);
            tagFont = new Font("Segoe UI", 7f, FontStyle.Bold);
            percentFont = new Font("Segoe UI", 24f, FontStyle.Bold);
            commandFont = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            tinyFont = new Font("Segoe UI", 7.5f);
            rightFont = new Font("Segoe UI", 8f, FontStyle.Bold);
        }

        public void SetMotor(MotorState motor)
        {
            if (motor == null) return;
            motorId = motor.Id;
            hasAmplitudeControl = motor.HasAmplitudeControl;
            isDefault = motor.IsDefault;
            level = motor.Level;
            durationMs = motor.DurationMs;
            Invalidate();
        }

        public void SetConnected(bool value)
        {
            if (connected == value) return;
            connected = value;
            Invalidate();
        }

        public void SetDisplay(float value)
        {
            display = Math.Max(0f, Math.Min(1f, value));
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
            float radius = h * 0.13f;
            RectangleF card = new RectangleF(1f, 1f, w - 2f, h - 2f);

            bool active = connected && display > 0.012f;
            Color borderColor = active
                ? Ui.Blend(Palette.CardBorder, Palette.Magenta, Math.Min(0.85f, display * 1.1f))
                : Palette.CardBorder;

            using (SolidBrush bg = new SolidBrush(Palette.Card))
            {
                Ui.FillRound(g, bg, card, radius);
            }
            using (Pen border = new Pen(borderColor, active ? 1.6f : 1f))
            {
                Ui.DrawRound(g, border, card, radius);
            }

            float p = Math.Max(12f, h * 0.13f);
            float topY = h * 0.085f;

            string label = motorId >= 0 ? Loc.T("motor.label") + " " + motorId : Loc.T("motor.label");
            Ui.DrawLabel(g, label, labelFont, Palette.Muted, p, topY);

            if (motorId >= 0)
            {
                SizeF labelSize = g.MeasureString(label, labelFont);
                string tag = isDefault ? Loc.T("motor.primary") : Loc.T("motor.secondary");
                Color tagColor = isDefault
                    ? Palette.Cyan
                    : (hasAmplitudeControl ? Palette.Blue : Palette.Dim);
                SizeF tagSize = g.MeasureString(tag, tagFont);
                float tagH = h * 0.155f;
                float tagW = tagSize.Width + tagH * 0.9f;
                float tagX = p + labelSize.Width + 8f;
                float tagY = topY + (g.MeasureString(label, labelFont).Height - tagH) / 2f;
                RectangleF pill = new RectangleF(tagX, tagY, tagW, tagH);
                using (SolidBrush pillBg = new SolidBrush(Color.FromArgb(30, tagColor)))
                {
                    Ui.FillRound(g, pillBg, pill, tagH / 2f);
                }
                using (Pen pillBorder = new Pen(Color.FromArgb(70, tagColor), 1f))
                {
                    Ui.DrawRound(g, pillBorder, pill, tagH / 2f);
                }
                using (SolidBrush tagBrush = new SolidBrush(tagColor))
                using (StringFormat centered = new StringFormat())
                {
                    centered.Alignment = StringAlignment.Center;
                    centered.LineAlignment = StringAlignment.Center;
                    g.DrawString(tag, tagFont, tagBrush, pill, centered);
                }
            }

            string percent = connected && motorId >= 0 ? Ui.Percent(display) : "--";
            using (StringFormat far = new StringFormat())
            {
                far.Alignment = StringAlignment.Far;
                far.LineAlignment = StringAlignment.Near;
                RectangleF percentRect = new RectangleF(
                    w * 0.52f,
                    -h * 0.015f,
                    w * 0.48f - p,
                    h * 0.40f
                );
                using (SolidBrush textBrush = new SolidBrush(
                    active ? Palette.Text : (connected ? Palette.Muted : Palette.Dim)
                ))
                {
                    g.DrawString(percent, percentFont, textBrush, percentRect, far);
                }
            }

            int amplitude = (int)Math.Round(display * 255f);
            string command = motorId < 0
                ? Loc.T("motor.noData")
                : Loc.F("motor.cmdFmt", amplitude.ToString());
            Ui.DrawLabel(
                g,
                command,
                commandFont,
                active ? Palette.Text : Palette.Muted,
                p,
                h * 0.39f
            );

            float barY = h * 0.645f;
            float barH = Math.Max(6f, h * 0.062f);
            float barW = w - p * 2f;
            RectangleF track = new RectangleF(p, barY, barW, barH);
            using (SolidBrush trackBrush = new SolidBrush(Color.FromArgb(120, Palette.CardBorder)))
            {
                FillPill(g, trackBrush, track, barH / 2f);
            }

            if (display > 0.004f)
            {
                float fillW = barW * display;
                if (fillW < barH) fillW = barH;
                RectangleF fill = new RectangleF(p, barY, fillW, barH);
                using (LinearGradientBrush gradient = new LinearGradientBrush(
                    track,
                    Palette.Cyan,
                    Palette.Magenta,
                    0f
                ))
                {
                    FillPill(g, gradient, fill, barH / 2f);
                }
                float dotX = p + fillW;
                using (SolidBrush glow = new SolidBrush(Color.FromArgb(70, Palette.Cyan)))
                {
                    g.FillEllipse(
                        glow,
                        dotX - barH * 1.4f,
                        barY - barH * 0.9f,
                        barH * 2.8f,
                        barH * 2.8f
                    );
                }
                using (SolidBrush dot = new SolidBrush(Palette.Text))
                {
                    g.FillEllipse(
                        dot,
                        dotX - barH * 0.5f,
                        barY,
                        barH,
                        barH
                    );
                }
            }

            string capability = !hasAmplitudeControl
                ? Loc.T("motor.noAmp")
                : Loc.T("motor.hasAmp");
            if (motorId < 0) capability = Loc.T("motor.connect");
            Ui.DrawLabel(g, capability, tinyFont, Palette.Dim, p, h * 0.80f);

            if (motorId >= 0)
            {
                string pulse = durationMs > 0
                    ? Loc.F("motor.pulseFmt", durationMs.ToString())
                    : Loc.T("motor.waiting");
                using (StringFormat far = new StringFormat())
                {
                    far.Alignment = StringAlignment.Far;
                    far.LineAlignment = StringAlignment.Near;
                    using (SolidBrush pulseBrush = new SolidBrush(
                        durationMs > 0 ? Ui.Blend(Palette.Muted, Palette.Cyan, display) : Palette.Dim
                    ))
                    {
                        g.DrawString(
                            pulse,
                            rightFont,
                            pulseBrush,
                            new RectangleF(p, h * 0.80f - 1f, barW, h * 0.16f),
                            far
                        );
                    }
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
                labelFont.Dispose();
                tagFont.Dispose();
                percentFont.Dispose();
                commandFont.Dispose();
                tinyFont.Dispose();
                rightFont.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    /// <summary>Placeholder shown before the first packet creates motor cards.</summary>
    public sealed class EmptyStateCard : Control
    {
        private string title = Loc.T("empty.waitTitle");
        private string subtitle = Loc.T("empty.waitBody");

        private readonly Font titleFont;
        private readonly Font bodyFont;

        public EmptyStateCard()
        {
            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.UserPaint |
                ControlStyles.ResizeRedraw,
                true
            );
            BackColor = Palette.Background;
            titleFont = new Font("Segoe UI", 10f, FontStyle.Bold);
            bodyFont = new Font("Segoe UI", 8.5f);
        }

        public void SetMessage(string newTitle, string newSubtitle)
        {
            title = newTitle;
            subtitle = newSubtitle;
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            g.Clear(Palette.Background);

            RectangleF card = new RectangleF(1f, 1f, Width - 2f, Height - 2f);
            using (SolidBrush bg = new SolidBrush(Palette.Card))
            {
                Ui.FillRound(g, bg, card, Math.Max(10f, Height * 0.13f));
            }
            using (Pen border = new Pen(Palette.CardBorder, 1f))
            {
                Ui.DrawRound(g, border, card, Math.Max(10f, Height * 0.13f));
            }

            using (StringFormat centered = new StringFormat())
            {
                centered.Alignment = StringAlignment.Center;
                centered.LineAlignment = StringAlignment.Center;
                using (SolidBrush titleBrush = new SolidBrush(Palette.Text))
                {
                    g.DrawString(
                        title,
                        titleFont,
                        titleBrush,
                        new RectangleF(20f, Height * 0.28f, Width - 40f, 26f),
                        centered
                    );
                }
                using (SolidBrush bodyBrush = new SolidBrush(Palette.Muted))
                {
                    g.DrawString(
                        subtitle,
                        bodyFont,
                        bodyBrush,
                        new RectangleF(40f, Height * 0.50f, Width - 80f, 40f),
                        centered
                    );
                }
            }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                titleFont.Dispose();
                bodyFont.Dispose();
            }
            base.Dispose(disposing);
        }
    }
}
