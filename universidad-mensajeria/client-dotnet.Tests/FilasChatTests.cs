using System;
using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// UIX-04/UIX-05: mismo contrato que `FilasChat` (Java) y `agrupar` (Python).
    /// </summary>
    public class FilasChatTests
    {
        [Fact]
        public void LaRachaDelMismoAutorAgrupa()
        {
            string hoy = DateTime.Today.ToString("yyyy-MM-dd");
            Assert.True(FilasChat.Agrupar(hoy + "T10:00:00", "B002", hoy + "T10:02:00", "B002"));
        }

        [Fact]
        public void CambioDeAutorRompeLaRacha()
        {
            string hoy = DateTime.Today.ToString("yyyy-MM-dd");
            Assert.False(FilasChat.Agrupar(hoy + "T10:00:00", "B002", hoy + "T10:02:00", "C003"));
            Assert.False(FilasChat.Agrupar(null, "B002", hoy + "T10:02:00", "B002"));
        }

        [Fact]
        public void CorteDeDiaYVentanaRompenLaRacha()
        {
            string hoy = DateTime.Today.ToString("yyyy-MM-dd");
            string ayer = DateTime.Today.AddDays(-1).ToString("yyyy-MM-dd");
            Assert.False(FilasChat.Agrupar(hoy + "T10:00:00", "B002", ayer + "T10:02:00", "B002"));
            Assert.False(FilasChat.Agrupar(hoy + "T10:00:00", "B002", hoy + "T11:00:00", "B002"));
        }

        [Fact]
        public void FechaIlegibleNoAgrupa()
        {
            Assert.False(FilasChat.Agrupar("basura", "B002", "basura", "B002"));
            Assert.False(FilasChat.CambiaElDia("basura", DateTime.Now.ToString("o")));
        }

        [Fact]
        public void DetectaElCambioDeDia()
        {
            string hoy = DateTime.Today.ToString("yyyy-MM-dd");
            string ayer = DateTime.Today.AddDays(-1).ToString("yyyy-MM-dd");
            Assert.True(FilasChat.CambiaElDia(ayer + " 10:00:00", hoy + "T10:00:00"));
            Assert.False(FilasChat.CambiaElDia(hoy + "T10:00:00", hoy + "T11:00:00"));
            Assert.False(FilasChat.CambiaElDia(null, hoy + "T10:00:00"));
        }
    }
}
