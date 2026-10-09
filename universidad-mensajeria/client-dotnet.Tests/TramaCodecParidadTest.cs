using System;
using System.Collections.Concurrent;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using Mensajeria.Componentes;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// Fase 1.6: los vectores de <c>contrato/framing.json</c> codificados contra el
    /// framing real del cliente .NET. El mismo fichero alimenta el test de los tres
    /// clientes, así que una deriva de framing en cualquiera de ellos sale aquí.
    ///
    /// <para><b>POR QUÉ se compara contra <c>trama_hex</c> y no solo ida y vuelta:</b>
    /// una ida y vuelta con el mismo bug se cancela y no detecta nada. El bug
    /// disfrazado de este protocolo es medir CARACTERES en vez de BYTES en la
    /// cabecera: la ida y vuelta pasa igual de verde con la cabecera 3 bytes corta.</para>
    /// </summary>
    public class TramaCodecParidadTest
    {
        private const string ClaveTipo = "tipo";
        private const string TopeTramaDelContrato = "16777216";
        private const int LongitudDeCabecera = 4;

        public static TheoryData<string, string, int, string> Vectores() => new()
        {
            { "ack_minimo", "{\"tipo\":\"ACK\"}", 14, "0000000e7b227469706f223a2241434b227d" },
            { "logout_minimo", "{\"tipo\":\"LOGOUT\"}", 17, "000000117b227469706f223a224c4f474f5554227d" },
            { "error_minimo", "{\"tipo\":\"ERROR\"}", 16, "000000107b227469706f223a224552524f52227d" },
            {
                "login_real", "{\"tipo\":\"LOGIN\",\"id\":\"v-1\",\"codigo\":\"A001\",\"contrasena\":\"Secreta123\"}",
                69,
                "000000457b227469706f223a224c4f47494e222c226964223a22762d31222c22636f6469676f223a2241303031222c22636f6e74726173656e61223a2253656372657461313233227d"
            },
            {
                "mensaje_texto",
                "{\"tipo\":\"MENSAJE_TEXTO\",\"id\":\"m-1\",\"remitente\":\"A001\",\"destinatario\":\"B002\",\"contenido\":\"Hola\"}",
                95,
                "0000005f7b227469706f223a224d454e53414a455f544558544f222c226964223a226d2d31222c2272656d6974656e7465223a2241303031222c2264657374696e61746172696f223a2242303032222c22636f6e74656e69646f223a22486f6c61227d"
            },
            // EL CASO CRÍTICO: 53 caracteres, 56 BYTES. Medir caracteres escribe 0x35.
            {
                "multibyte_acentos", "{\"tipo\":\"MENSAJE_TEXTO\",\"contenido\":\"Señor café año\"}",
                56,
                "000000387b227469706f223a224d454e53414a455f544558544f222c22636f6e74656e69646f223a225365c3b16f7220636166c3a92061c3b16f227d"
            }
        };

        public static TheoryData<string, string> Rechazos() => new()
        {
            { "longitud_cero", "00000000" },
            { "longitud_sobre_el_tope", "02000000" },
            { "trama_incompleta", "000000ff4142" },
            { "sin_tipo", "000000117b22746578746f223a22786f736861227d" },
            { "json_roto", "000000057b226e" }
        };

        // ------------------------------------------------------------- vectores

        [Theory]
        [MemberData(nameof(Vectores))]
        public void EscribirProduceLosBytesExactosDelVector(string nombre, string payload,
            int longitud, string tramaHex)
        {
            byte[] esperado = Convert.FromHexString(tramaHex);
            byte[] producido = SerializarComoTrama(DeJsonAPartes(payload));

            Assert.Equal(esperado, producido);
            // La cabecera es la longitud en BYTES, escrita big-endian.
            Assert.Equal(longitud, LeerInt32BigEndian(producido, 0));
            Assert.Equal(longitud, Encoding.UTF8.GetByteCount(payload));
            Assert.True(producido.Length == longitud + LongitudDeCabecera,
                $"{nombre}: deberian ser 4 de cabecera + {longitud} de payload");
        }

        [Theory]
        [MemberData(nameof(Vectores))]
        public void LeerDesmontaLosBytesExactosDelVector(string nombre, string payload,
            int longitud, string tramaHex)
        {
            byte[] trama = Convert.FromHexString(tramaHex);
            Assert.Equal(longitud, trama.Length - LongitudDeCabecera);

            var leido = TramaCodec.Leer(new MemoryStream(trama));
            var campos = CamposDe(payload);

            Assert.True(leido.Count == campos.Count, $"{nombre}: la trama perdio claves");
            Assert.Equal(campos[ClaveTipo], leido[ClaveTipo].GetString());
        }

        [Theory]
        [MemberData(nameof(Vectores))]
        public void IdaYVueltaConservaTodosLosCampos(string nombre, string payload,
            int longitud, string tramaHex)
        {
            // Ida y vuelta por los BYTES DEL CONTRATO, no por los que produce el
            // cliente: reserializando, un bug se cancelaría consigo mismo.
            byte[] trama = Convert.FromHexString(tramaHex);
            Assert.Equal(longitud, trama.Length - LongitudDeCabecera);

            var partes = DeJsonAPartes(payload);
            var leido = TramaCodec.Leer(new MemoryStream(trama));

            Assert.True(leido.Count == partes.Count, $"{nombre}: la trama perdio claves");
            foreach (var parte in partes)
                Assert.Equal(parte.Value, leido[parte.Key].GetString());
        }

        // ---------------------------------------------- EL CASO QUE MATA (acentos)

        /// <summary>
        /// 53 caracteres, 56 bytes. Si alguien mide <c>payload.Length</c> en vez de
        /// <c>Encoding.UTF8.GetByteCount</c>, la cabecera queda 3 bytes corta, el lector
        /// se descuadra y se pierde TODO lo que venga después.
        /// </summary>
        [Fact]
        public void MultibyteAcentosMideBytesYNoCaracteres()
        {
            const string payload = "{\"tipo\":\"MENSAJE_TEXTO\",\"contenido\":\"Señor café año\"}";

            Assert.Equal(53, payload.Length);                     // caracteres
            Assert.Equal(56, Encoding.UTF8.GetByteCount(payload)); // bytes: lo que va en la cabecera
            Assert.NotEqual(payload.Length, Encoding.UTF8.GetByteCount(payload));

            byte[] trama = SerializarComoTrama(DeJsonAPartes(payload));
            Assert.Equal(new byte[] { 0x00, 0x00, 0x00, 0x38 }, trama.Take(4).ToArray());
            // El payload va en UTF-8 CRUDO: "ñ" son 2 bytes (c3 b1), no los 6 del
            // escape \uXXXX. Gson (Java) y ensure_ascii=False (Python) escriben
            // exactamente esto, así que escaparlo rompería la paridad de bytes.
            Assert.Contains(new byte[] { 0xc3, 0xb1 }, trama);
            Assert.DoesNotContain(System.Text.Encoding.ASCII.GetBytes("\\u"), trama);
            Assert.Equal(Convert.FromHexString(
                    "000000387b227469706f223a224d454e53414a455f544558544f222c22636f6e74656e69646f223a225365c3b16f7220636166c3a92061c3b16f227d"),
                trama);
        }

        /// <summary>
        /// El mismo bug, demostrado: la cabecera escrita con el número de caracteres
        /// NO es la del contrato y descuadra el stream entero.
        /// </summary>
        [Fact]
        public void CabeceraEnCaracteresDifiereYDescuadraElStream()
        {
            const string payload = "{\"tipo\":\"MENSAJE_TEXTO\",\"contenido\":\"Señor café año\"}";
            int enBytes = Encoding.UTF8.GetByteCount(payload);
            int enCaracteres = payload.Length;

            Assert.Equal(56, enBytes);
            Assert.Equal(53, enCaracteres);
            Assert.Equal(enBytes, LeerInt32BigEndian(CabeceraBigEndian(enBytes), 0));
            Assert.Equal(enCaracteres, LeerInt32BigEndian(CabeceraBigEndian(enCaracteres), 0));

            // Con la cabecera corta, el lector intenta leer 53 bytes de un payload de
            // 56 y se come 3 bytes de lo que venga después. El error sale (JSON roto)
            // en vez de tragarse la trama entera, pero el descuadre es real: por eso
            // la longitud tiene que medirse en BYTES.
            byte[] stream = CabeceraBigEndian(enCaracteres)
                .Concat(Encoding.UTF8.GetBytes(payload))
                .ToArray();
            Assert.ThrowsAny<Exception>(() => TramaCodec.Leer(new MemoryStream(stream)));
        }

        [Fact]
        public void TopeDeTramaEsElDelContrato()
        {
            Assert.Equal(TramaCodec.TamanoMaximoTrama, int.Parse(TopeTramaDelContrato));
        }

        // ------------------------------------------------------------- rechazos

        [Theory]
        [MemberData(nameof(Rechazos))]
        public void RechazaLasTramasQueElContratoDeclaraInvalidas(string nombre, string tramaHex)
        {
            var ex = Assert.ThrowsAny<Exception>(() =>
                TramaCodec.Leer(new MemoryStream(Convert.FromHexString(tramaHex))));
            Assert.False(string.IsNullOrWhiteSpace(ex.Message), nombre);
        }

        [Fact]
        public void LongitudCeroSeRechaza()
        {
            var ex = Assert.Throws<InvalidOperationException>(() =>
                TramaCodec.Leer(new MemoryStream(Convert.FromHexString("00000000"))));
            Assert.Contains("0", ex.Message);
        }

        [Fact]
        public void LongitudSobreElTopeSeRechazaSinReservar32MiB()
        {
            // 0x02000000 = 32 MiB. Un atacante (o un bug) que anuncie eso no debe
            // hacer que el cliente reserve esa memoria antes de comprobar el tope.
            var ex = Assert.Throws<InvalidOperationException>(() =>
                TramaCodec.Leer(new MemoryStream(Convert.FromHexString("02000000"))));
            Assert.Contains("33554432", ex.Message);
        }

        [Fact]
        public void TramaIncompletaSeRechaza()
        {
            var ex = Assert.Throws<IOException>(() =>
                TramaCodec.Leer(new MemoryStream(Convert.FromHexString("000000ff4142"))));
            Assert.Contains("cerrada", ex.Message);
        }

        [Fact]
        public void TramaSinTipoSeRechaza()
        {
            // "tipo" es obligatoria en el contrato: sin ella la trama no es un mensaje
            // y el demultiplexor la trataría como respuesta huérfana.
            var ex = Assert.Throws<InvalidOperationException>(() =>
                TramaCodec.Leer(new MemoryStream(
                    Convert.FromHexString("000000117b22746578746f223a22786f736861227d"))));
            Assert.Contains(ClaveTipo, ex.Message);
        }

        [Fact]
        public void JsonRotoSeRechaza()
        {
            // Anuncia 5 bytes y solo llegan 3: falla en la lectura exacta, que es
            // justo lo que evita seguir interpretando basura como cabecera.
            Assert.ThrowsAny<Exception>(() => TramaCodec.Leer(new MemoryStream(
                Convert.FromHexString("000000057b226e"))));
        }

        // ------------------------------------------------ el contrato no se evade

        /// <summary>
        /// Guarda contra que alguien edite los vectores de esta clase en vez del
        /// contrato: se comparan con el fichero único.
        /// </summary>
        [Fact]
        public void ElContratoCubreLosMismosVectores()
        {
            string ruta = RutaContrato();
            Assert.True(File.Exists(ruta), $"No se encuentra contrato/framing.json en {ruta}");

            using var doc = JsonDocument.Parse(File.ReadAllText(ruta));
            var vectores = doc.RootElement.GetProperty("vectores").EnumerateArray().ToList();
            var mios = ((IEnumerable<object[]>)Vectores()).ToList();

            Assert.Equal(vectores.Count, mios.Count);
            foreach (var elemento in vectores)
            {
                string nombre = elemento.GetProperty("nombre").GetString()!;
                object[] mio = mios.Single(v => (string)v[0] == nombre);
                Assert.Equal(elemento.GetProperty("payload").GetString(), (string)mio[1]!);
                Assert.Equal(elemento.GetProperty("longitud").GetInt32(), (int)mio[2]);
                Assert.Equal(elemento.GetProperty("trama_hex").GetString(), (string)mio[3]!);
            }

            var rechazos = doc.RootElement.GetProperty("rechazos").EnumerateArray().ToList();
            var miosRechazos = ((IEnumerable<object[]>)Rechazos()).ToList();

            Assert.Equal(rechazos.Count, miosRechazos.Count);
            foreach (var elemento in rechazos)
            {
                string nombre = elemento.GetProperty("nombre").GetString()!;
                object[] mio = miosRechazos.Single(r => (string)r[0] == nombre);
                Assert.Equal(elemento.GetProperty("trama_hex").GetString(), (string)mio[1]!);
            }
        }

        // ------------------------------------------------------------------ TLS

        /// <summary>
        /// El framing es el MISMO sobre el socket claro y sobre el cifrado: TLS
        /// envuelve el <see cref="Stream"/>, no cambia ni la cabecera ni el payload
        /// (<c>contrato/framing.json</c> §<c>nota_tls</c>). Por eso el mismo vector
        /// vale para los dos casos; aun así se repite aquí contra un handshake real,
        /// porque es lo que demuestra que <see cref="TramaCodec"/> no depende del tipo
        /// de stream en ninguna de sus dos direcciones.
        /// </summary>
        [Fact]
        public async Task LosSeisVectoresSobreTlsProducenLosMismosBytes()
        {
            using var ca = ValidarTlsTests.EmitirCertificado("CN=localhost, O=Universidad");
            using var servidor = new ServidorTls(ca);

            foreach (object[] vector in Vectores())
            {
                string payload = (string)vector[1];
                byte[] esperado = Convert.FromHexString((string)vector[3]);

                using var cliente = new TcpClient();
                cliente.Connect(IPAddress.Loopback, servidor.Puerto);
                using var tls = new SslStream(cliente.GetStream(), false,
                    ValidarTls.CrearValidador(servidor.RutaPem, "localhost"));
                try
                {
                    await tls.AuthenticateAsClientAsync("localhost");
                }
                catch (Exception ex)
                {
                    // El hilo del servidor se come sus errores; sin esto el sintoma
                    // sería un "EOF inesperado" que no dice de dónde vino.
                    throw new InvalidOperationException(
                        $"El handshake TLS fallo: {ex.Message} | servidor: {servidor.UltimoError}", ex);
                }
                Assert.True(tls.IsAuthenticated);

                TramaCodec.Escribir(tls, DeJsonAPartes(payload));

                // Dirección escritura: lo que llegó por el socket cifrado es
                // exactamente la trama_hex del contrato, byte a byte.
                Assert.Equal(esperado, servidor.LeerTrama());

                // Dirección lectura: el eco del servidor lo desmonta el mismo codec.
                Assert.Equal(ValorDeCampo(payload, ClaveTipo),
                    TramaCodec.Leer(tls)[ClaveTipo].GetString());
            }
        }

        // --------------------------------------------------------------- apoyo

        /// <summary>
        /// Convierte el payload del vector en las partes que irían en un
        /// <c>Dictionary</c>, respetando el ORDEN del texto: el orden de claves es
        /// irrelevante para el protocolo, pero hace falta para comparar bytes.
        /// </summary>
        private static System.Collections.Generic.Dictionary<string, object?> DeJsonAPartes(string payload)
        {
            using var doc = JsonDocument.Parse(payload);
            var partes = new System.Collections.Generic.Dictionary<string, object?>();
            foreach (var propiedad in doc.RootElement.EnumerateObject())
                partes[propiedad.Name] = propiedad.Value.GetString();
            return partes;
        }

        private static System.Collections.Generic.Dictionary<string, string> CamposDe(string payload)
        {
            using var doc = JsonDocument.Parse(payload);
            return doc.RootElement.EnumerateObject()
                .ToDictionary(p => p.Name, p => p.Value.GetString() ?? "");
        }

        private static string ValorDeCampo(string payload, string campo)
            => CamposDe(payload)[campo];

        private static byte[] SerializarComoTrama(System.Collections.Generic.Dictionary<string, object?> mensaje)
        {
            using var buffer = new MemoryStream();
            TramaCodec.Escribir(buffer, mensaje);
            return buffer.ToArray();
        }

        private static byte[] CabeceraBigEndian(int longitud)
        {
            byte[] cabecera = BitConverter.GetBytes(longitud);
            if (BitConverter.IsLittleEndian) Array.Reverse(cabecera);
            return cabecera;
        }

        private static int LeerInt32BigEndian(byte[] bytes, int offset)
        {
            byte[] cuatro = bytes.Skip(offset).Take(LongitudDeCabecera).ToArray();
            if (BitConverter.IsLittleEndian) Array.Reverse(cuatro);
            return BitConverter.ToInt32(cuatro, 0);
        }

        private static string RutaContrato()
        {
            string[] candidatas =
            {
                Path.Combine(AppContext.BaseDirectory, "contrato", "framing.json"),
                Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "..",
                    "contrato", "framing.json")
            };
            return candidatas.FirstOrDefault(File.Exists) ?? candidatas[0];
        }

        /// <summary>
        /// Servidor TLS mínimo: acepta conexiones, hace el handshake y por cada trama
        /// que recibe la anota y la devuelve tal cual. Cada cliente hace un solo
        /// intercambio por vector.
        /// </summary>
        private sealed class ServidorTls : IDisposable
        {
            private readonly TcpListener _oyente;
            private readonly X509Certificate2 _certificado;
            private readonly Thread _hilo;
            private readonly BlockingCollection<byte[]> _recibidas = new();

            /// <summary>Ultimo error del hilo servidor, para diagnosticar sin comerse la excepcion.</summary>
            public string UltimoError => _ultimoError;

            private volatile string _ultimoError = "(ninguno)";

            public ServidorTls(X509Certificate2 certificado)
            {
                _certificado = certificado;
                RutaPem = Path.Combine(Path.GetTempPath(),
                    "ca-paridad-" + Guid.NewGuid().ToString("N") + ".pem");
                File.WriteAllText(RutaPem, certificado.ExportCertificatePem());
                _oyente = new TcpListener(IPAddress.Loopback, 0);
                _oyente.Start();
                Puerto = ((IPEndPoint)_oyente.LocalEndpoint).Port;
                _hilo = new Thread(Bucle) { IsBackground = true };
                _hilo.Start();
            }

            public int Puerto { get; }

            public string RutaPem { get; }

            public void Dispose()
            {
                // Solo se cierra la puerta: el hilo sigue vivo y puede seguir
                // tocando la coleccion, asi que no se hace Dispose de ella aqui
                // (daria ObjectDisposedException en background y tumbaria el run).
                _recibidas.CompleteAdding();
                try { _oyente.Stop(); } catch { }
                try { File.Delete(RutaPem); } catch { }
            }

            /// <summary>Bloquea hasta que el siguiente cliente haya enviado una trama.</summary>
            public byte[] LeerTrama() => _recibidas.Take();

            private void Bucle()
            {
                // El hilo es de fondo y su excepcion no la ve nadie: se registra y se
                // sigue, para no tumbar el proceso de tests al disposing del servidor.
                while (true)
                {
                    try
                    {
                        using var socket = _oyente.AcceptTcpClient();
                        using var tls = new SslStream(socket.GetStream(), false);
                        tls.AuthenticateAsServer(_certificado, false, false);
                        while (true)
                        {
                            byte[] trama = LeerTramaDe(tls);
                            _recibidas.Add(trama);
                            tls.Write(trama, 0, trama.Length);
                            tls.Flush();
                        }
                    }
                    catch (Exception ex) { _ultimoError = ex.ToString(); }
                }
            }

            /// <summary>
            /// Lee una trama y la devuelve tal cual. La cabecera se devuelve SIN
            /// invertir: es el servidor el que la pone big-endian y el test compara
            /// contra la trama_hex del contrato, que también la lleva asi.
            /// </summary>
            private static byte[] LeerTramaDe(Stream stream)
            {
                byte[] cabecera = new byte[LongitudDeCabecera];
                LeerExacto(stream, cabecera);
                int longitud = LeerInt32BigEndian(cabecera, 0);
                byte[] payload = new byte[longitud];
                LeerExacto(stream, payload);
                return cabecera.Concat(payload).ToArray();
            }

            private static void LeerExacto(Stream stream, byte[] destino)
            {
                int offset = 0;
                while (offset < destino.Length)
                {
                    int leidos = stream.Read(destino, offset, destino.Length - offset);
                    if (leidos <= 0) throw new IOException("Conexion cerrada");
                    offset += leidos;
                }
            }
        }
    }
}