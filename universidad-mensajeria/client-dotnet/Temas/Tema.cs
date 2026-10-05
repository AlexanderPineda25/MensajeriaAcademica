using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;

namespace Mensajeria.Temas
{
    /// <summary>Modo Normal (claro) / Oscuro (UI-09). Oscuro por defecto.</summary>
    public static class Tema
    {
        public const string Oscuro = "oscuro";
        public const string Claro = "claro";
        private const string Clave = "tema";
        private const string Fichero = "client.properties";

        public static string Normalizar(string? modo) =>
            modo == Claro ? Claro : Oscuro;

        public static string Leer()
        {
            try
            {
                if (File.Exists(Fichero))
                    foreach (var linea in File.ReadAllLines(Fichero))
                    {
                        var t = linea.Trim();
                        if (t.StartsWith(Clave + "="))
                            return Normalizar(t.Substring(Clave.Length + 1).Trim());
                    }
            }
            catch { }
            return Oscuro;
        }

        public static void Guardar(string modo)
        {
            try
            {
                string valor = Normalizar(modo);
                var lineas = File.Exists(Fichero)
                    ? File.ReadAllLines(Fichero).ToList() : new List<string>();
                bool puesta = false;
                for (int i = 0; i < lineas.Count; i++)
                    if (lineas[i].Trim().StartsWith(Clave + "="))
                    { lineas[i] = $"{Clave}={valor}"; puesta = true; }
                if (!puesta) lineas.Add($"{Clave}={valor}");
                File.WriteAllLines(Fichero, lineas);
            }
            catch { }
        }

        public static void Aplicar(string modo)
        {
            string m = Normalizar(modo);
            var app = Application.Current;
            if (app == null) return;
            var recursos = app.Resources;
            // WPF-UI base claro/oscuro (por nombre: evita acoplar el namespace
            // exacto del paquete; el XAML ya usa Theme="Light"/"Dark").
            foreach (var d in recursos.MergedDictionaries.ToList())
            {
                if (d.GetType().Name != "ThemesDictionary") continue;
                var prop = d.GetType().GetProperty("Theme");
                if (prop == null || !prop.CanWrite) continue;
                try
                {
                    object valor = Enum.Parse(prop.PropertyType,
                        m == Claro ? "Light" : "Dark", ignoreCase: true);
                    prop.SetValue(d, valor);
                }
                catch { }
            }
            // Nuestro diccionario: se quita el anterior y se fusiona el nuevo.
            foreach (var d in recursos.MergedDictionaries.ToList())
            {
                var origen = d.Source?.ToString() ?? "";
                if (origen.Contains("TemaOscuro.xaml") || origen.Contains("TemaClaro.xaml"))
                    recursos.MergedDictionaries.Remove(d);
            }
            recursos.MergedDictionaries.Add(new ResourceDictionary
            {
                Source = new Uri(
                    $"pack://application:,,,/Mensajeria;component/Themes/{(m == Claro ? "TemaClaro" : "TemaOscuro")}.xaml",
                    UriKind.Absolute)
            });
            // Superficie de ventana según modo (se reemplaza: los recursos
            // XAML están congelados y no admiten mutación).
            recursos["AppSurfaceBrush"] = new System.Windows.Media.SolidColorBrush(
                m == Claro
                    ? System.Windows.Media.Color.FromRgb(0xEE, 0xF1, 0xF6)
                    : System.Windows.Media.Color.FromRgb(0x0E, 0x16, 0x21));
            recursos["AppTextBrush"] = new System.Windows.Media.SolidColorBrush(
                m == Claro
                    ? System.Windows.Media.Color.FromRgb(0x20, 0x21, 0x24)
                    : System.Windows.Media.Color.FromRgb(0xE8, 0xEE, 0xF2));
        }
    }
}
