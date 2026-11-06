using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace JustVibrait.Monitor
{
    /// <summary>Scrolling 15-second graph: accelerometer estimate and commanded level.</summary>
    public sealed class TimelineGraph : Control
    {
        private const int Capacity = 450;

        private readonly float[] measured = new float[Capacity];
        private readonly float[] commanded = new float[Capacity];
        private readonly bool[] beats = new bool[Capacity];
        private int head;
        private int count;

        private readonly Font titleFont;
        private readonly Font tinyFont;
        private readonly Font legendFont;

        public TimelineGraph()
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
            tinyFont = new Font("Segoe UI", 7.5f);
            legendFont = new Font("Segoe UI", 8f);
        }

        public void AddSample(float measuredLevel, float commandedLevel, bool beat)
        {
            measured[head] = Math.Max(0f, Math.Min(1f, measuredLevel));
            commanded[head] = Math.Max(0f, Math.Min(1f, commandedLevel));
            beats[head] = beat;
            head = (head + 1) % Capacity;
            if (count < Capacity) count++;
            Invalidate();
        }

        public void Clear()
        {
            head = 0;
            count = 0;
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
            float radius = Math.Max(12f, h * 0.05f);
            RectangleF card = new RectangleF(1f, 1f, w - 2f, h - 2f);
            using (SolidBrush bg = new SolidBrush(Palette.Panel))
            {
                Ui.FillRound(g, bg, card, radius);
            }
            using (Pen border = new Pen(Palette.CardBorder, 1f))
            {
                Ui.DrawRound(g, border, card, radius);
            }

            float p = Math.Max(14f, w * 0.02f);
            Ui.DrawLabel(g, Loc.T("timeline.title"), titleFont, Palette.Muted, p, 12f);

            DrawLegend(g, w, p);

            float top = 38f;
            float bottomAxis = 22f;
            float leftAxis = 34f;
            RectangleF plot = new RectangleF(
                p + leftAxis,
                top,
                w - p * 2f - leftAxis,
                h - top - bottomAxis - 8f
            );
            if (plot.Width < 30f || plot.Height < 30f) return;

            using (Pen grid = new Pen(Color.FromArgb(22, Palette.Text), 1f))
            {
                for (int i = 0; i <= 4; i++)
                {
                    float y = plot.Bottom - plot.Height * i / 4f;
                    grid.Color = i == 0
                        ? Color.FromArgb(45, Palette.Text)
                        : Color.FromArgb(20, Palette.Text);
                    g.DrawLine(grid, plot.Left, y, plot.Right, y);
                }
                for (int i = 0; i <= 5; i++)
                {
                    float x = plot.Left + plot.Width * i / 5f;
                    g.DrawLine(grid, x, plot.Top, x, plot.Bottom);
                }
            }

            using (StringFormat far = new StringFormat())
            {
                far.Alignment = StringAlignment.Far;
                far.LineAlignment = StringAlignment.Center;
                for (int i = 0; i <= 4; i++)
                {
                    float y = plot.Bottom - plot.Height * i / 4f;
                    string label = (i * 25).ToString() + "%";
                    using (SolidBrush brush = new SolidBrush(Palette.Dim))
                    {
                        g.DrawString(
                            label,
                            tinyFont,
                            brush,
                            new RectangleF(p - 2f, y - 8f, leftAxis - 4f, 16f),
                            far
                        );
                    }
                }
            }

            DrawTimeLabels(g, plot);

            if (count < 2)
            {
                using (StringFormat centered = new StringFormat())
                {
                    centered.Alignment = StringAlignment.Center;
                    centered.LineAlignment = StringAlignment.Center;
                    using (SolidBrush brush = new SolidBrush(Palette.Dim))
                    {
                        g.DrawString(
                            Loc.T("timeline.noData"),
                            legendFont,
                            brush,
                            plot,
                            centered
                        );
                    }
                }
                return;
            }

            float step = plot.Width / (Capacity - 1f);

            GraphicsState state = g.Save();
            g.SetClip(plot);

            PointF[] measuredPoints = new PointF[count];
            PointF[] commandedPoints = new PointF[count];
            for (int i = 0; i < count; i++)
            {
                int index = (head - count + i + Capacity * 2) % Capacity;
                float x = plot.Right - (count - 1 - i) * step;
                measuredPoints[i] = new PointF(x, plot.Bottom - measured[index] * plot.Height);
                commandedPoints[i] = new PointF(x, plot.Bottom - commanded[index] * plot.Height);
                if (beats[index])
                {
                    using (Pen beatPen = new Pen(Color.FromArgb(55, Palette.Magenta), 1f))
                    {
                        g.DrawLine(
                            beatPen,
                            x,
                            plot.Top,
                            x,
                            plot.Top + Math.Min(10f, plot.Height * 0.08f)
                        );
                    }
                }
            }

            using (GraphicsPath fill = new GraphicsPath())
            {
                fill.AddLines(measuredPoints);
                fill.AddLine(
                    measuredPoints[count - 1].X,
                    plot.Bottom,
                    measuredPoints[0].X,
                    plot.Bottom
                );
                fill.CloseFigure();
                using (LinearGradientBrush fillBrush = new LinearGradientBrush(
                    plot,
                    Color.FromArgb(78, Palette.Cyan),
                    Color.FromArgb(0, Palette.Cyan),
                    90f
                ))
                {
                    g.FillPath(fillBrush, fill);
                }
            }

            using (Pen commandPen = new Pen(Color.FromArgb(210, Palette.Magenta), 1.6f))
            {
                commandPen.LineJoin = LineJoin.Round;
                g.DrawLines(commandPen, commandedPoints);
            }
            using (Pen measuredPen = new Pen(Palette.Cyan, 2.1f))
            {
                measuredPen.LineJoin = LineJoin.Round;
                g.DrawLines(measuredPen, measuredPoints);
            }

            g.Restore(state);
        }

        private void DrawLegend(Graphics g, float width, float padding)
        {
            RectangleF area = new RectangleF(width * 0.45f, 11f, width - padding - width * 0.45f, 18f);
            SizeF commandSize = g.MeasureString(Loc.T("timeline.command"), legendFont);
            SizeF factSize = g.MeasureString(Loc.T("timeline.fact"), legendFont);
            float commandX = area.Right - commandSize.Width;
            float factX = commandX - 18f - factSize.Width;
            DrawDot(g, factX - 12f, 17f, Palette.Cyan);
            using (SolidBrush brush = new SolidBrush(Palette.Muted))
            {
                g.DrawString(Loc.T("timeline.fact"), legendFont, brush, factX, 11f);
            }
            DrawDot(g, commandX - 12f, 17f, Palette.Magenta);
            using (SolidBrush brush = new SolidBrush(Palette.Muted))
            {
                g.DrawString(Loc.T("timeline.command"), legendFont, brush, commandX, 11f);
            }
        }

        private static void DrawDot(Graphics g, float x, float y, Color color)
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.FillEllipse(brush, x, y - 3f, 6f, 6f);
            }
        }

        private void DrawTimeLabels(Graphics g, RectangleF plot)
        {
            string sec = Loc.IsRussian ? "с" : "s";
            string[] labels = new string[] { "-15 " + sec, "-10 " + sec, "-5 " + sec, Loc.T("timeline.now") };
            using (StringFormat centered = new StringFormat())
            {
                centered.Alignment = StringAlignment.Center;
                centered.LineAlignment = StringAlignment.Near;
                using (SolidBrush brush = new SolidBrush(Palette.Dim))
                {
                    for (int i = 0; i < labels.Length; i++)
                    {
                        float x = plot.Left + plot.Width * i / (labels.Length - 1f);
                        RectangleF rect = new RectangleF(x - 30f, plot.Bottom + 4f, 60f, 16f);
                        if (i == 0) centered.Alignment = StringAlignment.Near;
                        else if (i == labels.Length - 1) centered.Alignment = StringAlignment.Far;
                        else centered.Alignment = StringAlignment.Center;
                        g.DrawString(labels[i], tinyFont, brush, rect, centered);
                    }
                }
            }
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                titleFont.Dispose();
                tinyFont.Dispose();
                legendFont.Dispose();
            }
            base.Dispose(disposing);
        }
    }
}
