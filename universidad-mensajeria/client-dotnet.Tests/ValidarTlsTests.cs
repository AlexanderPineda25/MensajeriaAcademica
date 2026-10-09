using System;
using System.IO;
using System.Net.Security;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Mensajeria.Componentes;
using Mensajeria.Fachada;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// SEG-01 / Fase 1.3: los tres rechazos de <see cref="ValidarTls"/> y el camino
    /// por defecto (sin almacén propio manda la cadena del sistema).
    /// </summary>
    public class ValidarTlsTests : IDisposable
    {
        private readonly string _dir;

        public ValidarTlsTests()
        {
            _dir = Path.Combine(Path.GetTempPath(), "tls-paridad-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(_dir);
        }

        public void Dispose()
        {
            try { Directory.Delete(_dir, true); } catch { }
        }

        private string Escribir(string nombre, string contenido)
        {
            string ruta = Path.Combine(_dir, nombre);
            File.WriteAllText(ruta, contenido);
            return ruta;
        }

        // ------------------------------------------------------------- rechazo 1

        [Theory]
        [InlineData("")]
        [InlineData("  ")]
        [InlineData("false")]
        [InlineData("FALSE")]
        [InlineData("False")]
        [InlineData(" true ")]
        public void HabilitadoAdmiteTrueYFalseEnCualquierFormaDeEscribirlo(string texto)
        {
            Assert.Equal(texto.Trim().Equals("true", StringComparison.OrdinalIgnoreCase),
                ValidarTls.HabilitadoDesdeTexto(texto));
        }

        [Fact]
        public void HabilitadoAusenteSignificaApagado()
        {
            Assert.False(ValidarTls.HabilitadoDesdeTexto(null));
        }

        [Theory]
        [InlineData("si")]
        [InlineData("1")]
        [InlineData("yes")]
        [InlineData("TRUEISH")]
        public void RechazaHabilitadoMalFormado(string texto)
        {
            // Sin este rechazo, un "tls.habilitado=si" se leería como apagado y el
            // cliente hablaría en claro creyendo que va cifrado.
            var ex = Assert.Throws<ArgumentException>(() => ValidarTls.HabilitadoDesdeTexto(texto));
            Assert.Contains("tls.habilitado", ex.Message);
        }

        [Fact]
        public void FachadaRechazaTlsMalFormadoAntesDeConectar()
        {
            string? anterior = Mensajeria.Transversal.ClienteConfig.RutaForzadoParaTest;
            string ruta = Escribir("client.properties", "tls.habilitado=quizas\n");
            try
            {
                Mensajeria.Transversal.ClienteConfig.RutaForzadoParaTest = ruta;
                var ex = Assert.Throws<ArgumentException>(() => new FachadaCliente());
                Assert.Contains("tls.habilitado", ex.Message);
            }
            finally
            {
                Mensajeria.Transversal.ClienteConfig.RutaForzadoParaTest = anterior;
            }
        }

        // ------------------------------------------------------------- rechazo 2

        [Fact]
        public void RechazaColisionDePuertosFijos()
        {
            var ex = Assert.Throws<ArgumentException>(() => ValidarTls.Validar(true, 5000, 5000, ""));
            Assert.Contains("comparten el puerto 5000", ex.Message);
        }

        [Fact]
        public void RechazaColisionDesdeElTextoDeLasPropiedades()
        {
            var ex = Assert.Throws<ArgumentException>(() =>
                ValidarTls.ValidarDesdeTexto("true", 5000, "5000", ""));
            Assert.Contains("tls.puerto", ex.Message);
        }

        [Theory]
        [InlineData(0)] // efimero: el TLS sigue al puerto plano, no colisiona
        [InlineData(5001)]
        public void PuertoDistintoNoColisiona(int puertoTls)
        {
            ValidarTls.Validar(true, 5000, puertoTls, "");
            ValidarTls.Validar(true, 0, 0, "");
        }

        [Fact]
        public void ApagadoNoValidaNadaNiSiqueHayAlmacenAusente()
        {
            // Con TLS apagado el almacén es irrelevante: no se toca el disco.
            ValidarTls.Validar(false, 5000, 5000, "/no/existe-ca.pem");
            ValidarTls.ValidarDesdeTexto("false", 5000, "5000", "/no/existe-ca.pem");
        }

        // ------------------------------------------------------------- rechazo 3

        [Fact]
        public void RechazaAlmacenAusente()
        {
            string ausente = Path.Combine(_dir, "no-existe-ca.pem");
            var ex = Assert.Throws<FileNotFoundException>(() =>
                ValidarTls.Validar(true, 5000, 5001, ausente));
            Assert.Contains("almacen de confianza no existe", ex.Message);
            Assert.Contains("tls.confianza", ex.Message);
        }

        [Fact]
        public void RechazaAlmacenSinCertificados()
        {
            // Existe pero no es un PEM: mejor un arranque roto que una confianza nula.
            string vacio = Escribir("vacio.pem", "esto no es un certificado\n");
            var ex = Assert.Throws<ArgumentException>(() => ValidarTls.CrearValidador(vacio, "localhost"));
            Assert.Contains("PEM", ex.Message);
        }

        // -------------------------------------------------------- camino por defecto

        [Theory]
        [InlineData(null)]
        [InlineData("")]
        [InlineData("   ")]
        public void CaminoPorDefectoNoDevuelveValidadorPropio(string? confianza)
        {
            // Sin almacén propio NO se cablea nada: la validación de cadena del sistema
            // es la correcta y hay que conservarla.
            Assert.Null(ValidarTls.CrearValidador(confianza, "localhost"));
            ValidarTls.Validar(true, 5000, 5001, confianza);
        }

        [Fact]
        public void AlmacenValidoSiDevuelveValidador()
        {
            using var ca = EmitirCertificado("CN=servidor-universidad, O=Universidad");
            string pem = Escribir("ca.pem", ca.ExportCertificatePem());
            Assert.NotNull(ValidarTls.CrearValidador(pem, "localhost"));
        }

        // ------------------------------------------- el validador contra certificados

        [Fact]
        public void ValidadorAceptaLaCaPropiaConElHostnameCorrecto()
        {
            using var ca = EmitirCertificado("CN=localhost, O=Universidad");
            var validador = ValidadorDe(ca);
            Assert.True(validador(new object(), ca, (X509Chain?)null, SslPolicyErrors.RemoteCertificateChainErrors));
        }

        [Fact]
        public void ValidadorRechazaUnaFirmaAjena()
        {
            using var ca = EmitirCertificado("CN=localhost, O=Universidad");
            using var impostor = EmitirCertificado("CN=localhost, O=Universidad");
            var validador = ValidadorDe(ca);
            Assert.False(validador(new object(), impostor, (X509Chain?)null, SslPolicyErrors.RemoteCertificateChainErrors));
        }

        [Fact]
        public void ValidadorRechazaElCertificadoDeOtroHost()
        {
            using var ca = EmitirCertificado("CN=localhost, O=Universidad");
            var validador = ValidadorDe(ca);
            // Nombre incorrecto, cadena correcta: el handshake pasa los dos. Un
            // name mismatch tiene que seguir rechazándose aunque el sistema confíe.
            Assert.False(validador(new object(), ca, (X509Chain?)null,
                SslPolicyErrors.RemoteCertificateChainErrors | SslPolicyErrors.RemoteCertificateNameMismatch));
        }

        [Fact]
        public void ValidadorAceptaLoQueElSistemaYaConfio()
        {
            using var ca = EmitirCertificado("CN=localhost, O=Universidad");
            var validador = ValidadorDe(ca);
            Assert.True(validador(new object(), ca, (X509Chain?)null, SslPolicyErrors.None));
        }

        [Fact]
        public void ValidadorRechazaUnCertificadoNulo()
        {
            using var ca = EmitirCertificado("CN=localhost, O=Universidad");
            Assert.False(ValidadorDe(ca)(new object(), (X509Certificate?)null, (X509Chain?)null, SslPolicyErrors.None));
        }

        [Fact]
        public void ValidadorRechazaUnHostQueNoCoincideConElCertificado()
        {
            using var ca = EmitirCertificado("CN=localhost, O=Universidad");
            string pem = Escribir("otro-host.pem", ca.ExportCertificatePem());
            var validador = ValidarTls.CrearValidador(pem, "otro-servidor")!;

            Assert.False(validador(new object(), ca, (X509Chain?)null, SslPolicyErrors.RemoteCertificateChainErrors));
        }

        private RemoteCertificateValidationCallback ValidadorDe(X509Certificate2 ca)
        {
            string pem = Escribir("ca-" + Guid.NewGuid().ToString("N") + ".pem", ca.ExportCertificatePem());
            return ValidarTls.CrearValidador(pem, "localhost")!;
        }

        /// <summary>
        /// CA propia al vuelo, con SAN dNSName = el CN. Es tooling de test: el
        /// cliente solo la acepta porque el test la mete en `tls.confianza`.
        /// </summary>
        // CreateSelfSigned devuelve la clave en un contenedor efímero que SslStream no
        // sabe usar para AuthenticateAsServer, así que se reexporta a PKCS#12 y se
        // vuelve a importar. X509CertificateLoader es .NET 9+ y la app es net8:
        // aquí el constructor es la única vía.
#pragma warning disable SYSLIB0057
        internal static X509Certificate2 EmitirCertificado(string sujeto)
        {
            string cn = sujeto.Split(',')[0];
            using var rsa = RSA.Create(2048);
            var peticion = new CertificateRequest(sujeto, rsa, HashAlgorithmName.SHA256,
                RSASignaturePadding.Pkcs1);
            peticion.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, false, 0, true));
            peticion.CertificateExtensions.Add(new X509KeyUsageExtension(
                X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, true));
            peticion.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
                new OidCollection { new("1.3.6.1.5.5.7.3.1") }, false)); // serverAuth
            var san = new SubjectAlternativeNameBuilder();
            san.AddDnsName(cn["CN=".Length..]);
            peticion.CertificateExtensions.Add(san.Build());

            using var autofirmado = peticion.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1),
                DateTimeOffset.UtcNow.AddYears(5));

            // CreateSelfSigned devuelve la clave en un contenedor efímero que SslStream
            // no sabe usar para AuthenticateAsServer: hay que exportarlo a PKCS#12 y
            // volverlo a importar para que la clave quede disponible.
            return new X509Certificate2(autofirmado.Export(X509ContentType.Pfx, "paridad"),
                "paridad", X509KeyStorageFlags.Exportable);
        }
#pragma warning restore SYSLIB0057
    }
}
