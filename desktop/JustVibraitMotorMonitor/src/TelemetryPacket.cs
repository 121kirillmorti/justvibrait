using System;
using System.Collections.Generic;
using System.Globalization;

namespace JustVibrait.Monitor
{
    public sealed class MotorState
    {
        public int Id;
        public bool HasAmplitudeControl;
        public int Amplitude;
        public long DurationMs;
        public bool IsDefault;

        public float Level
        {
            get { return Amplitude / 255f; }
        }
    }

    public sealed class TelemetryPacket
    {
        public int Sequence;
        public long UptimeMs;
        public string Device = "Android device";
        public string AndroidVersion = "";
        public int Sdk;
        public string Source = "LOCAL";
        public string Profile = "DYNAMIC";
        public float Intensity;
        public float Threshold;
        public float Bass;
        public float Level;
        public int Bpm;
        public bool Beat;
        public float Measured;
        public float MeasuredMs2;
        public bool HasSensor;
        public int DefaultMotorId;
        public List<MotorState> Motors = new List<MotorState>();
        public long ReceivedAtTicks;

        public int CommandedAmplitude
        {
            get
            {
                int value = 0;
                for (int i = 0; i < Motors.Count; i++)
                {
                    if (Motors[i].Amplitude > value) value = Motors[i].Amplitude;
                }
                return value;
            }
        }

        public float CommandedLevel
        {
            get { return CommandedAmplitude / 255f; }
        }

        public static bool TryParse(string line, out TelemetryPacket packet)
        {
            packet = null;
            if (string.IsNullOrEmpty(line)) return false;

            string[] parts = line.Trim('\r', '\n', ' ', '\uFEFF').Split('\t');
            if (parts.Length < 19 || parts[0] != "JV1") return false;

            try
            {
                TelemetryPacket p = new TelemetryPacket();
                p.Sequence = int.Parse(parts[1], CultureInfo.InvariantCulture);
                p.UptimeMs = long.Parse(parts[2], CultureInfo.InvariantCulture);
                p.Device = parts[3];
                p.AndroidVersion = parts[4];
                p.Sdk = int.Parse(parts[5], CultureInfo.InvariantCulture);
                p.Source = parts[6];
                p.Profile = parts[7];
                p.Intensity = ParseFloat(parts[8]);
                p.Threshold = ParseFloat(parts[9]);
                p.Bass = ParseFloat(parts[10]);
                p.Level = ParseFloat(parts[11]);
                p.Bpm = int.Parse(parts[12], CultureInfo.InvariantCulture);
                p.Beat = parts[13] == "1";
                p.Measured = ParseFloat(parts[14]);
                p.MeasuredMs2 = ParseFloat(parts[15]);
                p.HasSensor = parts[16] == "1";
                p.DefaultMotorId = int.Parse(parts[17], CultureInfo.InvariantCulture);
                p.Motors = ParseMotors(parts[18]);
                p.ReceivedAtTicks = DateTime.UtcNow.Ticks;
                packet = p;
                return true;
            }
            catch (Exception)
            {
                return false;
            }
        }

        private static float ParseFloat(string value)
        {
            return float.Parse(
                value,
                NumberStyles.Float,
                CultureInfo.InvariantCulture
            );
        }

        private static List<MotorState> ParseMotors(string spec)
        {
            List<MotorState> result = new List<MotorState>();
            if (string.IsNullOrEmpty(spec)) return result;

            string[] motors = spec.Split(';');
            for (int i = 0; i < motors.Length; i++)
            {
                string[] fields = motors[i].Split(':');
                if (fields.Length < 5) continue;
                MotorState motor = new MotorState();
                motor.Id = int.Parse(fields[0], CultureInfo.InvariantCulture);
                motor.HasAmplitudeControl = fields[1] == "1";
                motor.Amplitude = int.Parse(fields[2], CultureInfo.InvariantCulture);
                motor.DurationMs = long.Parse(fields[3], CultureInfo.InvariantCulture);
                motor.IsDefault = fields[4] == "1";
                result.Add(motor);
            }
            return result;
        }
    }
}
