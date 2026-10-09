using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Threading.Tasks;
using Mensajeria.Componentes;
using Mensajeria.Transversal;

namespace Mensajeria.Fachada.Servicios
{
    /// <summary>Reenvía las pendientes locales en su orden de creación.</summary>
    public sealed class ReintentadorPendientesCliente
    {
        private readonly Func<IHistorialLocal?> _historialActual;
        private readonly CoordinadorMensajeriaCliente _coordinadorMensajeria;

        public ReintentadorPendientesCliente(Func<IHistorialLocal?> historialActual,
            CoordinadorMensajeriaCliente coordinadorMensajeria)
        {
            _historialActual = historialActual ?? throw new ArgumentNullException(nameof(historialActual));
            _coordinadorMensajeria = coordinadorMensajeria
                ?? throw new ArgumentNullException(nameof(coordinadorMensajeria));
        }

        public async Task<int> ReintentarPendientesAsync(IConexionRed? conexion,
            string? usuarioActual)
        {
            if (conexion == null || usuarioActual == null) return 0;
            // Resolver aquí permite que una misma fachada use el store que el
            // login activó para la cuenta actual, sin conservar el store anterior.
            var historial = _historialActual();
            if (historial == null) return 0;

            int enviados = 0;
            foreach (var pendiente in historial.ListarPendientes())
            {
                if ((string?)pendiente["tipo"] == TipoMensaje.MENSAJE_LEIDO)
                {
                    try
                    {
                        conexion.Enviar(new Dictionary<string, object?>
                        {
                            { "id", pendiente["id"] },
                            { "tipo", TipoMensaje.MENSAJE_LEIDO },
                            { "remitente", pendiente["origen"] },
                            { "destinatario", pendiente["destino"] },
                            { "contenido", pendiente["contenido"] },
                            { "fechaHora", pendiente["fechaCreado"] }
                        });
                        historial.EliminarPendiente((string)pendiente["id"]!);
                        enviados++;
                    }
                    catch (Exception error)
                    {
                        historial.IncrementarIntento((string)pendiente["id"]!, error.Message);
                        break;
                    }
                    continue;
                }

                var solicitud = new Dictionary<string, object?>
                {
                    { "id", pendiente["id"] },
                    { "tipo", pendiente["tipo"] },
                    { "remitente", pendiente["origen"] },
                    { "destinatario", pendiente["destino"] },
                    { "fechaHora", pendiente["fechaCreado"] }
                };
                if ((string?)pendiente["tipo"] == TipoMensaje.MENSAJE_ARCHIVO)
                {
                    try
                    {
                        var payload = (byte[]?)pendiente["payload"] ?? Array.Empty<byte>();
                        string nombre = (string?)pendiente["nombreArchivo"] ?? "archivo.bin";
                        await _coordinadorMensajeria.EnviarFragmentosAsync(conexion,
                            (string)pendiente["origen"]!, (string)pendiente["destino"]!,
                            nombre, CoordinadorMensajeriaCliente.MimePorExtension(nombre),
                            payload, (string)pendiente["id"]!,
                            (string?)pendiente["fechaCreado"] ?? DateTime.UtcNow.ToString("O"),
                            null, default).ConfigureAwait(false);
                        historial.MarcarEstado((string)pendiente["id"]!, "ENVIADO");
                        historial.EliminarPendiente((string)pendiente["id"]!);
                        enviados++;
                    }
                    catch (Exception error)
                    {
                        historial.IncrementarIntento((string)pendiente["id"]!, error.Message);
                        break;
                    }
                    continue;
                }

                if ((string?)pendiente["tipo"] == TipoMensaje.MENSAJE_IMAGEN)
                {
                    var payload = (byte[]?)pendiente["payload"] ?? Array.Empty<byte>();
                    solicitud["contenidoImagen"] = Convert.ToBase64String(payload);
                    solicitud["nombreArchivo"] = pendiente["nombreArchivo"];
                }
                else
                {
                    solicitud["contenido"] = pendiente["contenido"];
                }

                try
                {
                    var respuesta = await conexion.Pedir(solicitud).ConfigureAwait(false);
                    LanzarSiError(respuesta, "reintento rechazado");
                    if ((string?)pendiente["tipo"] == TipoMensaje.MENSAJE_IMAGEN
                        && GetString(respuesta, "archivoId") is string archivoId)
                        historial.ActualizarArchivoId((string)pendiente["id"]!, archivoId);
                    historial.MarcarEstado((string)pendiente["id"]!, "ENVIADO");
                    historial.EliminarPendiente((string)pendiente["id"]!);
                    enviados++;
                }
                catch (Exception error)
                {
                    historial.IncrementarIntento((string)pendiente["id"]!, error.Message);
                    break;
                }
            }
            return enviados;
        }

        private static void LanzarSiError(Dictionary<string, JsonElement> respuesta,
            string defecto, params string[] tiposAceptados)
        {
            respuesta.TryGetValue("tipo", out var elementoTipo);
            string tipo = elementoTipo.ValueKind == JsonValueKind.String
                ? elementoTipo.GetString()! : "";
            bool tipoAceptado = tiposAceptados.Length == 0
                || Array.IndexOf(tiposAceptados, tipo) >= 0;
            bool exito = !respuesta.TryGetValue("exito", out var elementoExito)
                || elementoExito.ValueKind != JsonValueKind.False;
            if (tipoAceptado && exito) return;
            string motivo = GetString(respuesta, "mensajeError") ?? defecto;
            throw new InvalidOperationException(motivo);
        }

        private static string? GetString(Dictionary<string, JsonElement> mensaje, string clave) =>
            mensaje.TryGetValue(clave, out var valor) && valor.ValueKind == JsonValueKind.String
                ? valor.GetString() : null;
    }
}
