using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace JustVibrait.Monitor
{
    public sealed class ArcGauge : Control
    {
        private float measured;
        private float commanded;
        private float measuredMs2;
        private bool hasSensor = true;
        private bool connected;
        private string lastEvent = Loc.T("gauge.noPulses");

        private readonly Font titleFont;
        private readonly Font smallFont;
        private readonly Font tinyFont;
        private readonly Font bigFont;
        private readonly Font unitFont;

        public ArcGauge()
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
            smallFont = new Font("Segoe UI", 9f);
            tinyFont = new Font("Segoe UI", 7.5f);
            bigFont = new Font("Segoe UI", 42f, FontStyle.Bold);
            unitFont = new Font("Segoe UI", 10f, FontStyle.Bold);
        }

        public void SetValues(
            float measured,
            float commanded,
            float measuredMs2,
            bool hasSensor,
            bool connected
        )
        {
            this.measured = Math.Max(0f, Math.Min(1f, measured));
            this.commanded = Math.Max(0f, Math.Min(1f, commanded));
            this.measuredMs2 = Math.Max(0f, measuredMs2);
            this.hasSensor = hasSensor;
            this.connected = connected;
            Invalidate();
        }

        public void SetEvent(string text)
        {
            lastEvent = text;
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
            g.Clear(Palette.Background);

            RectangleF card = new RectangleF(6f, 6f, Width - 12f, Height - 12f);
            using (SolidBrush cardBrush = new SolidBrush(Palette.Panel))
            {
                Ui.FillRound(g, cardBrush, card, 20f);
            }
            using (Pen border = new Pen(Palette.CardBorder, 1f))
            {
                Ui.DrawRound(g, border, card, 20f);
            }

            Ui.DrawLabel(g, Loc.T("gauge.title"), titleFont, Palette.Cyan, 24f, 20f);
            Ui.DrawLabel(
                g,
                Loc.T("gauge.tag"),
                tinyFont,
                Palette.Muted,
                Math.Max(24f, Width - 122f),
                22f
            );

            float diameter = Math.Min(Width - 64f, Height - 150f);
            if (diameter < 120f) diameter = 120f;
            float left = (Width - diameter) / 2f;
            float top = 52f;
            RectangleF arcRect = new RectangleF(left, top, diameter, diameter);
            float stroke = Math.Max(10f, diameter * 0.055f);

            using (Pen track = new Pen(Palette.CardBorder, stroke))
            {
                track.StartCap = LineCap.Round;
                track.EndCap = LineCap.Round;
                g.DrawArc(track, arcRect, 135f, 270f);
            }

            float sweep = 270f * (hasSensor ? measured : 0f);
            if (sweep > 0.4f)
            {
                using (LinearGradientBrush gradient = new LinearGradientBrush(
                    arcRect,
                    Palette.Cyan,
                    Palette.Magenta,
                    45f
                ))
                {
                    using (Pen active = new Pen(gradient, stroke))
                    {
                        active.StartCap = LineCap.Round;
                        active.EndCap = LineCap.Round;
                        g.DrawArc(active, arcRect, 135f, sweep);
                    }
                }
                DrawTip(g, arcRect, 135f + sweep, stroke / 2f);
            }

            float innerInset = stroke * 0.85f;
            RectangleF innerRect = RectangleF.Inflate(arcRect, -innerInset, -innerInset);
            using (Pen commandTrack = new Pen(Color.FromArgb(46, Palette.Magenta), 4f))
            {
                commandTrack.StartCap = LineCap.Round;
                commandTrack.EndCap = LineCap.Round;
                g.DrawArc(commandTrack, innerRect, 135f, 270f);
            }
            float commandSweep = 270f * commanded;
            if (commandSweep > 0.4f)
            {
                using (Pen commandPen = new Pen(Palette.Magenta, 4f))
                {
                    commandPen.StartCap = LineCap.Round;
                    commandPen.EndCap = LineCap.Round;
                    g.DrawArc(commandPen, innerRect, 135f, commandSweep);
                }
            }

            float centerX = arcRect.X + arcRect.Width / 2f;
            float centerY = arcRect.Y + arcRect.Height / 2f;
            string percent = connected && hasSensor ? Ui.Percent(measured) : "--";
            using (StringFormat centered = new StringFormat())
            {
                centered.Alignment = StringAlignment.Center;
                centered.LineAlignment = StringAlignment.Center;
                RectangleF bigRect = new RectangleF(
                    centerX - diameter / 2f,
                    centerY - 48f,
                    diameter,
                    76f
                );
                using (SolidBrush textBrush = new SolidBrush(
                    connected ? Palette.Text : Palette.Dim
                ))
                {
                    g.DrawString(percent, bigFont, textBrush, bigRect, centered);
                }

                RectangleF captionRect = new RectangleF(
                    centerX - diameter / 2f,
                    centerY + 22f,
                    diameter,
                    22f
                );
                using (SolidBrush captionBrush = new SolidBrush(Palette.Muted))
                {
                    g.DrawString(Loc.T("gauge.measured"), smallFont, captionBrush, captionRect, centered);
                }

                RectangleF unitRect = new RectangleF(
                    centerX - diameter / 2f,
                    centerY + 44f,
                    diameter,
                    22f
                );
                using (SolidBrush unitBrush = new SolidBrush(Palette.Cyan))
                {
                    string unit = hasSensor
                        ? measuredMs2.ToString("0.0") + " м/с²"
                        : Loc.T("gauge.noSensor");
                    g.DrawString(unit, unitFont, unitBrush, unitRect, centered);
                }
            }

            float commandY = arcRect.Bottom + 8f;
            string commandText = commanded > 0.005f
                ? Loc.F(
                    "gauge.cmdFmt",
                    Ui.Percent(commanded),
                    ((int)Math.Round(commanded * 255f)).ToString()
                )
                : Loc.T("gauge.cmdIdle");
            Ui.DrawLabel(g, commandText, smallFont, Palette.Magenta, 24f, commandY);

            using (Pen divider = new Pen(Palette.CardBorder, 1f))
            {
                g.DrawLine(
                    divider,
                    24f,
                    commandY + 30f,
                    Width - 24f,
                    commandY + 30f
                );
            }
            Ui.DrawLabel(g, lastEvent, tinyFont, Palette.Muted, 24f, commandY + 42f);

            if (!connected)
            {
                using (SolidBrush dim = new SolidBrush(Color.FromArgb(150, Palette.Background)))
                {
                    Ui.FillRound(g, dim, card, 20f);
                }
                using (StringFormat centered = new StringFormat())
                {
                    centered.Alignment = StringAlignment.Center;
                    centered.LineAlignment = StringAlignment.Center;
                    using (SolidBrush brush = new SolidBrush(Palette.Muted))
                    using (Font font = new Font("Segoe UI", 11f, FontStyle.Bold))
                    {
                        g.DrawString(
                            Loc.T("gauge.waiting"),
                            font,
                            brush,
                            new RectangleF(card.X, card.Y, card.Width, card.Height),
                            centered
                        );
                    }
                }
            }
        }

        private static void DrawTip(Graphics g, RectangleF rect, float angleDegrees, float radius)
        {
            double radians = angleDegrees * Math.PI / 180.0;
            float cx = rect.X + rect.Width / 2f;
            float cy = rect.Y + rect.Height / 2f;
            float x = cx + (float)Math.Cos(radians) * (rect.Width / 2f);
            float y = cy + (float)Math.Sin(radians) * (rect.Height / 2f);
            using (SolidBrush glow = new SolidBrush(Color.FromArgb(65, Palette.Cyan)))
            {
                g.FillEllipse(glow, x - radius * 2f, y - radius * 2f, radius * 4f, radius * 4f);
            }
            using (SolidBrush dot = new SolidBrush(Palette.Cyan))
            {
                g.FillEllipse(dot, x - radius, y - radius, radius * 2f, radius * 2f);
            }
        }
    }
}
