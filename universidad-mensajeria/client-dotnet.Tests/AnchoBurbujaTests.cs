using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests;

public class AnchoBurbujaTests
{
    [Theory]
    [InlineData(400, 280)]
    [InlineData(600, 390)]
    [InlineData(2000, 720)]
    [InlineData(double.NaN, 720)]
    [InlineData(double.PositiveInfinity, 720)]
    public void LimitaElAnchoDeLaBurbujaAlAnchoDisponible(double ancho, double esperado)
    {
        Assert.Equal(esperado, AnchoBurbuja.Calcular(ancho));
    }

    [Fact]
    public void MuestraMensajesPropiosYEntrantesEnLadosOpuestos()
    {
        Assert.Equal(LadoBurbuja.Derecha, BurbujaChat.Alineacion(esPropio: true));
        Assert.Equal(LadoBurbuja.Izquierda, BurbujaChat.Alineacion(esPropio: false));
        Assert.True(BurbujaChat.DebeMostrar(esCabeceraDia: false));
        Assert.False(BurbujaChat.DebeMostrar(esCabeceraDia: true));
    }
}
