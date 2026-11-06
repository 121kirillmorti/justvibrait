using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace JustVibrait.Monitor
{
    public sealed class UdpTelemetryListener : IDisposable
    {
        public event Action<TelemetryPacket> PacketReceived;
        public event Action<string> ReceiveError;

        private readonly int port;
        private UdpClient client;
        private Thread thread;
        private volatile bool running;

        public UdpTelemetryListener(int port)
        {
            this.port = port;
        }

        public void Start()
        {
            Stop();
            UdpClient udp = new UdpClient();
            udp.ExclusiveAddressUse = false;
            udp.Client.SetSocketOption(
                SocketOptionLevel.Socket,
                SocketOptionName.ReuseAddress,
                true
            );
            udp.Client.Bind(new IPEndPoint(IPAddress.Any, port));
            client = udp;
            running = true;
            thread = new Thread(ReceiveLoop);
            thread.IsBackground = true;
            thread.Name = "UDP telemetry listener";
            thread.Start();
        }

        private void ReceiveLoop()
        {
            IPEndPoint remote = new IPEndPoint(IPAddress.Any, 0);
            while (running)
            {
                try
                {
                    UdpClient current = client;
                    if (current == null) break;
                    byte[] data = current.Receive(ref remote);
                    string text = Encoding.UTF8.GetString(data);
                    TelemetryPacket packet;
                    if (TelemetryPacket.TryParse(text, out packet))
                    {
                        Action<TelemetryPacket> handler = PacketReceived;
                        if (handler != null) handler(packet);
                    }
                }
                catch (ObjectDisposedException)
                {
                    break;
                }
                catch (SocketException)
                {
                    if (!running) break;
                }
                catch (Exception ex)
                {
                    if (running)
                    {
                        Action<string> handler = ReceiveError;
                        if (handler != null) handler(ex.Message);
                    }
                }
            }
        }

        public void Stop()
        {
            running = false;
            UdpClient current = client;
            client = null;
            if (current != null)
            {
                try { current.Close(); }
                catch (Exception) { }
            }
            Thread currentThread = thread;
            thread = null;
            if (currentThread != null && currentThread.IsAlive)
            {
                try { currentThread.Join(500); }
                catch (Exception) { }
            }
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
