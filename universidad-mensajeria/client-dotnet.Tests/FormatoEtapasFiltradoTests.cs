using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    public class FormatoEtapasFiltradoTests
    {
        [Fact]
        public void ConservaOrdenTraduceEtapasYFormateaDuracion()
        {
            const string json = "[{\"etapa\":\"SHA256\",\"ms\":3},"
                + "{\"etapa\":\"EscalaGrises\",\"ms\":12},"
                + "{\"etapa\":\"Giro180\",\"ms\":1250},"
                + "{\"etapa\":\"Reduccion\",\"ms\":0}]";

            string resumen = FormatoEtapasFiltrado.Resumir(json);

            Assert.Equal("Filtros: Verificación SHA-256 (3 ms) → Grises (12 ms) → "
                + "Giro 180° (1250 ms) → Reducción (0 ms)", resumen);
        }

        [Fact]
        public void OmiteRutasYNoPresentaComoNombreUnaEtapaQuePareceRuta()
        {
            const string ruta = "C:/servidor/uploads/privado/01_grises.png";
            string json = "[{\"etapa\":\"" + ruta + "\",\"ms\":4,\"ruta\":\"" + ruta
                + "\"},{\"etapa\":\"Brillo\",\"ms\":8,\"ruta\":\"" + ruta + "\"}]";

            string resumen = FormatoEtapasFiltrado.Resumir(json);

            Assert.DoesNotContain(ruta, resumen);
            Assert.DoesNotContain("uploads", resumen);
            Assert.Equal("Filtros: Grises (4 ms) → Brillo (8 ms)", resumen);
        }

        [Theory]
        [InlineData(null)]
        [InlineData("")]
        [InlineData("no es json")]
        [InlineData("{}")]
        [InlineData("[]")]
        public void NoCreaLeyendaSinEtapasValidas(string? json) =>
            Assert.Equal("", FormatoEtapasFiltrado.Resumir(json));
    }
}
