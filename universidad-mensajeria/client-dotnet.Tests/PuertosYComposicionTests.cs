using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using Mensajeria.Componentes;
using Mensajeria.Composicion;
using Mensajeria.Fachada;
using Mensajeria.Transversal.Dtos;
using Xunit;

namespace Mensajeria.Tests
{
    public class PuertosYComposicionTests
    {
        [Fact]
        public void TiposDeTramaCoincidenConElContratoCompartido()
        {
            string ruta = Path.Combine(AppContext.BaseDirectory, "contrato", "puertos.json");
            using var contrato = JsonDocument.Parse(File.ReadAllText(ruta));
            var enumContrato = contrato.RootElement.GetProperty("enums").EnumerateArray()
                .Single(e => e.GetProperty("nombre").GetString() == "TipoMensajeCliente");
            string[] esperados = enumContrato.GetProperty("valores").EnumerateArray()
                .Select(v => v.GetString()!).ToArray();

            Assert.Equal(esperados, Enum.GetNames<TipoMensajeCliente>());
        }

        [Fact]
        public void RespuestaRedRespetaLosVeinticincoComponentesCanonicos()
        {
            int componentes = typeof(RespuestaRed).GetConstructors()
                .Single().GetParameters().Length;

            Assert.Equal(25, componentes);
        }

        [Fact]
        public void ReceptorMensajesExponeLosCuatroCallbacksDelContrato()
        {
            string[] callbacks = typeof(IReceptorMensajes).GetMethods()
                .Select(m => m.Name).OrderBy(n => n, StringComparer.Ordinal).ToArray();

            Assert.Equal(new[] { "AlCierre", "AlDesconexion", "AlError", "AlRecibir" }, callbacks);
        }

        [Fact]
        public void ConexionRedRegistraElReceptorYEntregaLosCuatroEventos()
        {
            var conexion = new ConexionFalsa();
            var receptor = new ReceptorCaptura();

            ((IConexionRed)conexion).Suscribir(receptor);
            conexion.EmitirMensaje(new Dictionary<string, object?>
                { ["tipo"] = "MENSAJE_TEXTO", ["contenido"] = "hola" });
            conexion.EmitirCierre(new Dictionary<string, object?>
                { ["tipo"] = "CLOSE_NOTICE" });
            conexion.EmitirMensaje(new Dictionary<string, object?>
                { ["tipo"] = "TIPO_FUTURO" });
            conexion.EmitirDesconexion();

            Assert.Equal(TipoMensajeCliente.MENSAJE_TEXTO, Assert.Single(receptor.Mensajes).Tipo);
            Assert.Equal("hola", receptor.Mensajes[0].Contenido);
            Assert.Equal(TipoMensajeCliente.CLOSE_NOTICE, Assert.Single(receptor.Cierres).Tipo);
            Assert.Contains("tipo desconocido", Assert.Single(receptor.Errores));
            Assert.Equal(1, receptor.Desconexiones);
        }

        [Fact]
        public void MapeadorDirectorioCreaRecordsNeutralesYOmiteFilasSinCodigo()
        {
            var mapeador = new MapeadorDatosServidor();
            var usuarios = mapeador.MapearDirectorio(
                "[{\"codigo\":\"A001\",\"nombres\":\"Ana\",\"apellidos\":\"Ruiz\","
                + "\"programa\":\"Sistemas\",\"conectado\":true},"
                + "{\"codigo\":\"\",\"nombres\":\"Sin código\"}]");

            var usuario = Assert.Single(usuarios);
            Assert.Equal("A001", usuario.Codigo);
            Assert.Equal("Ana", usuario.Nombres);
            Assert.True(usuario.Conectado);
        }

        [Fact]
        public void MapeadorHistorialAdaptaTextoYAdjuntosConEstadoDeSesion()
        {
            var mapeador = new MapeadorDatosServidor();
            const string json = "[{\"id\":\"m1\",\"remitente\":\"A001\","
                + "\"destinatario\":\"B002\",\"tipo\":\"MENSAJE_TEXTO\","
                + "\"contenido\":\"hola\",\"fechaEnvio\":\"2026-10-08T10:00:00Z\"},"
                + "{\"id\":\"m2\",\"remitente\":\"B002\","
                + "\"destinatario\":\"A001\",\"tipo\":\"MENSAJE_ARCHIVO\","
                + "\"nombreArchivo\":\"informe.pdf\"}]";

            var mensajes = mapeador.MapearPaginaHistorial(json, "A001");

            Assert.Equal(2, mensajes.Count);
            Assert.True(mensajes[0].Enviado);
            Assert.Equal("ENVIADO", mensajes[0].Estado);
            Assert.False(mensajes[1].Enviado);
            Assert.Equal("ENTREGADO", mensajes[1].Estado);
            Assert.Equal("[Archivo: informe.pdf]", mensajes[1].Contenido);
        }

        [Fact]
        public void MapeadorHistorialConservaEtapasFiltradoDeImagen()
        {
            const string etapas = "[{\"etapa\":\"Sepia\",\"ms\":7,\"ruta\":\"privada/02.png\"}]";
            string json = "[{\"id\":\"m-img\",\"remitente\":\"B002\","
                + "\"destinatario\":\"A001\",\"tipo\":\"MENSAJE_IMAGEN\","
                + "\"fechaEnvio\":\"2026-10-08T10:00:00Z\","
                + "\"etapasFiltrado\":" + System.Text.Json.JsonSerializer.Serialize(etapas) + "}]";

            var mensaje = Assert.Single(new MapeadorDatosServidor()
                .MapearPaginaHistorial(json, "A001"));

            Assert.Equal(etapas, mensaje.EtapasFiltrado);
        }

        [Fact]
        public void MapeadorRechazaTipoDeMensajeDesconocidoSinAproximarlo()
        {
            var mapeador = new MapeadorDatosServidor();

            Assert.Throws<ArgumentException>(() => mapeador.NormalizarTipoMensaje("LOGIN_RESPUESTA "));
            Assert.Equal("MENSAJE_TEXTO", mapeador.NormalizarTipoMensaje("MENSAJE_TEXTO"));
        }

        [Fact]
        public void FabricaEsElPuntoDeEntradaDeLaFachadaDeProduccion()
        {
            IFachadaCliente fachada = FabricaCliente.CrearFachada();

            Assert.IsType<FachadaCliente>(fachada);
        }

        private sealed class ReceptorCaptura : IReceptorMensajes
        {
            public List<RespuestaRed> Mensajes { get; } = new();
            public List<RespuestaRed> Cierres { get; } = new();
            public List<string> Errores { get; } = new();
            public int Desconexiones { get; private set; }

            public void AlRecibir(RespuestaRed mensaje) => Mensajes.Add(mensaje);
            public void AlCierre(RespuestaRed aviso) => Cierres.Add(aviso);
            public void AlError(string motivo) => Errores.Add(motivo);
            public void AlDesconexion() => Desconexiones++;
        }

        private sealed class ConexionFalsa : IConexionRed
        {
            public event Action<Dictionary<string, JsonElement>>? OnMensajeRecibido;
            public event Action<Dictionary<string, JsonElement>>? OnCierre;
            public event Action? OnDesconectado;

            public void EmitirMensaje(Dictionary<string, object?> mensaje) =>
                OnMensajeRecibido?.Invoke(Convertir(mensaje));

            public void EmitirCierre(Dictionary<string, object?> aviso) =>
                OnCierre?.Invoke(Convertir(aviso));

            public void EmitirDesconexion() => OnDesconectado?.Invoke();

            public Task ConectarAsync(string host, int puerto) => Task.CompletedTask;
            public void Desconectar() { }
            public void Enviar(Dictionary<string, object?> msg) { }
            public Task<Dictionary<string, JsonElement>> Pedir(Dictionary<string, object?> msg) =>
                throw new NotSupportedException();
            public Task<Dictionary<string, JsonElement>> Pedir(
                Dictionary<string, object?> msg, TimeSpan timeout) => throw new NotSupportedException();

            private static Dictionary<string, JsonElement> Convertir(Dictionary<string, object?> valor)
            {
                using var documento = JsonDocument.Parse(JsonSerializer.Serialize(valor));
                return documento.RootElement.EnumerateObject().ToDictionary(
                    propiedad => propiedad.Name, propiedad => propiedad.Value.Clone());
            }
        }
    }
}
