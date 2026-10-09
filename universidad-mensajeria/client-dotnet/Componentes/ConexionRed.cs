using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Net.Sockets;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Componentes
{
    /// <summary>
    /// Red TCP del cliente .NET (espejo de ConexionCliente Java, Fase 5).
    /// Demultiplexor por id + escritura sincronizada; CLOSE_NOTICE SIEMPRE va
    /// a OnCierre (nunca completa un pendiente: carrera del KICK).
    /// </summary>
    public class ConexionRed : IConexionRed
    {
        private TcpClient? _cliente;
        private System.IO.Stream? _stream;
        /// <summary>FASE 11.2: TLS opcional (mismo framing sobre SslStream).</summary>
        public bool TlsHabilitado { get; set; }
        public int TlsPuerto { get; set; } = Transversal.ClienteConfig.TlsPuertoPorDefecto;
        /// <summary>SEG-01: PEM con la CA propia. Vacío = validación de cadena del sistema.</summary>
        public string TlsConfianza { get; set; } = "";
        /// <summary>
        /// Callback de validación del certificado. Por defecto (null) se usa la
        /// validación de cadena del sistema; nunca se cablea uno que acepte todo,
        /// porque eso desactivaría TLS (ver <see cref="ValidarTls"/>).
        /// </summary>
        public System.Net.Security.RemoteCertificateValidationCallback? ValidarCertificado { get; set; }
        private readonly ConcurrentDictionary<string, TaskCompletionSource<Dictionary<string, JsonElement>>> _pendientes = new();
        private readonly ConcurrentDictionary<IReceptorMensajes, byte> _receptores = new();
        private readonly SemaphoreSlim _envioLock = new(1, 1);
        private CancellationTokenSource? _cts;

        public event Action<Dictionary<string, JsonElement>>? OnMensajeRecibido;
        public event Action<Dictionary<string, JsonElement>>? OnCierre;
        public event Action? OnDesconectado;

        public void Suscribir(IReceptorMensajes receptor)
        {
            ArgumentNullException.ThrowIfNull(receptor);
            _receptores.TryAdd(receptor, 0);
        }

        public async Task ConectarAsync(string host, int puerto)
        {
            // SEG-01: los tres rechazos ANTES de abrir el socket (espejo de
            // validar_tls de Python). Un client.properties roto se ve al arrancar.
            ValidarTls.Validar(TlsHabilitado, puerto, TlsPuerto, TlsConfianza);

            _cliente = new TcpClient();
            if (!TlsHabilitado)
            {
                await _cliente.ConnectAsync(host, puerto);
                _stream = _cliente.GetStream();
            }
            else
            {
                int tlsPuerto = TlsPuerto > 0 ? TlsPuerto : puerto;
                await _cliente.ConnectAsync(host, tlsPuerto);
                var ssl = new System.Net.Security.SslStream(
                    _cliente.GetStream(), false,
                    (remitente, certificado, cadena, errores) =>
                        ValidarCertificado?.Invoke(remitente, certificado, cadena, errores)
                        ?? errores == System.Net.Security.SslPolicyErrors.None);
                await ssl.AuthenticateAsClientAsync(host);
                _stream = ssl;
            }
            _cts = new CancellationTokenSource();

            _ = Task.Run(() => EscucharAsync(_cts.Token));
        }

        public void Desconectar()
        {
            _cts?.Cancel();
            _stream?.Close();
            _cliente?.Close();
            foreach (var kv in _pendientes)
                kv.Value.TrySetCanceled();
            _pendientes.Clear();
        }

        public void Enviar(Dictionary<string, object?> msg)
        {
            if (_stream == null) throw new InvalidOperationException("No conectado");
            if (!msg.ContainsKey("id")) msg["id"] = Guid.NewGuid().ToString();
            _envioLock.Wait();
            try { TramaCodec.Escribir(_stream, msg); }
            finally { _envioLock.Release(); }
        }

        public Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg) =>
            Pedir(msg, TimeSpan.FromSeconds(15));

        public async Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg,
            TimeSpan timeout)
        {
            if (_stream == null) throw new InvalidOperationException("No conectado");

            string id = msg.ContainsKey("id") ? msg["id"]?.ToString()! : Guid.NewGuid().ToString();
            msg["id"] = id;

            var tcs = new TaskCompletionSource<Dictionary<string, JsonElement>>();
            _pendientes[id] = tcs;

            _envioLock.Wait();
            try { TramaCodec.Escribir(_stream, msg); }
            catch { _pendientes.TryRemove(id, out _); throw; }
            finally { _envioLock.Release(); }

            using var cts = new CancellationTokenSource(timeout);
            cts.Token.Register(() =>
            {
                if (_pendientes.TryRemove(id, out var pendingTcs))
                {
                    pendingTcs.TrySetException(new TimeoutException("sin respuesta del servidor"));
                }
            });

            return await tcs.Task;
        }

        private async Task EscucharAsync(CancellationToken token)
        {
            try
            {
                while (!token.IsCancellationRequested && _stream != null)
                {
                    var msg = await Task.Run(() => TramaCodec.Leer(_stream), token);

                    // CLOSE_NOTICE nunca completa un pendiente (carrera del KICK:
                    // el aviso viaja con el id de la solicitud y el ACK llega despues).
                    if (msg.TryGetValue("tipo", out var tipo)
                        && tipo.ValueKind == JsonValueKind.String
                        && tipo.GetString() == Transversal.TipoMensaje.CLOSE_NOTICE)
                    {
                        NotificarCrudo(OnCierre, msg);
                        NotificarReceptores(msg, cierre: true);
                        continue;
                    }

                    if (msg.TryGetValue("id", out var idElem) && idElem.ValueKind == JsonValueKind.String)
                    {
                        string id = idElem.GetString()!;
                        if (_pendientes.TryRemove(id, out var tcs))
                        {
                            tcs.TrySetResult(msg);
                            continue;
                        }
                    }

                    NotificarCrudo(OnMensajeRecibido, msg);
                    NotificarReceptores(msg, cierre: false);
                }
            }
            catch (Exception error)
            {
                NotificarError(error.Message);
                NotificarDesconexion();
            }
        }

        private void NotificarReceptores(Dictionary<string, JsonElement> mensaje, bool cierre)
        {
            RespuestaRed respuesta;
            try
            {
                respuesta = MapeadorRespuestaRed.Mapear(mensaje);
            }
            catch (Exception error)
            {
                NotificarError(error.Message);
                return;
            }

            foreach (var receptor in _receptores.Keys)
            {
                try
                {
                    if (cierre) receptor.AlCierre(respuesta);
                    else receptor.AlRecibir(respuesta);
                }
                catch (Exception error)
                {
                    NotificarError(receptor, error.Message);
                }
            }
        }

        private void NotificarError(string motivo)
        {
            foreach (var receptor in _receptores.Keys)
                NotificarError(receptor, motivo);
        }

        private static void NotificarError(IReceptorMensajes receptor, string motivo)
        {
            try { receptor.AlError(motivo); }
            catch { /* Los errores del receptor no deben detener el lector TCP. */ }
        }

        private void NotificarDesconexion()
        {
            if (OnDesconectado is { } evento)
            {
                foreach (Action callback in evento.GetInvocationList())
                {
                    try { callback(); }
                    catch { /* Un consumidor no debe impedir avisar a los demás. */ }
                }
            }
            foreach (var receptor in _receptores.Keys)
            {
                try { receptor.AlDesconexion(); }
                catch (Exception error) { NotificarError(receptor, error.Message); }
            }
        }

        private static void NotificarCrudo(
            Action<Dictionary<string, JsonElement>>? evento,
            Dictionary<string, JsonElement> mensaje)
        {
            if (evento is null) return;
            foreach (Action<Dictionary<string, JsonElement>> callback in evento.GetInvocationList())
            {
                try { callback(mensaje); }
                catch { /* Un consumidor legado no debe detener el lector TCP. */ }
            }
        }
    }
}
