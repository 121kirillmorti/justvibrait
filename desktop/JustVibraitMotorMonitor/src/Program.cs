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
            ApplyLanguageArgs(args);
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            Application.ThreadException += delegate(object sender, ThreadExceptionEventArgs e)
            {
                MessageBox.Show(
                    Loc.T("prog.uiError") + e.Exception.Message,
                    Loc.T("form.title"),
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
                    Loc.T("prog.unexpected") +
                    (error == null ? Loc.T("prog.unknown") : error.Message),
                    Loc.T("form.title"),
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

        private static void ApplyLanguageArgs(string[] args)
        {
            for (int i = 0; i < args.Length; i++)
            {
                string arg = args[i].ToLowerInvariant();
                if (arg == "--lang=ru" || arg == "/lang=ru" || arg == "--ru")
                {
                    Loc.SetLanguage(MonitorLanguage.Russian);
                }
                else if (arg == "--lang=en" || arg == "/lang=en" || arg == "--en")
                {
                    Loc.SetLanguage(MonitorLanguage.English);
                }
            }
        }
    }
}
