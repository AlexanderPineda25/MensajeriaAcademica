using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    public sealed class EstadoAvisoInlineTests
    {
        [Fact]
        public void IniciaOcultoYVacio()
        {
            var estado = new EstadoAvisoInline();

            Assert.False(estado.Visible);
            Assert.Equal("", estado.Mensaje);
            Assert.Equal(TipoAvisoInline.Informacion, estado.Tipo);
        }

        [Theory]
        [InlineData(TipoAvisoInline.Error, "No se pudo enviar")]
        [InlineData(TipoAvisoInline.Advertencia, "El archivo supera el límite")]
        [InlineData(TipoAvisoInline.Informacion, "Sin red: guardado para reintentar")]
        [InlineData(TipoAvisoInline.Exito, "Archivo guardado")]
        public void MostrarPublicaMensajeYSeveridad(TipoAvisoInline tipo, string mensaje)
        {
            var estado = new EstadoAvisoInline();

            estado.Mostrar("  " + mensaje + "  ", tipo);

            Assert.True(estado.Visible);
            Assert.Equal(mensaje, estado.Mensaje);
            Assert.Equal(tipo, estado.Tipo);
        }

        [Fact]
        public void MostrarAvisoNuevoReemplazaElAnterior()
        {
            var estado = new EstadoAvisoInline();
            estado.Mostrar("Error anterior", TipoAvisoInline.Error);

            estado.Mostrar("Descarga completa", TipoAvisoInline.Exito);

            Assert.True(estado.Visible);
            Assert.Equal("Descarga completa", estado.Mensaje);
            Assert.Equal(TipoAvisoInline.Exito, estado.Tipo);
        }

        [Fact]
        public void LimpiarOcultaYRestableceElAviso()
        {
            var estado = new EstadoAvisoInline();
            estado.Mostrar("Fallo de red", TipoAvisoInline.Error);

            estado.Limpiar();

            Assert.False(estado.Visible);
            Assert.Equal("", estado.Mensaje);
            Assert.Equal(TipoAvisoInline.Informacion, estado.Tipo);
        }

        [Theory]
        [InlineData(null)]
        [InlineData("")]
        [InlineData("  ")]
        public void MostrarTextoVacioLimpiaElAviso(string? mensaje)
        {
            var estado = new EstadoAvisoInline();
            estado.Mostrar("Aviso existente", TipoAvisoInline.Error);

            estado.Mostrar(mensaje, TipoAvisoInline.Advertencia);

            Assert.False(estado.Visible);
            Assert.Equal("", estado.Mensaje);
        }
    }
}
