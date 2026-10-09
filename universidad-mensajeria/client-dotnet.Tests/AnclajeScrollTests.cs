using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    public class AnclajeScrollTests
    {
        [Theory]
        [InlineData(0, 420, 20, 400)]
        [InlineData(300, 85, -15, 400)]
        [InlineData(0, 20, 80, 0)]
        public void AjustaOffsetParaConservarCoordenadaDelMensajeVisible(
            double desplazamientoActual, double coordenadaActual,
            double coordenadaOriginal, double esperado)
        {
            double ajustado = AnclajeScroll.AjustarDesplazamiento(
                desplazamientoActual, coordenadaActual, coordenadaOriginal);

            Assert.Equal(esperado, ajustado);
        }
    }
}
