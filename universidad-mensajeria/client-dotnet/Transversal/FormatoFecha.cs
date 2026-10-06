using System;
using System.Globalization;

namespace Mensajeria.Transversal
{
    /// <summary>
    /// UIX-03: formato de fecha unificado y relativo en los 3 clientes.
    ///
    /// Réplica exacta de <c>FormatoFecha.java</c> y <c>transversal/fechas.py</c>.
    /// Antes la burbuja mostraba <c>yyyy-MM-dd HH:mm</c> siempre; ahora la hora
    /// corta es relativa ("10:00" hoy, "02-01 10:00" este año) y el separador de
    /// día (UIX-04) pone el contexto. Contrato compartido (VIS-02).
    ///
    /// Acepta ISO con T, con espacio (SQLite) y con zona horaria.
    /// Lo ilegible devuelve "" en vez de romper la fila.
    /// </summary>
    public static class FormatoFecha
    {
        /// <summary>Hora corta para el pie de la burbuja.</summary>
        public static string HoraCorta(string? crudo)
        {
            if (!TryLeer(crudo, out var fecha)) return "";
            var hoy = DateTime.Today;
            string hora = fecha.ToString("HH:mm");
            if (fecha.Date == hoy) return hora;
            if (fecha.Year == hoy.Year) return fecha.ToString("dd-MM") + " " + hora;
            return fecha.ToString("dd-MM-yyyy", CultureInfo.InvariantCulture) + " " + hora;
        }

        /// <summary>Etiqueta para el separador de día (UIX-04).</summary>
        public static string EtiquetaDia(string? crudo)
        {
            if (!TryLeer(crudo, out var fecha)) return "";
            var hoy = DateTime.Today;
            if (fecha.Date == hoy) return "Hoy";
            if (fecha.Date == hoy.AddDays(-1)) return "Ayer";
            if (fecha.Year == hoy.Year) return fecha.ToString("dd-MM");
            return fecha.ToString("dd-MM-yyyy", CultureInfo.InvariantCulture);
        }

        /// <summary>Clave de agrupación por día (UIX-04): "" si ilegible.</summary>
        public static string ClaveDia(string? crudo)
        {
            return TryLeer(crudo, out var fecha) ? fecha.ToString("yyyy-MM-dd") : "";
        }

        private static bool TryLeer(string? crudo, out DateTime fecha)
        {
            fecha = default;
            if (string.IsNullOrWhiteSpace(crudo)) return false;
            // DateTimeOffset acepta T, espacio, microsegundos y zona.
            if (DateTimeOffset.TryParse(crudo, out var instante))
            {
                fecha = instante.ToLocalTime().DateTime;
                return true;
            }
            return false;
        }
    }
}
