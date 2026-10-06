using System;
using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// UIX-03: el mismo contrato que `FormatoFecha` (Java) y `fechas` (Python).
    /// </summary>
    public class FormatoFechaTests
    {
        [Fact]
        public void HoyMuestraSoloLaHora()
        {
            // Hoy a las HH:05 en ISO.
            string hoy = DateTime.Today.AddHours(DateTime.Now.Hour).AddMinutes(5)
                .ToString("yyyy-MM-ddTHH:mm:ss");

            Assert.Equal(DateTime.Now.ToString("HH") + ":05", FormatoFecha.HoraCorta(hoy));
            Assert.Equal("Hoy", FormatoFecha.EtiquetaDia(hoy));
        }

        [Fact]
        public void AyerSeMarcaComoAyer()
        {
            string ayer = DateTime.Now.AddDays(-1).Date.AddHours(9).AddMinutes(30)
                .ToString("yyyy-MM-ddTHH:mm:ss");

            Assert.Equal("Ayer", FormatoFecha.EtiquetaDia(ayer));
            Assert.Contains("09:30", FormatoFecha.HoraCorta(ayer));
        }

        [Fact]
        public void HaceUnaSemanaMuestraDiaYMes()
        {
            string hace = DateTime.Now.AddDays(-7).Date.AddHours(8)
                .ToString("yyyy-MM-dd HH:mm:ss");

            Assert.Matches(@"^\d{2}-\d{2} 08:00$", FormatoFecha.HoraCorta(hace));
            Assert.Matches(@"^\d{2}-\d{2}$", FormatoFecha.EtiquetaDia(hace));
        }

        [Fact]
        public void LoIlegibleNoRompeLaFila()
        {
            Assert.Equal("", FormatoFecha.HoraCorta(null));
            Assert.Equal("", FormatoFecha.HoraCorta(""));
            Assert.Equal("", FormatoFecha.HoraCorta("ayer a las tres"));
            Assert.Equal("", FormatoFecha.EtiquetaDia("basura"));
            Assert.Equal("", FormatoFecha.ClaveDia(null));
        }

        [Fact]
        public void ClaveDiaAgrupaPorFecha()
        {
            Assert.Equal(DateTime.Today.ToString("yyyy-MM-dd"),
                FormatoFecha.ClaveDia(DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss")));
        }
    }
}
