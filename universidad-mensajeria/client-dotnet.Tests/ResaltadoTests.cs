using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// UIX-06: mismo contrato que `textoResaltado` (Java) y `resaltar_html` (Python).
    /// UIX-10: mismo contrato que `anchoBurbuja` (Java) y `ancho_burbuja` (Python).
    /// </summary>
    public class ResaltadoTests
    {
        [Fact]
        public void EnvuelveCoincidenciasIgnorandoMayusculas()
        {
            var tramos = Resaltado.Dividir("Hola mundo, hola", "HOLA");

            Assert.Equal(3, tramos.Count);
            Assert.True(tramos[0].EsMatch);
            Assert.Equal("Hola", tramos[0].Texto);
            Assert.False(tramos[1].EsMatch);
            Assert.True(tramos[2].EsMatch);
            Assert.Equal("hola", tramos[2].Texto);
        }

        [Fact]
        public void NoInterpretaRegexNiRompeHtml()
        {
            // ".*" literal no casa con nada: un solo tramo sin match.
            var tramos = Resaltado.Dividir("hola", ".*");

            Assert.Single(tramos);
            Assert.False(tramos[0].EsMatch);
            Assert.Equal("hola", tramos[0].Texto);
        }

        [Fact]
        public void VacioDevuelveTextoSinMarcas()
        {
            Assert.Equal("hola", Assert.Single(Resaltado.Dividir("hola", "")).Texto);
            Assert.Equal("hola", Assert.Single(Resaltado.Dividir("hola", "   ")).Texto);
            Assert.Empty(Resaltado.Dividir("", "x"));
        }

        [Fact]
        public void AnchoBurbujaSigueAListaConTopeYSuelo()
        {
            Assert.Equal(720, AnchoBurbuja.Calcular(0));
            Assert.Equal(720, AnchoBurbuja.Calcular(50));
            Assert.Equal(585, AnchoBurbuja.Calcular(900));
            Assert.Equal(720, AnchoBurbuja.Calcular(2000));
            Assert.Equal(280, AnchoBurbuja.Calcular(300));
        }
    }
}
