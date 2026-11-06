using System;
using System.Drawing;
using System.Drawing.Drawing2D;

namespace JustVibrait.Monitor
{
    public static class Palette
    {
        public static readonly Color Background = ColorTranslator.FromHtml("#080B10");
        public static readonly Color Panel = ColorTranslator.FromHtml("#0F141C");
        public static readonly Color Card = ColorTranslator.FromHtml("#141B26");
        public static readonly Color CardBorder = ColorTranslator.FromHtml("#222D3D");
        public static readonly Color Text = ColorTranslator.FromHtml("#E8EFF9");
        public static readonly Color Muted = ColorTranslator.FromHtml("#8492A8");
        public static readonly Color Dim = ColorTranslator.FromHtml("#556276");
        public static readonly Color Cyan = ColorTranslator.FromHtml("#35D6FF");
        public static readonly Color Blue = ColorTranslator.FromHtml("#4A8CFF");
        public static readonly Color Magenta = ColorTranslator.FromHtml("#FF4FD8");
        public static readonly Color Green = ColorTranslator.FromHtml("#4ADE80");
        public static readonly Color Yellow = ColorTranslator.FromHtml("#FACC15");
        public static readonly Color Red = ColorTranslator.FromHtml("#FB7185");
    }

    public static class Ui
    {
        public static GraphicsPath RoundRect(RectangleF bounds, float radius)
        {
            float diameter = radius * 2f;
            GraphicsPath path = new GraphicsPath();
            path.AddArc(bounds.X, bounds.Y, diameter, diameter, 180f, 90f);
            path.AddArc(bounds.Right - diameter, bounds.Y, diameter, diameter, 270f, 90f);
            path.AddArc(
                bounds.Right - diameter,
                bounds.Bottom - diameter,
                diameter,
                diameter,
                0f,
                90f
            );
            path.AddArc(bounds.X, bounds.Bottom - diameter, diameter, diameter, 90f, 90f);
            path.CloseFigure();
            return path;
        }

        public static void FillRound(Graphics g, Brush brush, RectangleF bounds, float radius)
        {
            using (GraphicsPath path = RoundRect(bounds, radius))
            {
                g.FillPath(brush, path);
            }
        }

        public static void DrawRound(Graphics g, Pen pen, RectangleF bounds, float radius)
        {
            using (GraphicsPath path = RoundRect(bounds, radius))
            {
                g.DrawPath(pen, path);
            }
        }

        public static Color Blend(Color from, Color to, float amount)
        {
            float t = Math.Max(0f, Math.Min(1f, amount));
            return Color.FromArgb(
                (int)(from.A + (to.A - from.A) * t),
                (int)(from.R + (to.R - from.R) * t),
                (int)(from.G + (to.G - from.G) * t),
                (int)(from.B + (to.B - from.B) * t)
            );
        }

        public static string Percent(float value)
        {
            int percent = (int)Math.Round(Math.Max(0f, Math.Min(1f, value)) * 100f);
            return percent.ToString() + "%";
        }

        public static string SourceLabel(string source)
        {
            if (source == "SYSTEM") return "Системный звук";
            if (source == "MIC") return "Микрофон";
            return "Локальный файл";
        }

        public static string ProfileLabel(string profile)
        {
            if (profile == "SOFT") return "Мягкий";
            if (profile == "SHARP") return "Резкий";
            if (profile == "DYNAMIC") return "Динамический";
            return profile;
        }

        public static string DeviceLabel(TelemetryPacket packet)
        {
            if (packet == null) return "нет устройства";
            if (string.IsNullOrEmpty(packet.AndroidVersion)) return packet.Device;
            return packet.Device + "  ·  Android " + packet.AndroidVersion;
        }

        public static void DrawLabel(
            Graphics g,
            string text,
            Font font,
            Color color,
            float x,
            float y
        )
        {
            using (SolidBrush brush = new SolidBrush(color))
            {
                g.DrawString(text, font, brush, x, y);
            }
        }
    }
}
