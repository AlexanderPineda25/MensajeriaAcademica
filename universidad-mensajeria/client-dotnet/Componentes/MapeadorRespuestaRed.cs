using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Componentes
{
    /// <summary>Convierte la trama del borde TCP en el record neutral del cliente.</summary>
    public static class MapeadorRespuestaRed
    {
        public static RespuestaRed Mapear(IReadOnlyDictionary<string, JsonElement> crudo)
        {
            if (!crudo.TryGetValue("tipo", out var tipoElemento)
                || tipoElemento.ValueKind != JsonValueKind.String
                || !Enum.TryParse(tipoElemento.GetString(), ignoreCase: false,
                    out TipoMensajeCliente tipo)
                || !string.Equals(Enum.GetName(tipo), tipoElemento.GetString(), StringComparison.Ordinal))
                throw new ArgumentException("la respuesta de red tiene un tipo desconocido", nameof(crudo));

            return new RespuestaRed(
                tipo,
                Texto(crudo, "id"),
                Texto(crudo, "fechaHora"),
                Texto(crudo, "codigo"),
                Texto(crudo, "nombres"),
                Texto(crudo, "apellidos"),
                Texto(crudo, "programa"),
                Texto(crudo, "remitente"),
                Texto(crudo, "destinatario"),
                Texto(crudo, "contenido"),
                Texto(crudo, "hashSha256"),
                Entero(crudo, "numCaracteres"),
                Entero(crudo, "numPalabras"),
                Texto(crudo, "etapasFiltrado"),
                Texto(crudo, "nombreArchivo"),
                EnteroLargo(crudo, "tamanoArchivo"),
                Texto(crudo, "archivoId"),
                Texto(crudo, "mime"),
                Texto(crudo, "contenidoImagen"),
                Entero(crudo, "pagina"),
                Entero(crudo, "totalPaginas"),
                Booleano(crudo, "exito"),
                Texto(crudo, "mensajeError"),
                ListaTexto(crudo, "usuariosConectados"),
                Texto(crudo, "tipoOriginal"));
        }

        /// <summary>
        /// Adaptador temporal para conservar IFachadaCliente mientras la UI migra
        /// de Dictionary&lt;string, JsonElement&gt; a RespuestaRed.
        /// </summary>
        public static Dictionary<string, JsonElement> ACrudo(RespuestaRed respuesta)
        {
            var valores = new Dictionary<string, object?>
            {
                ["tipo"] = respuesta.Tipo.ToString(),
                ["id"] = respuesta.Id,
                ["fechaHora"] = respuesta.FechaHora,
                ["codigo"] = respuesta.Codigo,
                ["nombres"] = respuesta.Nombres,
                ["apellidos"] = respuesta.Apellidos,
                ["programa"] = respuesta.Programa,
                ["remitente"] = respuesta.Remitente,
                ["destinatario"] = respuesta.Destinatario,
                ["contenido"] = respuesta.Contenido,
                ["hashSha256"] = respuesta.HashSha256,
                ["numCaracteres"] = respuesta.NumCaracteres,
                ["numPalabras"] = respuesta.NumPalabras,
                ["etapasFiltrado"] = respuesta.EtapasFiltrado,
                ["nombreArchivo"] = respuesta.NombreArchivo,
                ["tamanoArchivo"] = respuesta.TamanoArchivo,
                ["archivoId"] = respuesta.ArchivoId,
                ["mime"] = respuesta.Mime,
                ["contenidoImagen"] = respuesta.ContenidoImagen,
                ["pagina"] = respuesta.Pagina,
                ["totalPaginas"] = respuesta.TotalPaginas,
                ["exito"] = respuesta.Exito,
                ["mensajeError"] = respuesta.MensajeError,
                ["usuariosConectados"] = respuesta.UsuariosConectados,
                ["tipoOriginal"] = respuesta.TipoOriginal
            };
            return valores.Where(par => par.Value != null).ToDictionary(
                par => par.Key,
                par => JsonSerializer.SerializeToElement(par.Value));
        }

        private static string? Texto(IReadOnlyDictionary<string, JsonElement> crudo, string clave) =>
            crudo.TryGetValue(clave, out var valor) && valor.ValueKind == JsonValueKind.String
                ? valor.GetString() : null;

        private static int? Entero(IReadOnlyDictionary<string, JsonElement> crudo, string clave) =>
            crudo.TryGetValue(clave, out var valor) && valor.TryGetInt32(out int resultado)
                ? resultado : null;

        private static long? EnteroLargo(IReadOnlyDictionary<string, JsonElement> crudo, string clave) =>
            crudo.TryGetValue(clave, out var valor) && valor.TryGetInt64(out long resultado)
                ? resultado : null;

        private static bool? Booleano(IReadOnlyDictionary<string, JsonElement> crudo, string clave) =>
            crudo.TryGetValue(clave, out var valor) && valor.ValueKind is JsonValueKind.True or JsonValueKind.False
                ? valor.GetBoolean() : null;

        private static List<string>? ListaTexto(
            IReadOnlyDictionary<string, JsonElement> crudo, string clave)
        {
            if (!crudo.TryGetValue(clave, out var valor) || valor.ValueKind != JsonValueKind.Array)
                return null;
            return valor.EnumerateArray().Where(item => item.ValueKind == JsonValueKind.String)
                .Select(item => item.GetString()!).ToList();
        }
    }
}
