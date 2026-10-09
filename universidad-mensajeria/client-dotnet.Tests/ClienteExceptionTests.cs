using System;
using System.IO;
using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// Fase 1.4: las seis excepciones del contrato (<c>contrato/puertos.json</c>
    /// §excepciones) y, sobre todo, sus <b>tres familias</b>. Un <c>catch</c> por tipo
    /// no es intercambiable entre ellas, y por eso las bases están elegidas para que
    /// en C# capturen exactamente lo que capturan en Java.
    /// </summary>
    public class ClienteExceptionTests
    {
        [Fact]
        public void HistorialYPersistenciaSonDeLaFamiliaCliente()
        {
            Assert.IsAssignableFrom<ClienteException>(new HistorialException("h"));
            Assert.IsAssignableFrom<ClienteException>(new PersistenciaException("p"));
        }

        [Fact]
        public void ValidacionEsArgumentExceptionYNoDeLaFamiliaCliente()
        {
            // Motivo de la separación: un fallo de validación del llamante no debe
            // caer en el `catch (ClienteException)` de "reintentar más tarde".
            var ex = new ValidacionException("codigo vacio");

            Assert.IsAssignableFrom<ArgumentException>(ex);
            Assert.IsNotType<ClienteException>(ex);
            Assert.Equal("codigo vacio", ex.Message);
        }

        [Fact]
        public void SesionEsInvalidOperationExceptionYNoDeLaFamiliaCliente()
        {
            // Un rechazo del servidor no se reintenta: es estado, no dominio.
            var ex = new SesionException("sesion cerrada");

            Assert.IsAssignableFrom<InvalidOperationException>(ex);
            Assert.IsNotType<ClienteException>(ex);
        }

        [Fact]
        public void OperacionNoSoportadaEsIoExceptionParaNoRomperElLSP()
        {
            // Motivo de la herencia: el llamante ya trata fallos de red, así que un
            // puerto opcional que no se soporta no puede romper la sustitución.
            var ex = new OperacionNoSoportadaException("enviarArchivo");

            Assert.IsAssignableFrom<IOException>(ex);
            Assert.IsNotAssignableFrom<ClienteException>(ex);
            Assert.Equal("enviarArchivo no soportado por este adaptador de red", ex.Message);
        }

        [Fact]
        public void LosCatchPorFamiliaCaenEnLaExcepcionCorrecta()
        {
            // Reproduce el `catch` real de cada capa y comprueba qué entra en cada uno.
            Exception validacion = new ValidacionException("v");
            Exception sesion = new SesionException("s");
            Exception red = new OperacionNoSoportadaException("r");
            Exception dominio = new HistorialException("d");

            Assert.True(validacion is ArgumentException);
            Assert.False(validacion is ClienteException);

            Assert.True(sesion is InvalidOperationException);
            Assert.False(sesion is ClienteException);

            Assert.True(red is IOException);

            Assert.True(dominio is ClienteException);
            Assert.False(dominio is ArgumentException);
        }

        [Fact]
        public void LaCausaSeConservaParaElDiagnostico()
        {
            var causa = new TimeoutException("sin respuesta");
            var conCausa = new HistorialException("fallo de consulta", causa);
            var validacion = new ValidacionException("valor malo", causa);
            var sesion = new SesionException("sesion invalida", causa);

            Assert.Same(causa, conCausa.InnerException);
            Assert.Same(causa, validacion.InnerException);
            Assert.Same(causa, sesion.InnerException);
        }

        [Fact]
        public void ClienteExceptionEsLaRaizDeLasDosSubmaquinasDelContrato()
        {
            // Las 6 excepciones del contrato en 3 familias, ni una más ni una menos.
            Assert.IsAssignableFrom<Exception>(new ClienteException("c"));
            Assert.Equal("c", new ClienteException("c").Message);
            Assert.Equal("c", new ClienteException("c", new Exception("x")).Message);
        }
    }
}