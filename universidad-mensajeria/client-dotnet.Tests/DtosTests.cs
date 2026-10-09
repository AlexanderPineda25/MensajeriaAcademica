using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using Mensajeria.Transversal.Dtos;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// Fase 1.4: los DTO tipados del contrato (<c>contrato/puertos.json</c> §records).
    /// Un test por record con ida y vuelta y casos límite. Estos tipos son la base de
    /// la Fase 2; hoy son aditivos y <see cref="Mensajeria.Fachada.IFachadaCliente"/>
    /// sigue hablando con <c>Dictionary&lt;string, JsonElement&gt;</c>.
    /// </summary>
    public class DtosTests
    {
        private static readonly JsonSerializerOptions Opciones = new()
        {
            // Igual que el TramaCodec: los nulos no se serializan.
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
        };

        private static T IdaYVuelta<T>(T original)
        {
            string json = JsonSerializer.Serialize(original, Opciones);
            return JsonSerializer.Deserialize<T>(json, Opciones)!;
        }

        // ------------------------------------------------------- MensajeLocal (17)

        [Fact]
        public void MensajeLocalIdaYVueltaConservaLosDiecisieteComponentes()
        {
            var original = new MensajeLocal(
                "m-1", "A001", "B002", "MENSAJE_TEXTO", "Hola", "abc123",
                4, 1, null, null, null, "2026-10-08T10:00:00",
                true, false, "ENTREGADO", "ar-1", "[{\"etapa\":\"Sepia\",\"ms\":8}]");

            var copia = IdaYVuelta(original);

            Assert.Equal(original, copia);
            Assert.Equal(17, Componentes<MensajeLocal>().Count);
            Assert.Equal("Hola", copia.Contenido);
            Assert.Equal(1, copia.NumPalabras);
            Assert.Equal("ENTREGADO", copia.Estado);
            Assert.Equal(original.EtapasFiltrado, copia.EtapasFiltrado);
        }

        [Fact]
        public void MensajeLocalAceptaTodosLosNulosDelUnicoCampoNoNumerico()
        {
            // Una fila recién creada: todavía no hay hash, ni nombre de archivo, ni
            // tamaño. Los numéricos son null, NO 0: 0 significa "medido y vacío".
            var vacio = new MensajeLocal(null, null, null, null, null, null,
                null, null, null, null, null, null, false, false, null, null);

            Assert.Equal(vacio, IdaYVuelta(vacio));
            Assert.Null(IdaYVuelta(vacio).NumCaracteres);
            Assert.Null(IdaYVuelta(vacio).TamanoArchivo);
            Assert.Null(IdaYVuelta(vacio).Contenido);
        }

        [Fact]
        public void MensajeLocalMarcaLaDifusionPropiaConDestinoEstrella()
        {
            var difusion = new MensajeLocal("m-9", "A001", "*", "BROADCAST", "aviso", null,
                5, 1, null, null, null, "2026-10-08T10:00:00", true, false, "ENTREGADO", null);

            Assert.Equal("*", IdaYVuelta(difusion).Destino);
        }

        [Fact]
        public void MensajeLocalEsIgualPorValor()
        {
            var a = new MensajeLocal("m-1", "A001", "B002", "MENSAJE_TEXTO", "Hola", null,
                4, 1, null, null, null, null, true, false, "ENVIADO", null);
            var b = a with { };

            Assert.Equal(a, b);
            Assert.NotEqual(a, a with { Contenido = "Adios" });
            Assert.Equal(a.GetHashCode(), b.GetHashCode());
        }

        // -------------------------------------------------------- RecienteChat (6)

        [Fact]
        public void RecienteChatIdaYVuelta()
        {
            var original = new RecienteChat("B002", "Hola", "MENSAJE_TEXTO",
                "2026-10-08T09:59:00", 3, true);

            Assert.Equal(original, IdaYVuelta(original));
            Assert.Equal(6, Componentes<RecienteChat>().Count);
            Assert.Equal(3, IdaYVuelta(original).NoLeidos);
            Assert.True(IdaYVuelta(original).Conectado);
        }

        [Fact]
        public void RecienteChatCasoLimiteSinMensajes()
        {
            // Contacto recién agregado: nunca escribió, no hay fecha ni contenido.
            var sinHistorial = new RecienteChat("C003", null, null, null, 0, false);

            var copia = IdaYVuelta(sinHistorial);

            Assert.Equal(sinHistorial, copia);
            Assert.Null(copia.UltimoContenido);
            Assert.Equal(0, copia.NoLeidos);
            Assert.False(copia.Conectado);
        }

        // ------------------------------------------------------- PendienteEnvio (10)

        [Fact]
        public void PendienteEnvioIdaYVuelta()
        {
            var original = new PendienteEnvio("p-1", "MENSAJE_TEXTO", "A001", "B002",
                "Hola", null, new byte[] { 1, 2, 3 }, "2026-10-08T10:00:00", 2, "sin red");

            var copia = IdaYVuelta(original);

            Assert.Equal(original.Id, copia.Id);
            Assert.Equal(original.Tipo, copia.Tipo);
            Assert.Equal(original.Origen, copia.Origen);
            Assert.Equal(original.Destino, copia.Destino);
            Assert.Equal(original.Contenido, copia.Contenido);
            Assert.Equal(original.NombreArchivo, copia.NombreArchivo);
            Assert.Equal(original.FechaCreado, copia.FechaCreado);
            Assert.Equal(original.Intentos, copia.Intentos);
            Assert.Equal(original.UltimoError, copia.UltimoError);
            // El payload se compara por contenido: record + byte[] es igualdad por
            // referencia, igual que en Java, así que Assert.Equal es el overload de
            // colección (compara elemento a elemento).
            Assert.Equal(original.Payload, copia.Payload);
        }

        [Fact]
        public void PendienteEnvioGuardaElPayloadEnBytesCrudosYNoEnBase64()
        {
            // AgenteBytes: es lo que permite re-fragmentar en el reintento
            // (AGENTS.md invariante 2). Con Base64 en un string no se puede cortar.
            byte[] original = new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x00, 0xFF, 0x7F, 0x80 };
            var pendiente = new PendienteEnvio("p-2", "MENSAJE_ARCHIVO", "A001", "B002",
                "[Archivo]", "foto.png", original, "2026-10-08T10:00:00", 0, null);

            Assert.IsType<byte[]>(pendiente.Payload);
            Assert.Equal(original, IdaYVuelta(pendiente).Payload);
            // Todos los 256 valores de byte sobreviven al viaje.
            byte[] todos = Enumerable.Range(0, 256).Select(i => (byte)i).ToArray();
            var completo = pendiente with { Payload = todos };
            Assert.Equal(todos, IdaYVuelta(completo).Payload);
        }

        [Fact]
        public void PendienteEnvioCasoLimiteSinPayload()
        {
            // Un texto pendiente no lleva archivo: payload nulo, no vacío.
            var texto = new PendienteEnvio("p-3", "MENSAJE_TEXTO", "A001", "B002",
                "Hola", null, null, "2026-10-08T10:00:00", 1, "timeout");

            var copia = IdaYVuelta(texto);

            Assert.Null(copia.Payload);
            Assert.Equal(10, Componentes<PendienteEnvio>().Count);
            Assert.Equal(texto.Id, copia.Id);
        }

        [Fact]
        public void PendienteEnvioPayloadVacioNoEsNulo()
        {
            // cero bytes != sin bytes: el reintento debe poder distinguirlo.
            var vacio = new PendienteEnvio("p-4", "MENSAJE_ARCHIVO", "A001", "B002",
                "[Archivo]", "vacio.bin", Array.Empty<byte>(), "2026-10-08T10:00:00", 0, null);

            byte[] copia = IdaYVuelta(vacio).Payload!;

            Assert.NotNull(copia);
            Assert.Empty(copia);
        }

        // --------------------------------------------------------- UsuarioLocal (7)

        [Fact]
        public void UsuarioLocalIdaYVuelta()
        {
            var original = new UsuarioLocal("A001", "Ana", "Ruiz", "Ingenieria de Sistemas",
                true, "2026-01-15T08:00:00", "2026-10-08T10:00:00");

            var copia = IdaYVuelta(original);

            Assert.Equal(original, copia);
            Assert.Equal(7, Componentes<UsuarioLocal>().Count);
            Assert.True(copia.Conectado);
        }

        [Fact]
        public void UsuarioLocalCasoLimiteSinApellidos()
        {
            // El alta es por CSV: nombres y apellidos no son obligatorios.
            var minimo = new UsuarioLocal("B002", "Luis", null, null, false, null, null);

            var copia = IdaYVuelta(minimo);

            Assert.Equal(minimo, copia);
            Assert.Null(copia.Apellidos);
            Assert.Null(copia.Programa);
        }

        // ------------------------------------------------ PaginaHistorialRemota (3)

        [Fact]
        public void PaginaHistorialRemotaIdaYVuelta()
        {
            var original = new PaginaHistorialRemota(20, 2, 5);

            Assert.Equal(original, IdaYVuelta(original));
            Assert.Equal(3, Componentes<PaginaHistorialRemota>().Count);
        }

        [Fact]
        public void PaginaHistorialRemotaCasoLimiteSinMasHistorial()
        {
            // "No hay más" NO es una página vacía por error: es la respuesta válida.
            var fin = new PaginaHistorialRemota(0, 1, 0);

            var copia = IdaYVuelta(fin);

            Assert.Equal(fin, copia);
            Assert.Equal(0, copia.TotalPaginas);
        }

        // ------------------------------------------------------ EventoCliente (3+1)

        [Fact]
        public void EventoClienteIdaYVueltaYLosCincoTipos()
        {
            var original = new EventoCliente(EventoCliente.TipoEvento.ACUSE_LECTURA, "B002", "m-1");

            var copia = IdaYVuelta(original);

            Assert.Equal(original, copia);
            Assert.Equal(3, Componentes<EventoCliente>().Count);
            Assert.Equal(5, Enum.GetValues<EventoCliente.TipoEvento>().Length);
            Assert.Equal("ACUSE_LECTURA", copia.Tipo.ToString());
        }

        [Fact]
        public void EventoClienteEmparejaPorNombreYNoPorOrdinal()
        {
            // Reordenar el enum no debe romper nada: el emparejamiento es por nombre.
            string[] contrato = { "PRESENCIA", "SESION_CERRADA", "ACUSE_ENTREGA", "ACUSE_LECTURA", "MENSAJE" };

            Assert.Equal(contrato.OrderBy(x => x, StringComparer.Ordinal),
                Enum.GetNames<EventoCliente.TipoEvento>().OrderBy(x => x, StringComparer.Ordinal));

            foreach (string nombre in contrato)
                Assert.Equal(nombre, Enum.Parse<EventoCliente.TipoEvento>(nombre).ToString());
        }

        [Fact]
        public void EventoClienteCasoLimiteSinRemitente()
        {
            // Un cierre de sesión no trae remitente.
            var cierre = new EventoCliente(EventoCliente.TipoEvento.SESION_CERRADA, null, null);

            var copia = IdaYVuelta(cierre);

            Assert.Null(copia.Remitente);
            Assert.Null(copia.Destinatario);
            Assert.Equal(EventoCliente.TipoEvento.SESION_CERRADA, copia.Tipo);
        }

        // ---------------------------------------------------------------- apoyo

        /// <summary>
        /// Cuenta los componentes positionales: si alguien añade o quita uno sin
        /// actualizar el contrato, salta aquí en vez de en la base de datos.
        /// </summary>
        private static IReadOnlyCollection<string> Componentes<T>()
        {
            var constructor = typeof(T).GetConstructors()
                .OrderByDescending(c => c.GetParameters().Length)
                .First();
            return constructor.GetParameters().Select(p => p.Name!).ToList();
        }
    }
}
