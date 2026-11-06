using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;

namespace JustVibrait.Monitor
{
    public enum MonitorLanguage
    {
        Russian,
        English
    }

    /// <summary>
    /// Desktop monitor localization (RU/EN).
    /// Auto-detects the Windows UI language, persists the user's choice,
    /// and can be forced with --lang=ru / --lang=en.
    /// </summary>
    public static class Loc
    {
        private static MonitorLanguage current = Load();

        public static MonitorLanguage Current
        {
            get { return current; }
        }

        public static bool IsRussian
        {
            get { return current == MonitorLanguage.Russian; }
        }

        public static event EventHandler LanguageChanged;

        public static void SetLanguage(MonitorLanguage language)
        {
            if (current == language) return;
            current = language;
            Save(language);
            EventHandler handler = LanguageChanged;
            if (handler != null) handler(null, EventArgs.Empty);
        }

        public static void Toggle()
        {
            SetLanguage(current == MonitorLanguage.Russian
                ? MonitorLanguage.English
                : MonitorLanguage.Russian);
        }

        public static string T(string key)
        {
            string value;
            if (IsRussian)
            {
                if (Russian.TryGetValue(key, out value)) return value;
            }
            else
            {
                if (English.TryGetValue(key, out value)) return value;
            }
            if (Russian.TryGetValue(key, out value)) return value;
            return key;
        }

        public static string F(string key, params object[] args)
        {
            return string.Format(CultureInfo.InvariantCulture, T(key), args);
        }

        public static string SourceLabel(string source)
        {
            if (source == "SYSTEM") return T("source.system");
            if (source == "MIC") return T("source.mic");
            return T("source.local");
        }

        public static string ProfileLabel(string profile)
        {
            if (profile == "SOFT") return T("profile.soft");
            if (profile == "SHARP") return T("profile.sharp");
            if (profile == "DYNAMIC") return T("profile.dynamic");
            return profile;
        }

        private static MonitorLanguage Load()
        {
            try
            {
                string path = SettingsPath();
                if (File.Exists(path))
                {
                    string saved = File.ReadAllText(path).Trim().ToLowerInvariant();
                    if (saved.StartsWith("en")) return MonitorLanguage.English;
                    if (saved.StartsWith("ru")) return MonitorLanguage.Russian;
                }
            }
            catch (Exception)
            {
            }
            try
            {
                if (CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "ru")
                {
                    return MonitorLanguage.Russian;
                }
            }
            catch (Exception)
            {
            }
            return MonitorLanguage.English;
        }

        private static void Save(MonitorLanguage language)
        {
            try
            {
                string path = SettingsPath();
                string dir = Path.GetDirectoryName(path);
                if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                File.WriteAllText(path, language == MonitorLanguage.English ? "en" : "ru");
            }
            catch (Exception)
            {
            }
        }

        private static string SettingsPath()
        {
            string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(local, "JustVibrait", "monitor-language.txt");
        }

        private static readonly Dictionary<string, string> Russian = BuildRussian();
        private static readonly Dictionary<string, string> English = BuildEnglish();

        private static Dictionary<string, string> BuildRussian()
        {
            Dictionary<string, string> d = new Dictionary<string, string>(StringComparer.Ordinal);
            d["form.title"] = "JustVibrait · Монитор вибромоторов";
            d["gauge.title"] = "ФАКТИЧЕСКАЯ ВИБРАЦИЯ";
            d["gauge.tag"] = "АКСЕЛЕРОМЕТР";
            d["gauge.measured"] = "ИЗМЕРЕНО";
            d["gauge.noSensor"] = "сенсор недоступен";
            d["gauge.cmdIdle"] = "КОМАНДА  0  ·  ожидание баса";
            d["gauge.cmdFmt"] = "КОМАНДА  {0}   ·   {1} / 255";
            d["gauge.waiting"] = "ОЖИДАНИЕ ТЕЛЕМЕТРИИ";
            d["gauge.noPulses"] = "импульсов ещё не было";
            d["gauge.waitingNext"] = "ожидание следующего импульса";
            d["gauge.noAnswer"] = "телефон не отвечает · проверьте Wi-Fi и UDP 45454";
            d["gauge.pulseFmt"] = "импульс · мотор {0} · {1} мс · {2} / 255";
            d["motor.label"] = "МОТОР";
            d["motor.primary"] = "ОСНОВНОЙ";
            d["motor.secondary"] = "ДОПОЛНИТЕЛЬНЫЙ";
            d["motor.noData"] = "НЕТ ДАННЫХ";
            d["motor.cmdFmt"] = "КОМАНДА {0} / 255";
            d["motor.noAmp"] = "амплитуда не управляется · вкл/выкл";
            d["motor.hasAmp"] = "амплитуда управляема · 1–255";
            d["motor.connect"] = "подключите телефон";
            d["motor.pulseFmt"] = "ИМПУЛЬС {0} МС";
            d["motor.waiting"] = "ОЖИДАНИЕ";
            d["empty.waitTitle"] = "ОЖИДАНИЕ ПЕРВОГО ПАКЕТА";
            d["empty.waitBody"] = "Включите «Монитор на ПК» в настройках приложения и подключите телефон к той же Wi-Fi сети.";
            d["empty.noMotorsTitle"] = "МОТОРЫ НЕ ОБНАРУЖЕНЫ";
            d["empty.noMotorsBody"] = "Устройство не сообщило ни одного вибромотора. Возможно, оно не поддерживает вибрацию.";
            d["timeline.title"] = "ТАЙМЛАЙН · 15 СЕКУНД";
            d["timeline.noData"] = "НЕТ ДАННЫХ · ВКЛЮЧИТЕ ТЕЛЕМЕТРИЮ";
            d["timeline.fact"] = "ФАКТ";
            d["timeline.command"] = "КОМАНДА";
            d["timeline.now"] = "сейчас";
            d["header.subtitle"] = "МОНИТОР ВИБРОМОТОРОВ · KOTIKTORTIKTECH";
            d["header.demo"] = "ДЕМО";
            d["header.stop"] = "СТОП";
            d["header.noPhone"] = "нет телефона";
            d["header.pcFmt"] = "ПК {0} · UDP {1}";
            d["header.demoSuffix"] = " · ДЕМО";
            d["metrics.signal"] = "СИГНАЛ";
            d["metrics.audio"] = "АУДИОАНАЛИЗ";
            d["metrics.bass"] = "БАС";
            d["metrics.level"] = "УРОВЕНЬ";
            d["metrics.tempo"] = "ТЕМП";
            d["metrics.threshold"] = "ПОРОГ";
            d["metrics.intensity"] = "ИНТЕНСИВНОСТЬ";
            d["source.system"] = "Системный звук";
            d["source.mic"] = "Микрофон";
            d["source.local"] = "Локальный файл";
            d["profile.soft"] = "Мягкий";
            d["profile.sharp"] = "Резкий";
            d["profile.dynamic"] = "Динамический";
            d["ui.noDevice"] = "нет устройства";
            d["form.demoDevice"] = "Pixel 8 Pro · демо";
            d["status.demo"] = "ДЕМО-РЕЖИМ · синтетическая телеметрия для проверки интерфейса";
            d["status.liveFmt"] = "Телеметрия активна · {0} · профиль {1}";
            d["status.udpErrorFmt"] = "UDP {0} недоступен: {1}";
            d["status.waitingFmt"] = "Ожидание телефона · Настройки → Отладка → Монитор на ПК (UDP {0})";
            d["status.rateFmt"] = "{0} пак/с · {1} мс назад";
            d["status.pcFmt"] = "ПК {0} · UDP {1}";
            d["prog.uiError"] = "Ошибка интерфейса:\r\n";
            d["prog.unexpected"] = "Непредвиденная ошибка:\r\n";
            d["prog.unknown"] = "неизвестно";
            return d;
        }

        private static Dictionary<string, string> BuildEnglish()
        {
            Dictionary<string, string> d = new Dictionary<string, string>(StringComparer.Ordinal);
            d["form.title"] = "JustVibrait · Vibrator monitor";
            d["gauge.title"] = "ACTUAL VIBRATION";
            d["gauge.tag"] = "ACCELEROMETER";
            d["gauge.measured"] = "MEASURED";
            d["gauge.noSensor"] = "sensor unavailable";
            d["gauge.cmdIdle"] = "COMMAND  0  ·  waiting for bass";
            d["gauge.cmdFmt"] = "COMMAND  {0}   ·   {1} / 255";
            d["gauge.waiting"] = "WAITING FOR TELEMETRY";
            d["gauge.noPulses"] = "no pulses yet";
            d["gauge.waitingNext"] = "waiting for next pulse";
            d["gauge.noAnswer"] = "phone is not responding · check Wi-Fi and UDP 45454";
            d["gauge.pulseFmt"] = "pulse · motor {0} · {1} ms · {2} / 255";
            d["motor.label"] = "MOTOR";
            d["motor.primary"] = "PRIMARY";
            d["motor.secondary"] = "SECONDARY";
            d["motor.noData"] = "NO DATA";
            d["motor.cmdFmt"] = "COMMAND {0} / 255";
            d["motor.noAmp"] = "amplitude not controllable · on/off";
            d["motor.hasAmp"] = "amplitude controllable · 1–255";
            d["motor.connect"] = "connect the phone";
            d["motor.pulseFmt"] = "PULSE {0} MS";
            d["motor.waiting"] = "WAITING";
            d["empty.waitTitle"] = "WAITING FOR FIRST PACKET";
            d["empty.waitBody"] = "Enable \"PC monitor\" in app settings and join the same Wi-Fi network.";
            d["empty.noMotorsTitle"] = "NO MOTORS FOUND";
            d["empty.noMotorsBody"] = "The device did not report any vibrators. It may not support vibration.";
            d["timeline.title"] = "TIMELINE · 15 SECONDS";
            d["timeline.noData"] = "NO DATA · ENABLE TELEMETRY";
            d["timeline.fact"] = "ACTUAL";
            d["timeline.command"] = "COMMAND";
            d["timeline.now"] = "now";
            d["header.subtitle"] = "VIBRATOR MONITOR · KOTIKTORTIKTECH";
            d["header.demo"] = "DEMO";
            d["header.stop"] = "STOP";
            d["header.noPhone"] = "no phone";
            d["header.pcFmt"] = "PC {0} · UDP {1}";
            d["header.demoSuffix"] = " · DEMO";
            d["metrics.signal"] = "SIGNAL";
            d["metrics.audio"] = "AUDIO ANALYSIS";
            d["metrics.bass"] = "BASS";
            d["metrics.level"] = "LEVEL";
            d["metrics.tempo"] = "TEMPO";
            d["metrics.threshold"] = "THRESHOLD";
            d["metrics.intensity"] = "INTENSITY";
            d["source.system"] = "System sound";
            d["source.mic"] = "Microphone";
            d["source.local"] = "Local file";
            d["profile.soft"] = "Soft";
            d["profile.sharp"] = "Sharp";
            d["profile.dynamic"] = "Dynamic";
            d["ui.noDevice"] = "no device";
            d["form.demoDevice"] = "Pixel 8 Pro · demo";
            d["status.demo"] = "DEMO MODE · synthetic telemetry for UI check";
            d["status.liveFmt"] = "Telemetry live · {0} · profile {1}";
            d["status.udpErrorFmt"] = "UDP {0} unavailable: {1}";
            d["status.waitingFmt"] = "Waiting for phone · Settings → Debug → PC monitor (UDP {0})";
            d["status.rateFmt"] = "{0} pkt/s · {1} ms ago";
            d["status.pcFmt"] = "PC {0} · UDP {1}";
            d["prog.uiError"] = "UI error:\r\n";
            d["prog.unexpected"] = "Unexpected error:\r\n";
            d["prog.unknown"] = "unknown";
            return d;
        }
    }
}
