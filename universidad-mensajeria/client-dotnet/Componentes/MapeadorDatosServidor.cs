using System;
using System.Collections.Generic;
using System.Text.Json;
using Mensajeria.Transversal;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Componentes
{
    /// <summary>Adaptador JSON para los campos de directorio e historial del servidor.</summary>
    public sealed class MapeadorDatosServidor : IMapeadorDatos
    {
        public List<UsuarioLocal> MapearDirectorio(string json)
        {
            using var documento = JsonDocument.Parse(json);
            var usuarios = new List<UsuarioLocal>();
            if (documento.RootElement.ValueKind != JsonValueKind.Array) return usuarios;

            foreach (var fila in documento.RootElement.EnumerateArray())
            {
                if (fila.ValueKind != JsonValueKind.Object) continue;
                string? codigo = Texto(fila, "codigo");
                if (string.IsNullOrWhiteSpace(codigo)) continue;
                usuarios.Add(new UsuarioLocal(
                    codigo,
                    Texto(fila, "nombres"),
                    Texto(fila, "apellidos"),
                    Texto(fila, "programa"),
                    Booleano(fila, "conectado"),
                    Texto(fila, "fechaRegistro"),
                    Texto(fila, "actualizado")));
            }
            return usuarios;
        }

        public List<MensajeLocal> MapearPaginaHistorial(string json, string codigoSesion)
        {
            if (string.IsNullOrWhiteSpace(codigoSesion))
                throw new ArgumentException("la sesión necesita un código", nameof(codigoSesion));
            using var documento = JsonDocument.Parse(json);
            var mensajes = new List<MensajeLocal>();
            if (documento.RootElement.ValueKind != JsonValueKind.Array) return mensajes;

            foreach (var fila in documento.RootElement.EnumerateArray())
            {
                if (fila.ValueKind != JsonValueKind.Object) continue;
                string? id = Texto(fila, "id");
                string? origen = Texto(fila, "remitente");
                string? destino = Texto(fila, "destinatario");
                string? tipoCrudo = Texto(fila, "tipo");
                if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(origen)
                    || string.IsNullOrWhiteSpace(destino) || string.IsNullOrWhiteSpace(tipoCrudo))
                    continue;

                string tipo = NormalizarTipoMensaje(tipoCrudo);
                string? nombre = Texto(fila, "nombreArchivo");
                bool propio = string.Equals(origen, codigoSesion, StringComparison.Ordinal);
                string? contenido = tipo switch
                {
                    TipoMensaje.MENSAJE_IMAGEN => "[Imagen]",
                    TipoMensaje.MENSAJE_ARCHIVO => $"[Archivo: {nombre ?? "archivo"}]",
                    _ => Texto(fila, "contenido")
                };
                mensajes.Add(new MensajeLocal(
                    id, origen, destino, tipo, contenido,
                    Texto(fila, "hashSha256"), Entero(fila, "numCaracteres"),
                    Entero(fila, "numPalabras"), nombre, null,
                    EnteroLargo(fila, "tamanoArchivo"),
                    Texto(fila, "fechaEnvio") ?? DateTime.UtcNow.ToString("O"),
                    propio, false, propio ? "ENVIADO" : "ENTREGADO",
                    Texto(fila, "archivoId"), Texto(fila, "etapasFiltrado")));
            }
            return mensajes;
        }

        public string NormalizarTipoMensaje(string tipo)
        {
            if (Enum.TryParse(tipo, ignoreCase: false, out TipoMensajeCliente tipoEnum)
                && string.Equals(Enum.GetName(tipoEnum), tipo, StringComparison.Ordinal))
                return tipo;
            throw new ArgumentException($"tipo de mensaje no reconocido: {tipo}", nameof(tipo));
        }

        private static string? Texto(JsonElement fila, string nombre) =>
            fila.TryGetProperty(nombre, out var valor) && valor.ValueKind == JsonValueKind.String
                ? valor.GetString() : null;

        private static bool Booleano(JsonElement fila, string nombre) =>
            fila.TryGetProperty(nombre, out var valor) && valor.ValueKind == JsonValueKind.True;

        private static int? Entero(JsonElement fila, string nombre) =>
            fila.TryGetProperty(nombre, out var valor) && valor.TryGetInt32(out int resultado)
                ? resultado : null;

        private static long? EnteroLargo(JsonElement fila, string nombre) =>
            fila.TryGetProperty(nombre, out var valor) && valor.TryGetInt64(out long resultado)
                ? resultado : null;
    }
}
