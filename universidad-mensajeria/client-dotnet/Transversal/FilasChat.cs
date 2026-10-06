using System;

namespace Mensajeria.Transversal
{
    /// <summary>
    /// UIX-04/UIX-05: reglas de filas del historial.
    ///
    /// Réplica exacta de <c>FilasChat</c> (Java) y <c>agrupar</c> (Python):
    /// separador cada vez que cambia el día, y racha sin remitente repetido
    /// con mismo autor, mismo día y menos de 5 minutos. Contrato compartido.
    /// </summary>
    public static class FilasChat
    {
        private const double VentanaRachaMinutos = 5;

        /// <summary>
        /// ¿El mensaje actual va en la racha del anterior (sin encabezado)?
        /// Con fecha ilegible no se agrupa: mostrar el remitente es lo seguro.
        /// </summary>
        public static bool Agrupar(string? fechaAnterior, string? autorAnterior,
            string? fechaActual, string? autorActual)
        {
            if (string.IsNullOrEmpty(autorAnterior) || autorAnterior != autorActual)
                return false;
            if (!TryLeer(fechaAnterior, out var a) || !TryLeer(fechaActual, out var b))
                return false;
            if (a.Date != b.Date)
                return false;
            return Math.Abs((b - a).TotalMinutes) < VentanaRachaMinutos;
        }

        /// <summary>¿Hay que pintar separador entre estos dos días (ambos conocidos)?</summary>
        public static bool CambiaElDia(string? fechaAnterior, string? fechaActual)
        {
            string a = FormatoFecha.ClaveDia(fechaAnterior);
            string b = FormatoFecha.ClaveDia(fechaActual);
            return a.Length > 0 && b.Length > 0 && a != b;
        }

        private static bool TryLeer(string? crudo, out DateTime fecha)
        {
            fecha = default;
            if (string.IsNullOrWhiteSpace(crudo)) return false;
            if (DateTimeOffset.TryParse(crudo, out var instante))
            {
                fecha = instante.ToLocalTime().DateTime;
                return true;
            }
            return false;
        }
    }
}
