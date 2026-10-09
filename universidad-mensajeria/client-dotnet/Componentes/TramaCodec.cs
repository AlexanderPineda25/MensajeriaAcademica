using System;
using System.Text.Json;
using System.Collections.Generic;
using System.IO;
using System.Text.Json.Serialization;

namespace Mensajeria.Componentes
{
    /// <summary>
    /// Framing del protocolo: <c>[4 bytes longitud big-endian][payload JSON UTF-8]</c>.
    /// Contrato: <c>contrato/framing.json</c> (Fase 1.6).
    /// </summary>
    public static class TramaCodec
    {
        /// <summary>16 MiB. Los archivos no caben: viajan fragmentados (AGENTS.md invariante 2).</summary>
        public const int TamanoMaximoTrama = 16 * 1024 * 1024;

        private const int LongitudDeCabecera = 4;

        private static readonly JsonSerializerOptions OpcionesDeEscritura = new()
        {
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
            // POR QUÉ esto y no el valor por defecto: System.Text.Json escapa TODO lo
            // no ASCII ("ñ" -> "ñ", 6 bytes) y Gson (Java) y json.dumps con
            // ensure_ascii=False (Python) escriben UTF-8 crudo. Sin esta línea el
            // cliente .NET no produce los bytes que dice el contrato y cada acento
            // engorda la trama. No hay riesgo de inyección: esto es un socket, no HTML.
            Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
        };

        public static void Escribir(System.IO.Stream stream, Dictionary<string, object?> mensaje)
        {
            byte[] payload = JsonSerializer.SerializeToUtf8Bytes(mensaje, OpcionesDeEscritura);
            // La longitud son BYTES del payload ya serializado, nunca caracteres.
            byte[] header = BitConverter.GetBytes(payload.Length);
            if (BitConverter.IsLittleEndian) Array.Reverse(header);
            stream.Write(header, 0, LongitudDeCabecera);
            stream.Write(payload, 0, payload.Length);
            stream.Flush();
        }

        /// <summary>
        /// Lee una trama: 4 bytes de longitud big-endian + payload JSON en UTF-8.
        /// Contrato: <c>contrato/framing.json</c> (Fase 1.6). La longitud son BYTES,
        /// nunca caracteres: por eso `BitConverter.GetBytes` + `Array.Reverse` y no
        /// una escritura directa de entero.
        /// </summary>
        public static Dictionary<string, JsonElement> Leer(System.IO.Stream stream)
        {
            byte[] header = LeerExacto(stream, LongitudDeCabecera);
            if (BitConverter.IsLittleEndian) Array.Reverse(header);
            int length = BitConverter.ToInt32(header, 0);
            if (length <= 0 || length > TamanoMaximoTrama)
                throw new InvalidOperationException($"Longitud invalida: {length}");
            byte[] payload = LeerExacto(stream, length);
            var mensaje = JsonSerializer.Deserialize<Dictionary<string, JsonElement>>(payload);
            // `tipo` es obligatorio en el contrato: sin ella la trama no es un mensaje
            // y dejarla pasar haria que el demultiplexor la tratara como respuesta huerfana.
            if (mensaje == null || !mensaje.TryGetValue("tipo", out var tipo)
                || tipo.ValueKind != JsonValueKind.String || string.IsNullOrEmpty(tipo.GetString()))
                throw new InvalidOperationException("Trama sin la clave obligatoria 'tipo'");
            return mensaje;
        }

        private static byte[] LeerExacto(System.IO.Stream stream, int n)
        {
            byte[] buf = new byte[n];
            int offset = 0;
            while (offset < n)
            {
                int read = stream.Read(buf, offset, n - offset);
                if (read == 0) throw new IOException("Conexion cerrada");
                offset += read;
            }
            return buf;
        }
    }
}
