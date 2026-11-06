using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace JustVibrait.Monitor
{
    internal static class SelfTest
    {
        private const int LoopbackPort = 45455;

        private const string SampleLine =
            "JV1\t42\t123456\tPixel 8 Pro\t15\t35\tSYSTEM\tDYNAMIC\t0.720\t0.340\t" +
            "0.510\t0.430\t126\t1\t0.640\t9.800\t1\t0\t0:1:180:24:1;1:1:120:20:0";

        private const string NoMotorLine =
            "JV1\t43\t123456\tPixel\t15\t35\tLOCAL\tSOFT\t0.500\t0.300\t0.100\t" +
            "0.200\t0\t0\t0.000\t0.000\t1\t0\t";

        private static int failures;

        private static void Main()
        {
            try
            {
                Console.OutputEncoding = Encoding.UTF8;
            }
            catch (Exception)
            {
            }
            Console.WriteLine("JustVibrait Motor Monitor · self-tests");
            Console.WriteLine("----------------------------------------");

            TestValidPacket();
            TestInvalidPackets();
            TestNoMotors();
            TestUdpLoopback();

            Console.WriteLine("----------------------------------------");
            if (failures == 0)
            {
                Console.WriteLine("Все проверки пройдены.");
                Environment.Exit(0);
            }
            else
            {
                Console.WriteLine("Провалено проверок: " + failures);
                Environment.Exit(1);
            }
        }

        private static void TestValidPacket()
        {
            TelemetryPacket packet;
            bool parsed = TelemetryPacket.TryParse(SampleLine, out packet);
            Check("valid packet parses", parsed);
            if (!parsed) return;

            Check("sequence", packet.Sequence == 42);
            Check("device name with spaces", packet.Device == "Pixel 8 Pro");
            Check("android version", packet.AndroidVersion == "15");
            Check("source", packet.Source == "SYSTEM");
            Check("profile", packet.Profile == "DYNAMIC");
            Check("intensity", Math.Abs(packet.Intensity - 0.72f) < 0.0005f);
            Check("measured", Math.Abs(packet.Measured - 0.64f) < 0.0005f);
            Check("measured m/s2", Math.Abs(packet.MeasuredMs2 - 9.8f) < 0.0005f);
            Check("beat flag", packet.Beat);
            Check("sensor flag", packet.HasSensor);
            Check("default motor id", packet.DefaultMotorId == 0);
            Check("two motors parsed", packet.Motors.Count == 2);
            if (packet.Motors.Count == 2)
            {
                Check("motor 0 amplitude", packet.Motors[0].Amplitude == 180);
                Check("motor 0 duration", packet.Motors[0].DurationMs == 24);
                Check("motor 0 is default", packet.Motors[0].IsDefault);
                Check("motor 1 amplitude", packet.Motors[1].Amplitude == 120);
                Check("motor 1 not default", !packet.Motors[1].IsDefault);
            }
            Check("commanded amplitude is max", packet.CommandedAmplitude == 180);
            Check(
                "commanded level",
                Math.Abs(packet.CommandedLevel - 180f / 255f) < 0.0005f
            );
        }

        private static void TestInvalidPackets()
        {
            TelemetryPacket packet;

            Check("null rejected", !TelemetryPacket.TryParse(null, out packet));
            Check("empty rejected", !TelemetryPacket.TryParse("", out packet));
            Check("wrong magic rejected", !TelemetryPacket.TryParse(SampleLine.Replace("JV1", "JV2"), out packet));
            Check("short packet rejected", !TelemetryPacket.TryParse("JV1\t1\t2\t3", out packet));
            Check(
                "bad sequence rejected",
                !TelemetryPacket.TryParse(SampleLine.Replace("\t42\t", "\tabc\t"), out packet)
            );
            Check(
                "bad motor rejected",
                !TelemetryPacket.TryParse(
                    SampleLine.Replace("0:1:180:24:1", "0:1:oops:24:1"),
                    out packet
                )
            );
            Check(
                "garbage rejected",
                !TelemetryPacket.TryParse("что-то совсем не то", out packet)
            );
        }

        private static void TestNoMotors()
        {
            TelemetryPacket packet;
            bool parsed = TelemetryPacket.TryParse(NoMotorLine, out packet);
            Check("packet without motors parses", parsed);
            if (!parsed) return;
            Check("no motors in list", packet.Motors.Count == 0);
            Check("commanded amplitude zero", packet.CommandedAmplitude == 0);
            Check("source LOCAL", packet.Source == "LOCAL");
        }

        private static void TestUdpLoopback()
        {
            TelemetryPacket captured = null;
            bool received = false;

            using (UdpTelemetryListener listener = new UdpTelemetryListener(LoopbackPort))
            using (ManualResetEvent signal = new ManualResetEvent(false))
            {
                listener.PacketReceived += delegate(TelemetryPacket packet)
                {
                    captured = packet;
                    signal.Set();
                };
                listener.Start();

                byte[] bytes = Encoding.UTF8.GetBytes(SampleLine);
                using (UdpClient sender = new UdpClient())
                {
                    sender.Send(
                        bytes,
                        bytes.Length,
                        new IPEndPoint(IPAddress.Loopback, LoopbackPort)
                    );
                }

                received = signal.WaitOne(3000);
            }

            Check("udp loopback received", received);
            Check("udp loopback parsed", received && captured != null);
            if (received && captured != null)
            {
                Check("udp sequence", captured.Sequence == 42);
                Check("udp motors", captured.Motors.Count == 2);
            }
        }

        private static void Check(string name, bool condition)
        {
            if (condition)
            {
                Console.WriteLine("[OK]   " + name);
            }
            else
            {
                failures++;
                Console.WriteLine("[FAIL] " + name);
            }
        }
    }
}
