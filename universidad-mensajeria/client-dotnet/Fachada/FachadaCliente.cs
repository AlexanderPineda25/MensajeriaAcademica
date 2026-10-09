using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Mensajeria.Componentes;
using Mensajeria.Store;
using Mensajeria.Fachada.Servicios;
using Mensajeria.Transversal;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Fachada
{
    /// <summary>
    /// Unico punto de entrada de la UI (espejo de FachadaCliente Java, Fase 5):
    /// inyecta interfaces, nunca implementaciones. Enviado con red → pedir() +
    /// ACK con metadatos; sin red → historial + pendiente con reintento.
    /// </summary>
    public class FachadaCliente : IFachadaCliente, IReceptorMensajes
    {
        public const string DestinoBroadcast = "*";

        private IConexionRed? _conexion;
        private IAutenticacion? _autenticacion;
        private readonly IHistorialLocal? _historial;
        private readonly ICacheUsuarios? _cache;
        private readonly IMapeadorDatos _mapeadorDatos;
        private readonly CoordinadorMensajeriaCliente _coordinadorMensajeria;
        private readonly ReintentadorPendientesCliente _reintentadorPendientes;

        private string _host = "localhost";
        private int _puerto = 5000;
        private bool _tlsHabilitado;
        private int _tlsPuerto = 5001;
        private string _tlsConfianza = "";
        private string _dbUrl = "sqlite:///~/mensajeria_cliente.db";
        private LocalStore? _store;
        private readonly bool _dependenciasInyectadas;

        public event Action<Dictionary<string, JsonElement>>? OnMensajeRecibido;
        public event Action<Dictionary<string, JsonElement>>? OnCierre;
        public event Action<Dictionary<string, JsonElement>>? OnPresencia;
        public event Action? OnDesconectado;
        public string? UsuarioActual => _autenticacion?.CodigoActual;

        /// <summary>Produccion: lee client.properties (mismo formato, §5.2).</summary>
        public FachadaCliente()
        {
            _dependenciasInyectadas = false;
            _mapeadorDatos = new MapeadorDatosServidor();
            _coordinadorMensajeria = new CoordinadorMensajeriaCliente(() => Historial);
            _reintentadorPendientes = new ReintentadorPendientesCliente(
                () => Historial, _coordinadorMensajeria);
            CargarPropiedades();
        }

        /// <summary>Producción: el composition root inyecta el mapeador del borde JSON.</summary>
        public FachadaCliente(IMapeadorDatos mapeadorDatos)
        {
            _dependenciasInyectadas = false;
            _mapeadorDatos = mapeadorDatos ?? throw new ArgumentNullException(nameof(mapeadorDatos));
            _coordinadorMensajeria = new CoordinadorMensajeriaCliente(() => Historial);
            _reintentadorPendientes = new ReintentadorPendientesCliente(
                () => Historial, _coordinadorMensajeria);
            CargarPropiedades();
        }

        /// <summary>Tests / DI: componentes ya montados.</summary>
        public FachadaCliente(IConexionRed conexion, IAutenticacion autenticacion,
            IHistorialLocal? historial, ICacheUsuarios? cache,
            IMapeadorDatos? mapeadorDatos = null)
        {
            _dependenciasInyectadas = true;
            _conexion = conexion;
            _autenticacion = autenticacion;
            _historial = historial;
            _cache = cache;
            _mapeadorDatos = mapeadorDatos ?? new MapeadorDatosServidor();
            _coordinadorMensajeria = new CoordinadorMensajeriaCliente(() => Historial);
            _reintentadorPendientes = new ReintentadorPendientesCliente(
                () => Historial, _coordinadorMensajeria);
            SuscribirRed();
        }

        private void CargarPropiedades()
        {
            _host = Transversal.ClienteConfig.Valor("host", Transversal.ClienteConfig.HostPorDefecto);
            _puerto = Transversal.ClienteConfig.Entero("puerto",
                    Transversal.ClienteConfig.PuertoPorDefecto, 1, 65535);
            _tlsHabilitado = ValidarTls.HabilitadoDesdeTexto(
                Transversal.ClienteConfig.Valor("tls.habilitado", "false"));
            _tlsPuerto = Transversal.ClienteConfig.Entero("tls.puerto",
                    Transversal.ClienteConfig.TlsPuertoPorDefecto, 1, 65535);
            // Vacío = validación de cadena del sistema (lo correcto por defecto).
            _tlsConfianza = Transversal.ClienteConfig.Valor("tls.confianza", "");
            _dbUrl = Transversal.ClienteConfig.Valor("db.url", Transversal.ClienteConfig.DbUrlPorDefecto);
        }

        public Task ConectarAsync() => ConectarAsync(_host, _puerto);

        public async Task ConectarAsync(string host, int puerto)
        {
            CambiarEstado(EstadoConexion.Conectando);
            try
            {
                await ConectarInternoAsync(host, puerto);
                CambiarEstado(EstadoConexion.Conectado);
            }
            catch
            {
                CambiarEstado(EstadoConexion.SinRed);
                throw;
            }
        }

        private async Task ConectarInternoAsync(string host, int puerto)
        {
            if (_dependenciasInyectadas)
            {
                await _conexion!.ConectarAsync(host, puerto);
                return;
            }
            var conexion = new ConexionRed
            {
                TlsHabilitado = _tlsHabilitado,
                TlsPuerto = _tlsPuerto,
                TlsConfianza = _tlsConfianza,
                // SEG-01: si hay PEM propio se valida contra él (nombre + cadena);
                // si no, CrearValidador devuelve null y manda la validación del sistema.
                ValidarCertificado = _tlsHabilitado
                    ? ValidarTls.CrearValidador(_tlsConfianza, host)
                    : null
            };
            await conexion.ConectarAsync(host, puerto);
            _conexion = conexion;
            string dbPath = _dbUrl.StartsWith("sqlite:///") ? _dbUrl["sqlite:///".Length..] : _dbUrl;
            _store = new LocalStore(dbPath);
            // Nota: _historial/_cache readonly solo en ctor DI; aqui se asignan una vez.
            _historialProd = new HistorialLocal(_store);
            _cacheProd = new CacheUsuarios(_store);
            _autenticacion = new Autenticacion(conexion);
            SuscribirRed();
        }

        // Backing para la ruta de produccion (la de tests viene por ctor).
        private HistorialLocal? _historialProd;
        private CacheUsuarios? _cacheProd;
        private IHistorialLocal? Historial => _historial ?? _historialProd;
        private ICacheUsuarios? Cache => _cache ?? _cacheProd;

        private void SuscribirRed()
        {
            if (_conexion == null) return;
            _conexion.Suscribir(this);
        }

        public void AlRecibir(RespuestaRed mensaje) =>
            AlRecibir(MapeadorRespuestaRed.ACrudo(mensaje));

        public void AlCierre(RespuestaRed aviso)
        {
            // PAT-03: CLOSE_NOTICE cierra la sesión, sin convertirlo en una
            // caída recuperable de transporte.
            CambiarEstado(EstadoConexion.SesionCerrada);
            OnCierre?.Invoke(MapeadorRespuestaRed.ACrudo(aviso));
        }

        public void AlError(string motivo) =>
            Trace.TraceError("Error al recibir mensaje de red: {0}", motivo);

        public void AlDesconexion()
        {
            // Un cierre voluntario no degrada una sesión que ya fue cerrada.
            if (_estado != EstadoConexion.SesionCerrada)
                CambiarEstado(EstadoConexion.SinRed);
            OnDesconectado?.Invoke();
        }

        // ------------------------------------------------------- PAT-03: estado

        private EstadoConexion _estado = EstadoConexion.SinRed;

        /// <summary>Estado actual de la conexión (antes nadie lo consultaba).</summary>
        public EstadoConexion EstadoConexion => _estado;

        /// <summary>La vista se suscribe aquí para pintar el banner (UIX-00a).</summary>
        public event Action<EstadoConexion>? OnEstadoConexion;

        private void CambiarEstado(EstadoConexion nuevo)
        {
            if (nuevo == _estado) return;
            _estado = nuevo;
            OnEstadoConexion?.Invoke(nuevo);
        }

        public void Desconectar()
        {
            DetenerFlushPendientes();
            _autenticacion?.Logout();
            _conexion?.Desconectar();
            CambiarEstado(EstadoConexion.SinRed);
            _store?.Dispose();
            _store = null;
        }

        public async Task<bool> LoginAsync(string codigo, string contrasena)
        {
            if (_autenticacion == null) throw new InvalidOperationException("ConectarAsync primero");
            bool ok = await _autenticacion.LoginAsync(codigo, contrasena);
            if (ok)
            {
                CambiarEstado(EstadoConexion.Conectado);
                CambiarAStorePorCuenta(codigo);
                AsegurarEnCache(codigo);
                await ReintentarPendientesAsync();
                IniciarFlushPendientes(IntervaloFlushSegundos);
            }
            return ok;
        }

        /// <summary>
        /// FASE 11.1: worker que vacia pendientes en orden cada cierto tiempo
        /// (best-effort). Cubre la reconexion silenciosa sin accion de la UI.
        /// </summary>
        public const int IntervaloFlushSegundos = 30;
        private System.Threading.Timer? _flush;
        private readonly object _flushLock = new();

        public void IniciarFlushPendientes(int segundos)
        {
            lock (_flushLock)
            {
                if (_flush != null) return;
                var periodo = TimeSpan.FromSeconds(Math.Max(1, segundos));
                _flush = new System.Threading.Timer(_ =>
                {
                    try { ReintentarPendientesAsync().GetAwaiter().GetResult(); }
                    catch { }
                }, null, periodo, periodo);
            }
        }

        public void DetenerFlushPendientes()
        {
            lock (_flushLock)
            {
                _flush?.Dispose();
                _flush = null;
            }
        }

        /// <summary>
        /// Resuelve SQLite por cuenta (&lt;base&gt;_&lt;cuenta&gt;.db, misma ruta que Python),
        /// migra el legado de forma idempotente y conmuta el historial/caché.
        /// Dos procesos con la misma cuenta comparten archivo (WAL); cuentas
        /// distintas nunca comparten filas. El legado queda como respaldo.
        /// </summary>
        private void CambiarAStorePorCuenta(string codigo)
        {
            if (_dependenciasInyectadas) return;
            try
            {
                string legada = LocalStore.RutaLegada(_dbUrl);
                string porCuenta = LocalStore.RutaParaCuenta(_dbUrl, codigo);
                if (_store != null && string.Equals(_store.DbPath, porCuenta, StringComparison.OrdinalIgnoreCase))
                    return;
                LocalStore.MigrarLegadoACuenta(legada, porCuenta, codigo);
                _store?.Dispose();
                _store = new LocalStore(porCuenta);
                _historialProd = new HistorialLocal(_store);
                _cacheProd = new CacheUsuarios(_store);
            }
            catch { }
        }

        /// <summary>Conectados del servidor (array de strings, PROTOCOLO.md §3) + cache.</summary>
        public async Task<List<string>> ListarConectadosAsync()
        {
            if (_conexion == null) throw new InvalidOperationException("ConectarAsync primero");
            var msg = new Dictionary<string, object?> { { "tipo", TipoMensaje.LISTAR_CONECTADOS } };
            var resp = await _conexion.Pedir(msg).ConfigureAwait(false);

            var lista = new List<string>();
            if (resp.TryGetValue("usuariosConectados", out var arr) && arr.ValueKind == JsonValueKind.Array)
            {
                foreach (var item in arr.EnumerateArray())
                {
                    if (item.ValueKind == JsonValueKind.String)
                        lista.Add(item.GetString()!);
                    else if (item.ValueKind == JsonValueKind.Object
                        && item.TryGetProperty("codigo", out var c)
                        && c.ValueKind == JsonValueKind.String)
                        lista.Add(c.GetString()!); // tolerante, el contrato manda strings
                }
            }
            Cache?.MarcarConectados(lista);
            foreach (var codigo in lista) AsegurarEnCache(codigo);
            return lista;
        }

        public async Task<List<Dictionary<string, object>>> ListarUsuariosAsync()
        {
            if (_conexion == null) throw new InvalidOperationException("ConectarAsync primero");
            var resp = await _conexion.Pedir(new Dictionary<string, object?>
                { { "tipo", TipoMensaje.LISTAR_USUARIOS } }).ConfigureAwait(false);
            // El servidor envia el directorio en `contenido` (JSON) — ListarCaso.java,
            // igual que lee el cliente Java (FachadaCliente.sincronizarDirectorio).
            string contenido = GetString(resp, "contenido") ?? "[]";
            List<UsuarioLocal> filas;
            try { filas = _mapeadorDatos.MapearDirectorio(contenido); }
            catch { return DirectorioLocal(); }
            if (filas.Count == 0) return DirectorioLocal();
            var usuarios = new List<Dictionary<string, object>>();
            foreach (var usuario in filas)
            {
                string codigo = usuario.Codigo!;
                string nombres = usuario.Nombres ?? "";
                string apellidos = usuario.Apellidos ?? "";
                Cache?.Guardar(codigo, nombres, apellidos, usuario.Programa,
                    usuario.Conectado ? 1 : 0, usuario.FechaRegistro,
                    usuario.Actualizado ?? DateTime.UtcNow.ToString("O"));
                usuarios.Add(new Dictionary<string, object>
                {
                    ["codigo"] = codigo, ["nombres"] = nombres,
                    ["apellidos"] = apellidos, ["conectado"] = usuario.Conectado
                });
            }
            Cache?.MarcarConectados(usuarios.Where(u => (bool)u["conectado"])
                .Select(u => (string)u["codigo"]).ToList());
            return usuarios;
        }

public async Task MarcarLeidosAsync(string otroUsuario)
        {
            if (UsuarioActual == null || _conexion == null)
                throw new InvalidOperationException("inicia sesion primero");
            var pendientes = Pendientes();
            foreach (string id in Historial?.IdsNoLeidos(UsuarioActual, otroUsuario) ?? new List<string>())
            {
                string idPendiente = "lectura:" + id;
                if (pendientes.Any(p => (string?)p["id"] == idPendiente)) continue;
                Historial?.RegistrarPendiente(idPendiente, TipoMensaje.MENSAJE_LEIDO,
                    UsuarioActual, otroUsuario, id, null, null, DateTime.UtcNow.ToString("O"));
            }
            Historial?.MarcarLeidos(UsuarioActual, otroUsuario);
            await ReintentarPendientesAsync().ConfigureAwait(false);
        }

        /// <summary>UIX-02: no leídos por contacto para la insignia.</summary>
        public IReadOnlyDictionary<string, int> ContarNoLeidos()
        {
            if (UsuarioActual == null || Historial == null)
                return new Dictionary<string, int>();
            return Historial.ContarNoLeidosPorContacto(UsuarioActual);
        }

        /// <summary>UIX-06: ids con el patrón (de más nuevo a más viejo).</summary>
        public List<string> BuscarEnHistorial(string otro, string patron)
        {
            if (UsuarioActual == null || Historial == null)
                return new List<string>();
            return Historial.BuscarEnConversacion(UsuarioActual, otro, patron, 200);
        }

        public async Task ExpulsarOtrasSesionesAsync()
        {
            if (_conexion == null || UsuarioActual == null)
                throw new InvalidOperationException("inicia sesion primero");
            var resp = await _conexion.Pedir(new Dictionary<string, object?>
            {
                { "tipo", TipoMensaje.KICK }, { "codigo", UsuarioActual }
            }).ConfigureAwait(false);
            LanzarSiError(resp, "no se pudieron cerrar las otras sesiones", TipoMensaje.ACK);
        }

        /// <summary>True si salio por red (ACK); False si quedo en pendientes.
        /// Persiste PENDIENTE antes de la red con el ID estable, para que un acuse
        /// rápido (ENTREGADO/LEIDO) que llegue durante la espera correlacione por
        /// el mismo ID y no se pierda: el Upsert posterior nunca retrocede estado.
        /// </summary>
        public async Task<bool> EnviarTextoAsync(string destinatario, string contenido)
        {
            ExigirSesion(destinatario, contenido);
            RechazarDestinoPropio(destinatario);
            var (conexion, yo) = SesionEnvio();
            return await _coordinadorMensajeria.EnviarTextoAsync(
                conexion, yo, destinatario, contenido).ConfigureAwait(false);
        }

        public async Task<bool> EnviarImagenAsync(string destinatario, string imagePath)
        {
            if (string.IsNullOrEmpty(destinatario)) throw new ArgumentException("elige un destinatario");
            var (conexion, yo) = SesionEnvio();
            RechazarDestinoPropio(destinatario);
            return await _coordinadorMensajeria.EnviarImagenAsync(
                conexion, yo, destinatario, imagePath).ConfigureAwait(false);
        }

        public const long MaxBytesArchivo = IFachadaCliente.MaxBytesArchivo;
        public const int ParteArchivoBytes = CoordinadorMensajeriaCliente.ParteArchivoBytes;

        /// <summary>
        /// Envía un archivo genérico fragmentado (tope propio 50 MB, PROTOCOLO.md §3/§5):
        /// INICIO + PARTES base64 ≤1 MiB secuenciales + FIN con sha256. Persiste
        /// PENDIENTE con el ID estable antes de la red y reutiliza el mismo ID
        /// en reintentos (el servidor reinicia el buffer ante el mismo id).
        /// </summary>
        public async Task<bool> EnviarArchivoAsync(string destinatario, string rutaArchivo,
            IProgress<double>? progreso = null, CancellationToken cancelacion = default)
        {
            if (string.IsNullOrEmpty(destinatario)) throw new ArgumentException("elige un destinatario");
            var (conexion, yo) = SesionEnvio();
            RechazarDestinoPropio(destinatario);
            return await _coordinadorMensajeria.EnviarArchivoAsync(
                conexion, yo, destinatario, rutaArchivo, progreso, cancelacion)
                .ConfigureAwait(false);
        }

        public async Task DifundirAsync(string contenido)
        {
            if (_conexion == null || UsuarioActual == null) throw new InvalidOperationException("inicia sesion primero");
            if (string.IsNullOrWhiteSpace(contenido)) throw new ArgumentException("el contenido no puede estar vacio");
            var id = Guid.NewGuid().ToString();
            var fecha = DateTime.UtcNow.ToString("O");
            var resp = await _conexion.Pedir(new Dictionary<string, object?>
            {
                { "id", id },
                { "tipo", TipoMensaje.BROADCAST },
                { "remitente", UsuarioActual },
                { "contenido", contenido },
                { "fechaHora", fecha }
            }).ConfigureAwait(false);
            LanzarSiError(resp, "difusion rechazada", TipoMensaje.ACK);
            Historial?.UpsertMensaje(id, UsuarioActual, DestinoBroadcast,
                TipoMensaje.BROADCAST, contenido, null, null, null,
                null, null, null, fecha, 1, 1);
        }

        /// <summary>
        /// DESCARGAR_ARCHIVO bajo demanda: respuesta unica para archivos chicos y
        /// secuencia INICIO+PARTES+FIN reensamblada para archivos grandes (&gt;1 MiB).
        /// </summary>
        public async Task<string> DescargarArchivoAsync(string idMensaje, string archivoId, string rutaDestino)
        {
            if (_conexion == null) throw new InvalidOperationException("ConectarAsync primero");
            var colector = new ColectorFragmentos(null);
            void AlPush(Dictionary<string, JsonElement> msg) => colector.AlPush(msg);
            _conexion.OnMensajeRecibido += AlPush;
            try
            {
                var resp = await _conexion.Pedir(new Dictionary<string, object?>
                {
                    { "tipo", TipoMensaje.DESCARGAR_ARCHIVO },
                    { "archivoId", archivoId },
                    { "fechaHora", DateTime.UtcNow.ToString("O") }
                });
                if ((GetString(resp, "tipo") == TipoMensaje.ARCHIVO_INICIO))
                {
                    string? idT = GetString(resp, "id");
                    if (idT == null) throw new InvalidOperationException("descarga sin id");
                    colector.FijarId(idT);
                    colector.AlPush(resp);
                    byte[] bytes = await colector.EsperarAsync(TimeSpan.FromMinutes(5))
                        .ConfigureAwait(false);
                    await File.WriteAllBytesAsync(rutaDestino, bytes).ConfigureAwait(false);
                    Historial?.MarcarDescargado(idMensaje, rutaDestino);
                    return rutaDestino;
                }
                LanzarSiError(resp, "descarga rechazada", TipoMensaje.DESCARGAR_ARCHIVO_RESPUESTA);
                string b64 = GetString(resp, "contenidoImagen") ?? "";
                await File.WriteAllBytesAsync(rutaDestino, Convert.FromBase64String(b64))
                    .ConfigureAwait(false);
                Historial?.MarcarDescargado(idMensaje, rutaDestino);
                return rutaDestino;
            }
            finally
            {
                _conexion.OnMensajeRecibido -= AlPush;
            }
        }

        /// <summary>Reensambla PARTES de una descarga correlacionadas por id.</summary>
        private sealed class ColectorFragmentos
        {
            private readonly object _bloqueo = new();
            private readonly Dictionary<int, byte[]> _partes = new();
            private int _total = -1;
            private string? _hash;
            private string? _idEsperado;
            private readonly TaskCompletionSource<byte[]> _listo = new(
                TaskCreationOptions.RunContinuationsAsynchronously);

            public ColectorFragmentos(string? idEsperado) { _idEsperado = idEsperado; }

            public void FijarId(string id)
            {
                lock (_bloqueo) { _idEsperado ??= id; }
            }

            public void AlPush(Dictionary<string, JsonElement> msg)
            {
                lock (_bloqueo)
                {
                    if (_idEsperado != null && GetString(msg, "id") != _idEsperado) return;
                }
                string? tipo = GetString(msg, "tipo");
                try
                {
                    if (tipo == TipoMensaje.ARCHIVO_INICIO)
                    {
                        lock (_bloqueo)
                        {
                            if (msg.TryGetValue("totalPartes", out var tp)
                                && tp.ValueKind == JsonValueKind.Number
                                && tp.TryGetInt32(out int n)) _total = n;
                        }
                    }
                    else if (tipo == TipoMensaje.ARCHIVO_PARTE)
                    {
                        if (!msg.TryGetValue("indiceParte", out var ip)
                            || ip.ValueKind != JsonValueKind.Number
                            || !ip.TryGetInt32(out int indice)) return;
                        string? b64 = GetString(msg, "contenidoImagen");
                        if (b64 == null) return;
                        byte[] bytes = Convert.FromBase64String(b64);
                        bool completa = false;
                        lock (_bloqueo)
                        {
                            _partes[indice] = bytes;
                            completa = _total > 0 && _partes.Count == _total && _hash != null;
                        }
                        if (completa) Completar();
                    }
                    else if (tipo == TipoMensaje.ARCHIVO_FIN)
                    {
                        bool completa = false;
                        lock (_bloqueo)
                        {
                            _hash = GetString(msg, "hashSha256");
                            completa = _total > 0 && _partes.Count == _total && _hash != null;
                        }
                        if (completa) Completar();
                    }
                }
                catch { }
            }

            private void Completar()
            {
                try
                {
                    List<int> orden;
                    lock (_bloqueo) { orden = new List<int>(_partes.Keys); }
                    orden.Sort();
                    using var ms = new MemoryStream();
                    foreach (int i in orden) ms.Write(_partes[i], 0, _partes[i].Length);
                    byte[] todo = ms.ToArray();
                    string hash;
                    using (var sha = System.Security.Cryptography.SHA256.Create())
                        hash = Convert.ToHexString(sha.ComputeHash(todo)).ToLowerInvariant();
                    lock (_bloqueo)
                    {
                        if (_hash != null && !hash.Equals(_hash, StringComparison.OrdinalIgnoreCase))
                        {
                            _listo.TrySetException(new InvalidOperationException(
                                "sha256 de la descarga no coincide"));
                            return;
                        }
                    }
                    _listo.TrySetResult(todo);
                }
                catch (Exception e)
                {
                    _listo.TrySetException(e);
                }
            }

            public async Task<byte[]> EsperarAsync(TimeSpan espera)
            {
                using var cts = new CancellationTokenSource(espera);
                using (cts.Token.Register(() => _listo.TrySetException(
                    new TimeoutException("descarga fragmentada incompleta"))))
                {
                    return await _listo.Task.ConfigureAwait(false);
                }
            }
        }

        public string? ContenidoImagenLocal(string idMensaje) => Historial?.ContenidoPorId(idMensaje);

        /// <summary>Historial en orden cronologico (los mas nuevos abajo, como Python).
        /// Ordena por instante real y no por texto: los formatos mezclados
        /// (UTC con Z vs hora local) rompen el ORDER BY alfabetico.</summary>
        public List<Dictionary<string, object>> CargarHistorial(string otroUsuario, int offset = 0)
        {
            var yo = UsuarioActual;
            if (yo == null) throw new InvalidOperationException("inicia sesion primero");
            var pagina = Historial?.PaginaConversacion(yo, otroUsuario, 50, offset)
                ?? new List<Dictionary<string, object>>();
            pagina.Sort((a, b) => CompararFecha(
                a.TryGetValue("fechaEnvio", out var fa) ? fa?.ToString() : null,
                b.TryGetValue("fechaEnvio", out var fb) ? fb?.ToString() : null));
            return pagina;
        }

        private static int CompararFecha(string? x, string? y) =>
            ParseRobusto(x).CompareTo(ParseRobusto(y));

        private static DateTimeOffset ParseRobusto(string? s) =>
            !string.IsNullOrWhiteSpace(s) && DateTimeOffset.TryParse(s, out var dto)
                ? dto.ToUniversalTime() : DateTimeOffset.MaxValue;

        public async Task<int> TraerHistorialAsync(string otroUsuario, int pagina)
        {
            if (_conexion == null || UsuarioActual == null)
                throw new InvalidOperationException("inicia sesion primero");
            var resp = await _conexion.Pedir(new Dictionary<string, object?>
            {
                { "tipo", TipoMensaje.HISTORIAL_REQ }, { "remitente", UsuarioActual },
                { "destinatario", otroUsuario }, { "pagina", pagina }
            }).ConfigureAwait(false);
            LanzarSiError(resp, "historial rechazado", TipoMensaje.HISTORIAL_PAGE);
            string json = GetString(resp, "contenido") ?? "[]";
            foreach (var mensaje in _mapeadorDatos.MapearPaginaHistorial(json, UsuarioActual!))
            {
                Historial?.UpsertMensaje(mensaje.IdMensaje!, mensaje.Origen!,
                    mensaje.Destino!, mensaje.Tipo!, mensaje.Contenido,
                    mensaje.HashSha256, mensaje.NumCaracteres, mensaje.NumPalabras,
                    mensaje.NombreArchivo, mensaje.RutaArchivo, mensaje.TamanoArchivo,
                    mensaje.FechaEnvio!, mensaje.Enviado ? 1 : 0,
                    mensaje.Descargado ? 1 : 0, mensaje.Estado!, mensaje.ArchivoId,
                    mensaje.EtapasFiltrado);
            }
            return GetInt(resp, "totalPaginas") ?? 0;
        }

        public int ContarConversacion(string otro) =>
            UsuarioActual == null ? 0 : (Historial?.ContarConversacion(UsuarioActual, otro) ?? 0);

        public IReadOnlyDictionary<string, string> UltimosMensajesPorContacto() =>
            UsuarioActual == null
                ? new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
                : (Historial?.UltimosMensajesPorContacto(UsuarioActual)
                    ?? new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase));

        public List<Dictionary<string, object>> DirectorioLocal() =>
            Cache?.Listar() ?? new List<Dictionary<string, object>>();

        public List<Dictionary<string, object?>> Pendientes() =>
            Historial?.ListarPendientes() ?? new List<Dictionary<string, object?>>();

        public Task<int> ReintentarPendientesAsync() =>
            _reintentadorPendientes.ReintentarPendientesAsync(_conexion, UsuarioActual);

        private void AlRecibir(Dictionary<string, JsonElement> msg)
        {
            if (!msg.TryGetValue("tipo", out var t) || t.ValueKind != JsonValueKind.String) return;
            string tipo = t.GetString()!;
            if (tipo == TipoMensaje.MENSAJE_ENTREGADO || tipo == TipoMensaje.MENSAJE_LEIDO)
            {
                string? idMensaje = GetString(msg, "contenido");
                if (idMensaje != null)
                    Historial?.MarcarEstado(idMensaje,
                        tipo == TipoMensaje.MENSAJE_LEIDO ? "LEIDO" : "ENTREGADO");
                OnMensajeRecibido?.Invoke(msg);
                return;
            }
            if (tipo == TipoMensaje.PRESENCIA)
            {
                var conectados = new List<string>();
                if (msg.TryGetValue("usuariosConectados", out var lista)
                    && lista.ValueKind == JsonValueKind.Array)
                {
                    foreach (var item in lista.EnumerateArray())
                        if (item.ValueKind == JsonValueKind.String && item.GetString() is string codigo)
                            conectados.Add(codigo);
                }
                Cache?.MarcarConectados(conectados);
                if (msg.TryGetValue("codigo", out var codigoMsg)
                    && codigoMsg.ValueKind == JsonValueKind.String)
                    AsegurarEnCache(codigoMsg.GetString()!);
                OnPresencia?.Invoke(msg);
                return;
            }
            if (tipo == TipoMensaje.ARCHIVO_INICIO || tipo == TipoMensaje.ARCHIVO_PARTE)
                return; // Partes de descarga: las consume el colector de DescargarArchivoAsync.
            if (tipo == TipoMensaje.ARCHIVO_FIN)
            {
                OnMensajeRecibido?.Invoke(msg);
                return;
            }
            if (tipo != TipoMensaje.MENSAJE_TEXTO && tipo != TipoMensaje.MENSAJE_IMAGEN
                && tipo != TipoMensaje.MENSAJE_ARCHIVO
                && tipo != TipoMensaje.BROADCAST && tipo != TipoMensaje.SYNC_LOGIN) return;
            try
            {
                // Lo entrante siempre es (remitente -> yo): el broadcast aparece
                // en la conversacion con su remitente (leccion Fase 5).
                string remitente = GetString(msg, "remitente") ?? "desconocido";
                string id = GetString(msg, "id") ?? Guid.NewGuid().ToString();
                string fecha = GetString(msg, "fechaHora") ?? DateTime.UtcNow.ToString("O");
                bool esImagen = tipo == TipoMensaje.MENSAJE_IMAGEN;
                bool esArchivo = tipo == TipoMensaje.MENSAJE_ARCHIVO;
                string? texto = esImagen ? "[Imagen]"
                    : esArchivo ? $"[Archivo: {GetString(msg, "nombreArchivo") ?? "archivo"}]"
                    : GetString(msg, "contenido");
                Historial?.UpsertMensaje(id, remitente, UsuarioActual ?? remitente, tipo,
                    texto,
                    GetString(msg, "hashSha256"), GetInt(msg, "numCaracteres"),
                    GetInt(msg, "numPalabras"), GetString(msg, "nombreArchivo"),
                    null, GetLong(msg, "tamanoArchivo"), fecha, 0,
                    tipo == TipoMensaje.MENSAJE_TEXTO ? 1 : 0,
                    "ENTREGADO", GetString(msg, "archivoId"),
                    GetString(msg, "etapasFiltrado"));
                AsegurarEnCache(remitente);
            }
            catch { }
            OnMensajeRecibido?.Invoke(msg);
        }

        private void ExigirSesion(string destinatario, string contenido)
        {
            if (_conexion == null || UsuarioActual == null)
                throw new InvalidOperationException("inicia sesion primero");
            if (string.IsNullOrWhiteSpace(destinatario))
                throw new ArgumentException("elige un destinatario");
            if (string.IsNullOrWhiteSpace(contenido))
                throw new ArgumentException("el contenido no puede estar vacio");
        }

        private void RechazarDestinoPropio(string destinatario)
        {
            if (UsuarioActual != null && destinatario == UsuarioActual)
                throw new ArgumentException("elige un destinatario distinto a tu cuenta");
        }

        /// <summary>Foto de la sesion para operaciones largas: si el servidor
        /// cierra la sesion a mitad del envio se sigue con estos valores en vez
        /// de NullReferenceException.</summary>
        private (IConexionRed Conexion, string Usuario) SesionEnvio()
        {
            var conexion = _conexion;
            var usuario = UsuarioActual;
            if (conexion == null || usuario == null)
                throw new InvalidOperationException("sesión cerrada, vuelve a iniciar sesión");
            return (conexion, usuario);
        }

        private void AsegurarEnCache(string codigo)
        {
            if (Cache?.PorCodigo(codigo) == null)
            {
                string ahora = DateTime.UtcNow.ToString("O");
                Cache?.Guardar(codigo, codigo, "", null, 0, ahora, ahora);
            }
        }

        private static void LanzarSiError(Dictionary<string, JsonElement> resp,
            string defecto, params string[] okTipos)
        {
            resp.TryGetValue("tipo", out var t);
            string tipo = t.ValueKind == JsonValueKind.String ? t.GetString()! : "";
            bool esOk = okTipos.Length == 0 || Array.IndexOf(okTipos, tipo) >= 0;
            bool exito = !resp.TryGetValue("exito", out var e)
                || e.ValueKind != JsonValueKind.False;
            if (esOk && exito) return;
            string motivo = GetString(resp, "mensajeError") ?? defecto;
            throw new InvalidOperationException(motivo);
        }

        private static string? GetString(Dictionary<string, JsonElement> m, string clave) =>
            m.TryGetValue(clave, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

        private static string? Texto(JsonElement m, string clave) =>
            m.TryGetProperty(clave, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

        private static int? Numero(JsonElement m, string clave) =>
            m.TryGetProperty(clave, out var v) && v.ValueKind == JsonValueKind.Number && v.TryGetInt32(out int n) ? n : null;

        private static long? NumeroLong(JsonElement m, string clave) =>
            m.TryGetProperty(clave, out var v) && v.ValueKind == JsonValueKind.Number && v.TryGetInt64(out long n) ? n : null;

        private static int? GetInt(Dictionary<string, JsonElement> m, string clave) =>
            m.TryGetValue(clave, out var v) && v.ValueKind == JsonValueKind.Number && v.TryGetInt32(out int n) ? n : null;

        private static long? GetLong(Dictionary<string, JsonElement> m, string clave) =>
            m.TryGetValue(clave, out var v) && v.ValueKind == JsonValueKind.Number && v.TryGetInt64(out long n) ? n : null;
    }
}
