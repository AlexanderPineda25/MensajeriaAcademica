using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using Mensajeria.Componentes;
using Mensajeria.Fachada.Servicios;
using Mensajeria.Store;
using Xunit;

namespace Mensajeria.Tests
{
    public class ReintentadorPendientesClienteTests
    {
        [Fact]
        public async Task ReintentaEnOrdenUsandoElStoreActivoYConservaLosIds()
        {
            string rutaAnterior = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".db");
            string rutaActual = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".db");
            try
            {
                using var storeAnterior = new LocalStore(rutaAnterior);
                using var storeActual = new LocalStore(rutaActual);
                var historialAnterior = new HistorialLocal(storeAnterior);
                var historialActual = new HistorialLocal(storeActual);
                historialActual.RegistrarPendiente("p-1", "MENSAJE_TEXTO", "A001", "B002",
                    "primero", null, null, "2026-10-08T10:00:00Z");
                historialActual.RegistrarPendiente("p-2", "MENSAJE_TEXTO", "A001", "B002",
                    "segundo", null, null, "2026-10-08T10:01:00Z");

                IHistorialLocal? historialActivo = historialAnterior;
                var coordinador = new CoordinadorMensajeriaCliente(() => historialActivo);
                var reintentador = new ReintentadorPendientesCliente(
                    () => historialActivo, coordinador);
                historialActivo = historialActual;
                var red = new RedCaptura();

                int enviados = await reintentador.ReintentarPendientesAsync(red, "A001");

                Assert.Equal(2, enviados);
                Assert.Equal(new[] { "p-1", "p-2" }, red.Peticiones
                    .Select(p => (string)p["id"]!));
                Assert.Empty(historialActual.ListarPendientes());
                Assert.Empty(historialAnterior.ListarPendientes());
            }
            finally
            {
                BorrarBase(rutaAnterior);
                BorrarBase(rutaActual);
            }
        }

        [Fact]
        public async Task PrimerErrorIncrementaIntentosYDetieneElLote()
        {
            string ruta = Path.Combine(Path.GetTempPath(), Guid.NewGuid() + ".db");
            try
            {
                using var store = new LocalStore(ruta);
                var historial = new HistorialLocal(store);
                historial.RegistrarPendiente("p-1", "MENSAJE_TEXTO", "A001", "B002",
                    "primero", null, null, "2026-10-08T10:00:00Z");
                historial.RegistrarPendiente("p-2", "MENSAJE_TEXTO", "A001", "B002",
                    "segundo", null, null, "2026-10-08T10:01:00Z");
                var coordinador = new CoordinadorMensajeriaCliente(() => historial);
                var reintentador = new ReintentadorPendientesCliente(() => historial, coordinador);
                var red = new RedCaptura { ErrorPedir = new IOException("corte") };

                int enviados = await reintentador.ReintentarPendientesAsync(red, "A001");

                Assert.Equal(0, enviados);
                Assert.Equal("p-1", Assert.Single(red.Peticiones)["id"]);
                var pendientes = historial.ListarPendientes();
                Assert.Equal(2, pendientes.Count);
                Assert.Equal(1L, pendientes[0]["intentos"]);
                Assert.Equal("corte", pendientes[0]["ultimoError"]);
                Assert.Equal(0L, pendientes[1]["intentos"]);
            }
            finally
            {
                BorrarBase(ruta);
            }
        }

        private static void BorrarBase(string ruta)
        {
            try { if (File.Exists(ruta)) File.Delete(ruta); } catch { }
            try { if (File.Exists(ruta + "-wal")) File.Delete(ruta + "-wal"); } catch { }
            try { if (File.Exists(ruta + "-shm")) File.Delete(ruta + "-shm"); } catch { }
        }

        private sealed class RedCaptura : IConexionRed
        {
            public List<Dictionary<string, object?>> Peticiones { get; } = new();
            public Exception? ErrorPedir { get; set; }

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
                Peticiones.Add(new Dictionary<string, object?>(msg));
                if (ErrorPedir is Exception error) throw error;
                return Task.FromResult(Convertir(new Dictionary<string, object?>
                {
                    ["tipo"] = "ACK",
                    ["exito"] = true
                }));
            }

            private static Dictionary<string, JsonElement> Convertir(Dictionary<string, object?> valor)
            {
                using var documento = JsonDocument.Parse(JsonSerializer.Serialize(valor));
                return documento.RootElement.EnumerateObject().ToDictionary(
                    propiedad => propiedad.Name, propiedad => propiedad.Value.Clone());
            }
        }
    }
}
