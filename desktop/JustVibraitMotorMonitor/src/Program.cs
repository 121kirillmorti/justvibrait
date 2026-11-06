using System;
using System.Threading;
using System.Windows.Forms;

namespace JustVibrait.Monitor
{
    internal static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            Application.ThreadException += delegate(object sender, ThreadExceptionEventArgs e)
            {
                MessageBox.Show(
                    "Ошибка интерфейса:\r\n" + e.Exception.Message,
                    "JustVibrait · Монитор вибромоторов",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            };

            AppDomain.CurrentDomain.UnhandledException += delegate(
                object sender,
                UnhandledExceptionEventArgs e
            )
            {
                Exception error = e.ExceptionObject as Exception;
                MessageBox.Show(
                    "Непредвиденная ошибка:\r\n" +
                    (error == null ? "неизвестно" : error.Message),
                    "JustVibrait · Монитор вибромоторов",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            };

            MotorMonitorForm form = new MotorMonitorForm();
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == "--demo" || args[i] == "/demo")
                {
                    form.StartInDemoMode();
                }
            }
            Application.Run(form);
        }
    }
}