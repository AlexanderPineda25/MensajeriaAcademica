using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading.Tasks;
using Mensajeria.Componentes;
using Mensajeria.Fachada.Servicios;
using Xunit;

namespace Mensajeria.Tests
{
    public class CoordinadorMensajeriaClienteTests
    {
        [Fact]
        public async Task EnvioTextoGuardaPendienteAntesDeRedYAckMarcaEnviado()
        {
            var orden = new List<string>();
            var historialObsoleto = new HistorialCaptura(new List<string>());
            var historialActual = new HistorialCaptura(orden);
            IHistorialLocal? storeActual = historialObsoleto;
            var coordinador = new CoordinadorMensajeriaCliente(() => storeActual);
            storeActual = historialActual;
            var red = new RedCaptura(orden, new Dictionary<string, object?>
            {
                ["tipo"] = "ACK",
                ["exito"] = true,
                ["hashSha256"] = "hash-1",
                ["numCaracteres"] = 4,
                ["numPalabras"] = 1
            });

            bool enviado = await coordinador.EnviarTextoAsync(red, "A001", "B002", "hola");

            Assert.True(enviado);
            Assert.Equal(new[] { "guardar:PENDIENTE", "red:Pedir", "guardar:ENVIADO" }, orden);
            Assert.Empty(historialObsoleto.Mensajes);
            var fila = Assert.Single(historialActual.Mensajes);
            var solicitud = Assert.Single(red.Peticiones);
            Assert.Equal(fila.Id, solicitud["id"]);
            Assert.Equal("hola", solicitud["contenido"]);
            Assert.Equal("hash-1", fila.Hash);
        }

        [Fact]
        public async Task EnvioTextoSinRedConservaPendienteYDevuelveFalse()
        {
            var orden = new List<string>();
            var historial = new HistorialCaptura(orden);
            var red = new RedCaptura(orden, error: new IOException("sin red"));
            var coordinador = new CoordinadorMensajeriaCliente(() => historial);

            bool enviado = await coordinador.EnviarTextoAsync(red, "A001", "B002", "hola");

            Assert.False(enviado);
            Assert.Equal(new[] { "guardar:PENDIENTE", "red:Pedir", "pendiente" }, orden);
            var mensaje = Assert.Single(historial.Mensajes);
            var pendiente = Assert.Single(historial.Pendientes);
            Assert.Equal("PENDIENTE", mensaje.Estado);
            Assert.Equal(mensaje.Id, pendiente.Id);
            Assert.Equal("hola", pendiente.Contenido);
            Assert.Null(pendiente.Payload);
        }

        [Fact]
        public async Task EnvioImagenPersisteLasEtapasDevueltasEnElAck()
        {
            const string etapas = "[{\"etapa\":\"EscalaGrises\",\"ms\":12,\"ruta\":\"uploads/privado/01.png\"}]";
            var historial = new HistorialCaptura(new List<string>());
            var red = new RedCaptura(new List<string>(), new Dictionary<string, object?>
            {
                ["tipo"] = "IMAGE_FILTERED_RESULT",
                ["exito"] = true,
                ["etapasFiltrado"] = etapas,
                ["archivoId"] = "archivo-1"
            });
            string ruta = Path.GetTempFileName();
            try
            {
                await File.WriteAllBytesAsync(ruta, new byte[] { 1, 2, 3 });
                var coordinador = new CoordinadorMensajeriaCliente(() => historial);

                Assert.True(await coordinador.EnviarImagenAsync(red, "A001", "B002", ruta));

                var mensaje = Assert.Single(historial.Mensajes);
                Assert.Equal(etapas, mensaje.EtapasFiltrado);
                Assert.Equal("archivo-1", mensaje.ArchivoId);
            }
            finally
            {
                File.Delete(ruta);
            }
        }

        private sealed class HistorialCaptura : IHistorialLocal
        {
            private readonly List<string> _orden;
            public List<FilaMensaje> Mensajes { get; } = new();
            public List<Pendiente> Pendientes { get; } = new();

            public HistorialCaptura(List<string> orden) => _orden = orden;

            public void UpsertMensaje(string id, string origen, string destino, string tipo,
                string? contenido, string? hash, int? numCaracteres, int? numPalabras,
                string? nombreArchivo, string? rutaArchivo, long? tamanoArchivo,
                string fechaEnvio, int enviado, int descargado,
                string estado = "ENVIADO", string? archivoId = null,
                string? etapasFiltrado = null)
            {
                _orden.Add($"guardar:{estado}");
                var fila = new FilaMensaje(id, estado, hash, archivoId, etapasFiltrado);
                int existente = Mensajes.FindIndex(mensaje => mensaje.Id == id);
                if (existente >= 0) Mensajes[existente] = fila;
                else Mensajes.Add(fila);
            }

            public void RegistrarPendiente(string id, string tipo, string origen, string destino,
                string? contenido, string? nombreArchivo, byte[]? payload, string fechaCreado)
            {
                _orden.Add("pendiente");
                Pendientes.Add(new Pendiente(id, contenido, payload));
            }

            public List<Dictionary<string, object>> PaginaConversacion(string origen, string destino,
                int limit, int offset) => throw new NotSupportedException();
            public IReadOnlyDictionary<string, string> UltimosMensajesPorContacto(string yo) =>
                new Dictionary<string, string>();
            public List<string> BuscarEnConversacion(string origen, string destino, string patron,
                int limite) => new();
            public string? ContenidoPorId(string idMensaje) => null;
            public int ContarConversacion(string origen, string destino) => 0;
            public void MarcarDescargado(string idMensaje, string rutaArchivo) { }
            public void ActualizarArchivoId(string idMensaje, string archivoId) { }
            public void MarcarEstado(string idMensaje, string estado) { }
            public List<string> IdsNoLeidos(string yo, string otro) => new();
            public IReadOnlyDictionary<string, int> ContarNoLeidosPorContacto(string yo) =>
                new Dictionary<string, int>();
            public void MarcarLeidos(string yo, string otro) { }
            public List<Dictionary<string, object?>> ListarPendientes() => new();
            public void IncrementarIntento(string id, string ultimoError) { }
            public void EliminarPendiente(string id) { }

            public sealed record FilaMensaje(string Id, string Estado, string? Hash,
                string? ArchivoId, string? EtapasFiltrado);
            public sealed record Pendiente(string Id, string? Contenido, byte[]? Payload);
        }

        private sealed class RedCaptura : IConexionRed
        {
            private readonly List<string> _orden;
            private readonly Dictionary<string, object?>? _respuesta;
            private readonly Exception? _error;
            public List<Dictionary<string, object?>> Peticiones { get; } = new();

            public RedCaptura(List<string> orden, Dictionary<string, object?>? respuesta = null,
                Exception? error = null)
            {
                _orden = orden;
                _respuesta = respuesta;
                _error = error;
            }

            public event Action<Dictionary<string, JsonElement>>? OnMensajeRecibido
            { add { } remove { } }
            public event Action<Dictionary<string, JsonElement>>? OnCierre
            { add { } remove { } }
            public event Action? OnDesconectado { add { } remove { } }

            public Task ConectarAsync(string host, int puerto) => Task.CompletedTask;
            public void Desconectar() { }
            public void Enviar(Dictionary<string, object?> msg) => throw new NotSupportedException();
            public Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg) =>
                Pedir(msg, TimeSpan.FromSeconds(1));

            public Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg,
                TimeSpan timeout)
            {
                _orden.Add("red:Pedir");
                Peticiones.Add(new Dictionary<string, object?>(msg));
                if (_error != null) throw _error;
                return Task.FromResult(Convertir(_respuesta!));
            }

            private static Dictionary<string, JsonElement> Convertir(Dictionary<string, object?> valor)
            {
                using var documento = JsonDocument.Parse(JsonSerializer.Serialize(valor));
                var resultado = new Dictionary<string, JsonElement>();
                foreach (var propiedad in documento.RootElement.EnumerateObject())
                    resultado[propiedad.Name] = propiedad.Value.Clone();
                return resultado;
            }
        }
    }
}
