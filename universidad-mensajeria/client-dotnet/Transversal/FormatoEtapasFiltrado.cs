using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Mensajeria.Transversal
{
    /// <summary>Presentación en orden de etapas de imagen, sin exponer rutas.</summary>
    public static class FormatoEtapasFiltrado
    {
        public static string Resumir(string? etapasJson)
        {
            if (string.IsNullOrWhiteSpace(etapasJson)) return "";

            try
            {
                using var documento = JsonDocument.Parse(etapasJson);
                if (documento.RootElement.ValueKind != JsonValueKind.Array) return "";

                var filas = new List<string>();
                foreach (var elemento in documento.RootElement.EnumerateArray())
                {
                    if (elemento.ValueKind != JsonValueKind.Object
                        || !elemento.TryGetProperty("etapa", out var etapaElemento)
                        || etapaElemento.ValueKind != JsonValueKind.String)
                        continue;

                    string? nombre = NombreLegible(etapaElemento.GetString());
                    if (nombre == null) continue;

                    string duracion = "";
                    if (elemento.TryGetProperty("ms", out var msElemento)
                        && msElemento.ValueKind == JsonValueKind.Number
                        && msElemento.TryGetDouble(out double ms)
                        && double.IsFinite(ms) && ms >= 0)
                    {
                        duracion = " (" + ms.ToString("0.################",
                            CultureInfo.InvariantCulture) + " ms)";
                    }
                    filas.Add(nombre + duracion);
                }
                return filas.Count == 0 ? "" : "Filtros: " + string.Join(" → ", filas);
            }
            catch (JsonException)
            {
                return "";
            }
        }

        private static string? NombreLegible(string? etapa)
        {
            if (string.IsNullOrWhiteSpace(etapa)) return null;
            string nombre = etapa.Trim().Replace('\\', '/');
            int separador = nombre.LastIndexOf('/');
            if (separador >= 0) nombre = nombre[(separador + 1)..];
            int extension = nombre.LastIndexOf('.');
            if (extension > 0) nombre = nombre[..extension];
            nombre = Regex.Replace(nombre, @"^\d+[_\-\s.]*", "");

            string clave = Regex.Replace(nombre.ToLowerInvariant(), @"[^a-z0-9áéíóúñ]", "");
            if (clave is "sha256" or "sha256imagen") return "Verificación SHA-256";
            if (clave is "grises" or "escalagris" or "escalagrises" or "escaladegrises")
                return "Grises";
            if (clave == "sepia") return "Sepia";
            if (clave is "reduccion" or "reducción") return "Reducción";
            if (clave == "brillo") return "Brillo";
            Match giro = Regex.Match(clave, @"^giro(\d+)$");
            if (giro.Success) return "Giro " + giro.Groups[1].Value + "°";
            if (clave == "giro") return "Giro";

            nombre = Regex.Replace(nombre, @"[_\-]+", " ");
            nombre = Regex.Replace(nombre, @"(?<=[a-záéíóúñ])(?=[A-Z])", " ");
            nombre = Regex.Replace(nombre, @"\s+", " ").Trim();
            if (nombre.Length == 0) return null;
            return char.ToUpper(nombre[0], CultureInfo.CurrentCulture) + nombre[1..];
        }
    }
}
