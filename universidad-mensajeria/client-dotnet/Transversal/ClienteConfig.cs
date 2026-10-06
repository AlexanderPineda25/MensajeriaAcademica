using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace Mensajeria.Transversal
{
    /// <summary>
    /// Unico lector/escritor de client.properties en el cliente .NET (MUERTE-5).
    ///
    /// Antes conviven tres analisadores propios en el mismo proyecto
    /// (FachadaCliente.CargarPropiedades, LoginWindow.CargarServidor y
    /// Tema.Leer/Guardar) con dos rutas distintas: BaseDirectory y el directorio
    /// de trabajo. Consecuencia: el servidor que *muestra* el login puede no ser
    /// el que *usa* la fachada, y Guardar escribia en un sitio que Leer no
    /// consultaba. Aqui se resuelve una sola vez y todos leen de aqui.
    ///
    /// Formato (PROTOCOLO.md §5.2, identico en Java y Python): lineas
    /// `clave=valor`, `#` comenta, vacias se ignoran, el valor es todo lo que va
    /// despues del primer `=` y se recortan los espacios de ambos lados.
    /// </summary>
    public static class ClienteConfig
    {
        public const string Fichero = "client.properties";

        public const string HostPorDefecto = "localhost";
        public const int PuertoPorDefecto = 5000;
        public const int TlsPuertoPorDefecto = 5001;
        public const string DbUrlPorDefecto = "sqlite:///~/mensajeria_cliente.db";

        /// <summary>
        /// Ruta unica: primero junto al ejecutable (que es donde viaja en la
        /// distribucion) y, si no esta, el directorio de trabajo. Antes cada
        /// consumidor elegia una de las dos por su cuenta.
        /// </summary>
        public static string Ruta
        {
            get
            {
                if (_rutaForzada != null) return _rutaForzada;
                string junto = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, Fichero);
                if (File.Exists(junto)) return junto;
                string actual = Path.Combine(Directory.GetCurrentDirectory(), Fichero);
                return File.Exists(actual) ? actual : junto;
            }
        }

        /// <summary>
        /// Seam para los tests: fija el fichero a leer/escribir. En produccion
        /// se queda null y manda <see cref="Ruta"/>.
        /// </summary>
        public static string? RutaForzadoParaTest
        {
            get => _rutaForzada;
            set => _rutaForzada = value;
        }

        private static string? _rutaForzada;

        /// <summary>Todas las claves del fichero; vacio si no existe.</summary>
        public static Dictionary<string, string> Leer()
        {
            var props = new Dictionary<string, string>(StringComparer.Ordinal);
            try
            {
                string ruta = Ruta;
                if (!File.Exists(ruta)) return props;
                foreach (var linea in File.ReadAllLines(ruta))
                {
                    var t = linea.Trim();
                    if (t.Length == 0 || t.StartsWith("#")) continue;
                    int eq = t.IndexOf('=');
                    if (eq < 0) continue;
                    string clave = t[..eq].Trim();
                    if (clave.Length > 0)
                        props[clave] = t[(eq + 1)..].Trim();
                }
            }
            catch { }
            return props;
        }

        public static string Valor(string clave, string porDefecto)
        {
            string? v = Leer().GetValueOrDefault(clave);
            return string.IsNullOrWhiteSpace(v) ? porDefecto : v;
        }

        /// <summary>Entero validado por rango: fuera de rango gana el defecto.</summary>
        public static int Entero(string clave, int porDefecto, int min, int max)
        {
            if (int.TryParse(Leer().GetValueOrDefault(clave), out int v) && v >= min && v <= max)
                return v;
            return porDefecto;
        }

        public static bool Booleano(string clave, bool porDefecto)
        {
            string? v = Leer().GetValueOrDefault(clave);
            return string.IsNullOrWhiteSpace(v) ? porDefecto : v.Equals("true", StringComparison.OrdinalIgnoreCase);
        }

        /// <summary>
        /// Escribe una clave conservando el resto del fichero (orden, comentarios
        /// y claves ajenas). Si no existia, la anade al final.
        /// </summary>
        public static void Guardar(string clave, string valor)
        {
            try
            {
                string ruta = Ruta;
                var lineas = File.Exists(ruta)
                    ? File.ReadAllLines(ruta).ToList()
                    : new List<string>();
                string prefijo = clave + "=";
                bool puesta = false;
                for (int i = 0; i < lineas.Count; i++)
                {
                    var t = lineas[i].Trim();
                    if (!t.StartsWith("#") && t.StartsWith(prefijo, StringComparison.Ordinal))
                    {
                        lineas[i] = $"{clave}={valor}";
                        puesta = true;
                    }
                }
                if (!puesta) lineas.Add($"{clave}={valor}");
                File.WriteAllLines(ruta, lineas);
            }
            catch { }
        }
    }
}