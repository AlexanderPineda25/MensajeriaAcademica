using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Mensajeria.Componentes;
using Mensajeria.Fachada;
using Mensajeria.Transversal;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Fachada.Servicios
{
    /// <summary>
    /// Coordina el envío 1-a-1: persiste primero, intenta la red y deja una
    /// pendiente solo ante fallos de transporte.
    /// </summary>
    public sealed class CoordinadorMensajeriaCliente
    {
        public const int ParteArchivoBytes = 1024 * 1024;

        private readonly Func<IHistorialLocal?> _historialActual;

        public CoordinadorMensajeriaCliente(Func<IHistorialLocal?> historialActual)
        {
            _historialActual = historialActual ?? throw new ArgumentNullException(nameof(historialActual));
        }

        public async Task<bool> EnviarTextoAsync(IConexionRed conexion, string remitente,
            string destinatario, string contenido)
        {
            var historial = _historialActual();
            var id = Guid.NewGuid().ToString();
            var fecha = DateTime.UtcNow.ToString("O");
            historial?.UpsertMensaje(id, remitente, destinatario,
                TipoMensaje.MENSAJE_TEXTO, contenido, null, null, null,
                null, null, null, fecha, 1, 1, "PENDIENTE");
            var mensaje = new Dictionary<string, object?>
            {
                { "id", id },
                { "tipo", TipoMensaje.MENSAJE_TEXTO },
                { "remitente", remitente },
                { "destinatario", destinatario },
                { "contenido", contenido },
                { "fechaHora", fecha }
            };
            try
            {
                var respuesta = await conexion.Pedir(mensaje).ConfigureAwait(false);
                var aceptada = ExigirAceptado(respuesta, "envio rechazado", TipoMensaje.ACK);
                historial?.UpsertMensaje(id, remitente, destinatario,
                    TipoMensaje.MENSAJE_TEXTO, contenido,
                    aceptada.HashSha256, aceptada.NumCaracteres, aceptada.NumPalabras,
                    null, null, null, fecha, 1, 1, "ENVIADO");
                return true;
            }
            catch (Exception error) when (EsFalloTransporte(error))
            {
                historial?.RegistrarPendiente(id, TipoMensaje.MENSAJE_TEXTO, remitente,
                    destinatario, contenido, null, null, fecha);
                return false;
            }
        }

        public async Task<bool> EnviarImagenAsync(IConexionRed conexion, string remitente,
            string destinatario, string imagePath)
        {
            byte[] bytes = await File.ReadAllBytesAsync(imagePath).ConfigureAwait(false);
            if (bytes.Length == 0) throw new ArgumentException("la imagen esta vacia");
            var historial = _historialActual();
            var id = Guid.NewGuid().ToString();
            var fecha = DateTime.UtcNow.ToString("O");
            var nombre = Path.GetFileName(imagePath);
            historial?.UpsertMensaje(id, remitente, destinatario,
                TipoMensaje.MENSAJE_IMAGEN, "[Imagen]", null, null, null,
                nombre, imagePath, bytes.Length, fecha, 1, 1, "PENDIENTE");
            var mensaje = new Dictionary<string, object?>
            {
                { "id", id },
                { "tipo", TipoMensaje.MENSAJE_IMAGEN },
                { "remitente", remitente },
                { "destinatario", destinatario },
                { "contenidoImagen", Convert.ToBase64String(bytes) },
                { "nombreArchivo", nombre },
                { "tamanoArchivo", bytes.Length },
                { "fechaHora", fecha }
            };
            try
            {
                var respuesta = await conexion.Pedir(mensaje).ConfigureAwait(false);
                var aceptada = ExigirAceptado(respuesta, "envio rechazado",
                    TipoMensaje.IMAGE_FILTERED_RESULT);
                historial?.UpsertMensaje(id, remitente, destinatario,
                    TipoMensaje.MENSAJE_IMAGEN, "[Imagen]", aceptada.HashSha256,
                    null, null, nombre, imagePath, bytes.Length, fecha, 1, 1,
                    "ENVIADO", aceptada.ArchivoId, aceptada.EtapasFiltrado);
                return true;
            }
            catch (Exception error) when (EsFalloTransporte(error))
            {
                historial?.RegistrarPendiente(id, TipoMensaje.MENSAJE_IMAGEN, remitente,
                    destinatario, null, nombre, bytes, fecha);
                return false;
            }
        }

        public async Task<bool> EnviarArchivoAsync(IConexionRed conexion, string remitente,
            string destinatario, string rutaArchivo, IProgress<double>? progreso = null,
            CancellationToken cancelacion = default)
        {
            if (string.IsNullOrEmpty(rutaArchivo) || !File.Exists(rutaArchivo))
                throw new ArgumentException("archivo no encontrado");
            byte[] bytes = await File.ReadAllBytesAsync(rutaArchivo, cancelacion).ConfigureAwait(false);
            if (bytes.Length == 0) throw new ArgumentException("el archivo esta vacio");
            if (bytes.Length > IFachadaCliente.MaxBytesArchivo) throw new ArgumentException(
                $"el archivo supera el maximo de {IFachadaCliente.MaxBytesArchivo} bytes");
            var historial = _historialActual();
            var id = Guid.NewGuid().ToString();
            var fecha = DateTime.UtcNow.ToString("O");
            var nombre = Path.GetFileName(rutaArchivo);
            historial?.UpsertMensaje(id, remitente, destinatario,
                TipoMensaje.MENSAJE_ARCHIVO, $"[Archivo: {nombre}]", null, null, null,
                nombre, rutaArchivo, bytes.Length, fecha, 1, 1, "PENDIENTE");
            try
            {
                await EnviarFragmentosAsync(conexion, remitente, destinatario, nombre,
                    MimePorExtension(rutaArchivo), bytes, id, fecha, progreso, cancelacion)
                    .ConfigureAwait(false);
                historial?.UpsertMensaje(id, remitente, destinatario,
                    TipoMensaje.MENSAJE_ARCHIVO, $"[Archivo: {nombre}]", null, null, null,
                    nombre, rutaArchivo, bytes.Length, fecha, 1, 1, "ENVIADO");
                return true;
            }
            catch (Exception error) when (EsFalloTransporte(error))
            {
                historial?.RegistrarPendiente(id, TipoMensaje.MENSAJE_ARCHIVO, remitente,
                    destinatario, null, nombre, bytes, fecha);
                return false;
            }
        }

        /// <summary>Reutiliza el mismo protocolo para reenviar archivos pendientes.</summary>
        internal Task EnviarFragmentosAsync(IConexionRed conexion, string remitente,
            string destinatario, string nombre, string mime, byte[] bytes, string id,
            string fecha, IProgress<double>? progreso, CancellationToken cancelacion) =>
            EnviarFragmentosPrivadoAsync(conexion, remitente, destinatario, nombre,
                mime, bytes, id, fecha, progreso, cancelacion);

        internal static string MimePorExtension(string ruta) =>
            Path.GetExtension(ruta).ToLowerInvariant() switch
            {
                ".pdf" => "application/pdf",
                ".png" => "image/png",
                ".jpg" or ".jpeg" => "image/jpeg",
                ".gif" => "image/gif",
                ".bmp" => "image/bmp",
                ".txt" => "text/plain",
                _ => "application/octet-stream"
            };

        private static async Task EnviarFragmentosPrivadoAsync(IConexionRed conexion,
            string remitente, string destinatario, string nombre, string mime,
            byte[] bytes, string id, string fecha, IProgress<double>? progreso,
            CancellationToken cancelacion)
        {
            int totalPartes = (bytes.Length + ParteArchivoBytes - 1) / ParteArchivoBytes;
            var inicio = await conexion.Pedir(new Dictionary<string, object?>
            {
                { "id", id },
                { "tipo", TipoMensaje.ARCHIVO_INICIO },
                { "remitente", remitente },
                { "destinatario", destinatario },
                { "nombreArchivo", nombre },
                { "mime", mime },
                { "tamanoArchivo", (long)bytes.Length },
                { "totalPartes", totalPartes },
                { "fechaHora", fecha }
            }).ConfigureAwait(false);
            ExigirAceptado(inicio, "envio rechazado", TipoMensaje.ACK);
            for (int i = 0; i < totalPartes; i++)
            {
                cancelacion.ThrowIfCancellationRequested();
                int desde = i * ParteArchivoBytes;
                int hasta = Math.Min(desde + ParteArchivoBytes, bytes.Length);
                string base64 = Convert.ToBase64String(bytes, desde, hasta - desde);
                var parte = await conexion.Pedir(new Dictionary<string, object?>
                {
                    { "id", id },
                    { "tipo", TipoMensaje.ARCHIVO_PARTE },
                    { "remitente", remitente },
                    { "archivoId", id },
                    { "indiceParte", i },
                    { "contenidoImagen", base64 },
                    { "fechaHora", fecha }
                }).ConfigureAwait(false);
                ExigirAceptado(parte, "envio rechazado", TipoMensaje.ACK);
                progreso?.Report((double)(i + 1) / totalPartes);
            }

            string hash = Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
            var fin = await conexion.Pedir(new Dictionary<string, object?>
            {
                { "id", id },
                { "tipo", TipoMensaje.ARCHIVO_FIN },
                { "remitente", remitente },
                { "archivoId", id },
                { "hashSha256", hash },
                { "fechaHora", fecha }
            }).ConfigureAwait(false);
            ExigirAceptado(fin, "envio rechazado", TipoMensaje.ACK);
        }

        private static RespuestaRed ExigirAceptado(
            Dictionary<string, JsonElement> respuesta, string defecto, string tipoEsperado)
        {
            respuesta.TryGetValue("tipo", out var tipoElemento);
            string tipo = tipoElemento.ValueKind == JsonValueKind.String
                ? tipoElemento.GetString()! : "";
            bool exito = !respuesta.TryGetValue("exito", out var exitoElemento)
                || exitoElemento.ValueKind != JsonValueKind.False;
            if (tipo != tipoEsperado || !exito)
            {
                string motivo = respuesta.TryGetValue("mensajeError", out var errorElemento)
                    && errorElemento.ValueKind == JsonValueKind.String
                    ? errorElemento.GetString()! : defecto;
                throw new InvalidOperationException(motivo);
            }
            return MapeadorRespuestaRed.Mapear(respuesta);
        }

        private static bool EsFalloTransporte(Exception error) =>
            error is TimeoutException || error is SocketException || error is IOException;
    }
}
