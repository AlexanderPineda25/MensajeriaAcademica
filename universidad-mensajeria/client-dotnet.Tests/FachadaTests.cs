using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using Mensajeria.Componentes;
using Mensajeria.Fachada;
using Mensajeria.Store;
using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>Fachada .NET con red falsa y SQLite real: online/offline/reintento.</summary>
    public class FachadaTests : IDisposable
    {
        private readonly string _dbPath;
        private readonly LocalStore _store;
        private readonly RedFalsa _red;
        private readonly FachadaCliente _f;
        private readonly List<Dictionary<string, JsonElement>> _avisos = new();

        public FachadaTests()
        {
            _dbPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".db");
            _store = new LocalStore(_dbPath);
            _red = new RedFalsa();
            var hist = new HistorialLocal(_store);
            var cache = new CacheUsuarios(_store);
            _f = new FachadaCliente(_red, new Autenticacion(_red), hist, cache);
            _f.OnMensajeRecibido += m => _avisos.Add(m);
            Assert.True(_f.LoginAsync("A001", "x").GetAwaiter().GetResult());
        }

        public void Dispose()
        {
            _store.Dispose();
            try { File.Delete(_dbPath); } catch { }
        }

        [Fact]
        public async Task EnviarTextoOnlineGuardaMetadatos()
        {
            Assert.True(await _f.EnviarTextoAsync("B002", "hola"));
            var conv = _f.CargarHistorial("B002");
            Assert.Single(conv);
            Assert.Equal("hola", conv[0]["contenido"]);
            Assert.Equal("h", conv[0]["hash"]);
            Assert.Equal(conv[0]["id"], _red.Peticiones.Last()["id"]);
            Assert.Empty(_f.Pendientes());
        }

        [Fact]
        public async Task BuscarEnHistorialDevuelveIdsDeMasNuevoAMasViejo()
        {
            // UIX-06: la fachada delega en HistorialLocal.BuscarEnConversacion.
            Assert.True(await _f.EnviarTextoAsync("B002", "hola mundo"));
            Assert.True(await _f.EnviarTextoAsync("B002", "HOLA de vuelta"));
            Assert.True(await _f.EnviarTextoAsync("C003", "hola otro"));

            var ids = _f.BuscarEnHistorial("B002", "HOLA");

            Assert.Equal(2, ids.Count);
            Assert.DoesNotContain(
                (string)_f.CargarHistorial("C003")[0]["id"], ids);
            Assert.Empty(_f.BuscarEnHistorial("B002", "inexistente"));
            Assert.Empty(_f.BuscarEnHistorial("B002", "  "));
        }

        [Fact]
        public async Task KickSolicitaCerrarOtrasSesionesSinCerrarLaActual()
        {
            await _f.ExpulsarOtrasSesionesAsync();

            var solicitud = Assert.Single(_red.Peticiones, p =>
                (string?)p.GetValueOrDefault("tipo") == "KICK");
            Assert.Equal("A001", solicitud["codigo"]);
            Assert.Equal("A001", _f.UsuarioActual);
        }

        [Fact]
        public void AcuseActualizaElMismoRegistroPorId()
        {
            _f.EnviarTextoAsync("B002", "hola").GetAwaiter().GetResult();
            string id = (string)_f.CargarHistorial("B002")[0]["id"];
            _red.Recibir(Doc(new Dictionary<string, object?>
                { { "tipo", "MENSAJE_ENTREGADO" }, { "contenido", id } }));
            Assert.Equal("ENTREGADO", _f.CargarHistorial("B002")[0]["estado"]);
            _red.Recibir(Doc(new Dictionary<string, object?>
                { { "tipo", "MENSAJE_LEIDO" }, { "contenido", id } }));
            Assert.Equal("LEIDO", _f.CargarHistorial("B002")[0]["estado"]);
        }

        [Fact]
        public async Task AcuseDeLecturaOfflineSeReintentaSinDuplicarse()
        {
            _red.Recibir(Doc(new Dictionary<string, object?>
                { { "tipo", "MENSAJE_TEXTO" }, { "id", "entrante-1" },
                  { "remitente", "B002" }, { "contenido", "hola" },
                  { "fechaHora", "2026-10-01T10:00:00" } }));
            _red.Caida = true;
            await _f.MarcarLeidosAsync("B002");
            Assert.Single(_f.Pendientes());
            _red.Caida = false;
            Assert.Equal(1, await _f.ReintentarPendientesAsync());
            Assert.Empty(_f.Pendientes());
            Assert.Contains(_red.Peticiones, p => (string?)p.GetValueOrDefault("tipo") == "MENSAJE_LEIDO"
                && (string?)p.GetValueOrDefault("contenido") == "entrante-1");
        }

        [Fact]
        public async Task ErrorServidorSeElevaNoSeTraga()
        {
            await Assert.ThrowsAsync<InvalidOperationException>(() =>
                _f.EnviarTextoAsync("ZZZ", "hola"));
        }

        [Fact]
        public async Task EnviarOfflineEncolaYReintentaConAck()
        {
            _red.Caida = true;
            Assert.False(await _f.EnviarTextoAsync("B002", "luego"));
            Assert.Single(_f.Pendientes());
            _red.Caida = false;
            Assert.Equal(1, await _f.ReintentarPendientesAsync());
            Assert.Empty(_f.Pendientes());
        }

        [Fact]
        public async Task FlushWorkerVaciaPendientesSinLlamadaExplicita()
        {
            _red.Caida = true;
            Assert.False(await _f.EnviarTextoAsync("B002", "luego"));
            Assert.Single(_f.Pendientes());
            _red.Caida = false;
            _f.DetenerFlushPendientes();
            _f.IniciarFlushPendientes(1);
            try
            {
                var limite = DateTime.UtcNow.AddSeconds(15);
                while (_f.Pendientes().Count != 0 && DateTime.UtcNow < limite)
                    await Task.Delay(100);
                Assert.Empty(_f.Pendientes());
            }
            finally
            {
                _f.DetenerFlushPendientes();
            }
        }

        [Fact]
        public async Task BroadcastPropioMarcadoConDestinoEspecial()
        {
            await _f.DifundirAsync("aviso");
            var propias = _f.CargarHistorial(FachadaCliente.DestinoBroadcast);
            Assert.Single(propias);
            Assert.Equal("aviso", propias[0]["contenido"]);
        }

        [Fact]
        public void BroadcastEntranteApareceEnConversacionConSuRemitente()
        {
            _red.Recibir(Doc(new Dictionary<string, object?>
                { { "tipo", "BROADCAST" }, { "id", "bc-1" }, { "remitente", "B002" },
                  { "contenido", "hola a todos" }, { "fechaHora", "2026-01-01T10:00:00" } }));
            var conv = _f.CargarHistorial("B002");
            Assert.Single(conv);
            Assert.Equal("hola a todos", conv[0]["contenido"]);
            Assert.Equal("B002", conv[0]["origen"]);
            Assert.Single(_avisos);
        }

        [Fact]
        public void ImagenEntrantePersisteEtapasFiltradoDelPush()
        {
            const string etapas = "[{\"etapa\":\"EscalaGrises\",\"ms\":6,\"ruta\":\"privada/01.png\"}]";
            _red.Recibir(Doc(new Dictionary<string, object?>
            {
                ["tipo"] = "MENSAJE_IMAGEN",
                ["id"] = "imagen-push",
                ["remitente"] = "B002",
                ["destinatario"] = "A001",
                ["fechaHora"] = "2026-10-08T10:00:00Z",
                ["etapasFiltrado"] = etapas,
                ["archivoId"] = "archivo-push"
            }));

            var fila = Assert.Single(_f.CargarHistorial("B002"));

            Assert.Equal(etapas, fila["etapasFiltrado"]);
            Assert.Equal("archivo-push", fila["archivoId"]);
        }

        [Fact]
        public async Task HistorialRemotoPersisteEtapasFiltradoRecibido()
        {
            const string etapas = "[{\"etapa\":\"Reduccion\",\"ms\":14,\"ruta\":\"privada/05.png\"}]";
            _red.HistorialRemotoJson = "[{\"id\":\"imagen-remota\",\"remitente\":\"B002\","
                + "\"destinatario\":\"A001\",\"tipo\":\"MENSAJE_IMAGEN\","
                + "\"fechaEnvio\":\"2026-10-08T10:00:00Z\",\"etapasFiltrado\":"
                + JsonSerializer.Serialize(etapas) + "}]";

            await _f.TraerHistorialAsync("B002", 0);

            var fila = Assert.Single(_f.CargarHistorial("B002"));
            Assert.Equal("imagen-remota", fila["id"]);
            Assert.Equal(etapas, fila["etapasFiltrado"]);
        }

        [Fact]
        public async Task ConectarConservaLaConexionInyectada()
        {
            await _f.ConectarAsync();
            Assert.Equal(1, _red.Conexiones);
        }

        [Fact]
        public void PresenciaPushSeExponeALaUi()
        {
            bool notificada = false;
            _f.OnPresencia += _ => notificada = true;
            _red.Recibir(Doc(new Dictionary<string, object?>
            {
                { "tipo", "PRESENCIA" }, { "codigo", "B002" },
                { "contenido", "conectado" }, { "usuariosConectados", new[] { "A001", "B002" } }
            }));
            Assert.True(notificada);
        }

        [Fact]
        public void FachadaRegistraElReceptorTipadoYConservaLosEventosDeVista()
        {
            int cierres = 0;
            int desconexiones = 0;
            _f.OnCierre += _ => cierres++;
            _f.OnDesconectado += () => desconexiones++;

            _red.Recibir(Doc(new Dictionary<string, object?>
                { { "tipo", "MENSAJE_TEXTO" }, { "id", "tipado-1" },
                  { "remitente", "B002" }, { "contenido", "hola" } }));
            _red.CerrarSesion();
            _red.Caer();

            Assert.Contains(_avisos, mensaje =>
                mensaje.TryGetValue("id", out var id) && id.GetString() == "tipado-1");
            Assert.Equal(1, cierres);
            Assert.Equal(EstadoConexion.SesionCerrada, _f.EstadoConexion);
            Assert.Equal(1, desconexiones);
        }

        [Fact]
        public void ImagenEntranteNoGuardaBase64()
        {
            _red.Recibir(Doc(new Dictionary<string, object?>
                { { "tipo", "MENSAJE_IMAGEN" }, { "id", "im-1" }, { "remitente", "B002" },
                  { "contenidoImagen", "QUJD" }, { "nombreArchivo", "f.png" },
                  { "hashSha256", "hi" }, { "fechaHora", "2026-01-01T10:00:00" } }));
            var conv = _f.CargarHistorial("B002");
            Assert.False(conv[0].ContainsKey("contenido"));
        }

        private static Dictionary<string, JsonElement> Doc(Dictionary<string, object?> obj)
        {
            var json = JsonSerializer.Serialize(obj);
            using var doc = JsonDocument.Parse(json);
            var dict = new Dictionary<string, JsonElement>();
            foreach (var p in doc.RootElement.EnumerateObject())
                dict[p.Name] = p.Value.Clone();
            return dict;
        }

        private class RedFalsa : IConexionRed
        {
            public bool Caida { get; set; }
            public int Conexiones { get; private set; }
            public string? HistorialRemotoJson { get; set; }
            public List<Dictionary<string, object?>> Peticiones { get; } = new();
            public event Action<Dictionary<string, JsonElement>>? OnMensajeRecibido;
            public event Action<Dictionary<string, JsonElement>>? OnCierre;
            public event Action? OnDesconectado;

            public void Recibir(Dictionary<string, JsonElement> msg) =>
                OnMensajeRecibido?.Invoke(msg);

            /// <summary>PAT-03: simula que el hilo receptor muere (corte de red).</summary>
            public void Caer() => OnDesconectado?.Invoke();

            /// <summary>PAT-03: el servidor manda CLOSE_NOTICE y cierra.</summary>
            public void CerrarSesion() => OnCierre?.Invoke(Doc(new Dictionary<string, object?>
                { { "tipo", "CLOSE_NOTICE" } }));

            public Task ConectarAsync(string host, int puerto)
            {
                Conexiones++;
                return Task.CompletedTask;
            }
            public void Desconectar() { }
            public void Enviar(Dictionary<string, object?> msg)
            {
                if (Caida) throw new System.IO.IOException("corte simulado");
                Peticiones.Add(new Dictionary<string, object?>(msg));
            }

            public Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg) =>
                Pedir(msg, TimeSpan.FromSeconds(5));

            public Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg,
                TimeSpan timeout)
            {
                if (Caida) throw new IOExceptionShim();
                Peticiones.Add(new Dictionary<string, object?>(msg));
                string tipo = (string?)msg.GetValueOrDefault("tipo") ?? "";
                string? id = msg.GetValueOrDefault("id")?.ToString();
                return Task.FromResult(tipo switch
                {
                    "LOGIN" => Doc(new Dictionary<string, object?>
                        { { "tipo", "LOGIN_RESPUESTA" }, { "id", id }, { "exito", true } }),
                    "MENSAJE_TEXTO" when (string?)msg.GetValueOrDefault("destinatario") == "ZZZ" =>
                        Doc(new Dictionary<string, object?>
                            { { "tipo", "ERROR" }, { "id", id }, { "mensajeError", "destinatario inexistente" } }),
                    "MENSAJE_TEXTO" => Doc(new Dictionary<string, object?>
                        { { "tipo", "ACK" }, { "id", id }, { "exito", true },
                          { "hashSha256", "h" }, { "numCaracteres", 4 }, { "numPalabras", 1 } }),
                    "MENSAJE_IMAGEN" => Doc(new Dictionary<string, object?>
                        { { "tipo", "IMAGE_FILTERED_RESULT" }, { "id", id }, { "exito", true },
                          { "hashSha256", "hi" }, { "etapasFiltrado", "[]" }, { "archivoId", "7" } }),
                    "HISTORIAL_REQ" => Doc(new Dictionary<string, object?>
                        { { "tipo", "HISTORIAL_PAGE" }, { "id", id },
                          { "contenido", HistorialRemotoJson ?? "[]" }, { "totalPaginas", 1 } }),
                    "BROADCAST" => Doc(new Dictionary<string, object?>
                        { { "tipo", "ACK" }, { "id", id }, { "exito", true } }),
                    "KICK" => Doc(new Dictionary<string, object?>
                        { { "tipo", "ACK" }, { "id", id }, { "exito", true } }),
                    "LISTAR_CONECTADOS" => Doc(new Dictionary<string, object?>
                        { { "tipo", "LISTAR_CONECTADOS_RESPUESTA" }, { "id", id },
                          { "usuariosConectados", new[] { "A001", "B002" } } }),
                    _ => Doc(new Dictionary<string, object?>
                        { { "tipo", "ERROR" }, { "mensajeError", "no soportado en falsa" } })
                });
            }

            private static Dictionary<string, JsonElement> Doc(Dictionary<string, object?> obj) =>
                FachadaTests.Doc(obj);

            private sealed class IOExceptionShim : System.IO.IOException
            {
                public IOExceptionShim() : base("corte simulado") { }
            }
        }

        // ---------------------------------------------------------------- PAT-03

        [Fact]
        public async Task EstadoDeConexionAvanzaConectandoYConectado()
        {
            var vistos = new List<EstadoConexion>();
            _f.OnEstadoConexion += e => vistos.Add(e);

            await _f.ConectarAsync();

            Assert.Equal(EstadoConexion.Conectado, _f.EstadoConexion);
            Assert.Equal(EstadoConexion.Conectando, vistos[0]);
        }

        [Fact]
        public void CaidaDelHiloPasaASinRedYEnciendeElBanner()
        {
            // UIX-00a: esto es lo que no hacia nadie antes. El banner ahora se
            // enciende porque el estado es observable.
            _red.Caer();

            Assert.Equal(EstadoConexion.SinRed, _f.EstadoConexion);
            Assert.True(EstadoConexionTexto.MuestraBanner(_f.EstadoConexion));
        }

        [Fact]
        public void CierreDelServidorNoEsCaidaDeRed()
        {
            _red.CerrarSesion();

            Assert.Equal(EstadoConexion.SesionCerrada, _f.EstadoConexion);
        }

        [Fact]
        public void UnCierreVoluntarioNoSeReportaComoCaida()
        {
            _red.CerrarSesion();
            var trasCierre = _f.EstadoConexion;

            _red.Caer();

            Assert.Equal(trasCierre, _f.EstadoConexion);
        }

        [Fact]
        public void LosTextosCoincidenConLosDeLosOtrosClientes()
        {
            // Contrato compartido (VIS-02): el mismo texto en los 3 clientes.
            Assert.Equal("Sin conexión — mensajes en cola local, se reintentan solos",
                EstadoConexionTexto.Texto(EstadoConexion.SinRed));
            Assert.Equal("En línea", EstadoConexionTexto.Texto(EstadoConexion.Conectado));
            Assert.Equal("Conectando…", EstadoConexionTexto.Texto(EstadoConexion.Conectando));
            Assert.Equal("Sesión cerrada", EstadoConexionTexto.Texto(EstadoConexion.SesionCerrada));
        }

        [Fact]
        public void ElBannerSoloSeEnciendeEnEstadosDegradados()
        {
            Assert.False(EstadoConexionTexto.MuestraBanner(EstadoConexion.Conectado));
            Assert.False(EstadoConexionTexto.MuestraBanner(EstadoConexion.Conectando));
            Assert.True(EstadoConexionTexto.MuestraBanner(EstadoConexion.SinRed));
            Assert.True(EstadoConexionTexto.MuestraBanner(EstadoConexion.SesionCerrada));
        }
    }
}
