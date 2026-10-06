using System;
using System.Collections.Generic;

namespace Mensajeria.Transversal
{
    /// <summary>
    /// UIX-06: parte el texto por la consulta para resaltarla.
    ///
    /// Réplica de <c>textoResaltado</c> (Java) y <c>resaltar_html</c> (Python):
    /// insensible a mayúsculas, sin regex (el patrón puede traer `.*+?`
    /// literales). Cada tramo dice si es coincidencia; la vista decide cómo
    /// pintarlo (TextFlow / span / Run).
    /// </summary>
    public static class Resaltado
    {
        public readonly record struct Tramo(string Texto, bool EsMatch);

        public static List<Tramo> Dividir(string? texto, string? patron)
        {
            var tramos = new List<Tramo>();
            texto ??= "";
            patron = (patron ?? "").Trim();
            if (patron.Length == 0)
            {
                if (texto.Length > 0) tramos.Add(new Tramo(texto, false));
                return tramos;
            }
            int pos = 0;
            while (true)
            {
                int i = texto.IndexOf(patron, pos, StringComparison.CurrentCultureIgnoreCase);
                if (i < 0) break;
                if (i > pos) tramos.Add(new Tramo(texto.Substring(pos, i - pos), false));
                tramos.Add(new Tramo(texto.Substring(i, patron.Length), true));
                pos = i + patron.Length;
            }
            if (pos < texto.Length) tramos.Add(new Tramo(texto.Substring(pos), false));
            return tramos;
        }
    }
}
