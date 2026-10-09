using System.IO;
using System.Linq;
using System.Xml.Linq;
using Xunit;

namespace Mensajeria.Tests
{
    public class AccionesChatWpfTests
    {
        private static string RaizMonorepo => Path.GetFullPath(Path.Combine(
            AppContext.BaseDirectory, "..", "..", "..", ".."));

        [Fact]
        public void ComposerEnviaConEnterYReservaMayusEnterParaElSalto()
        {
            string ruta = Path.Combine(RaizMonorepo, "client-dotnet", "Views", "ChatWindow.xaml");
            var documento = XDocument.Load(ruta);
            XNamespace wpf = "http://schemas.microsoft.com/winfx/2006/xaml/presentation";
            XNamespace xaml = "http://schemas.microsoft.com/winfx/2006/xaml";
            XNamespace ui = "http://schemas.lepo.co/wpfui/2022/xaml";
            var composer = Assert.Single(documento.Descendants(ui + "TextBox"),
                elemento => (string?)elemento.Attribute(xaml + "Name") == "txtMensaje");

            Assert.Equal("True", (string?)composer.Attribute("AcceptsReturn"));
            Assert.Equal("TxtMensaje_KeyDown", (string?)composer.Attribute("KeyDown"));
            Assert.Contains("Enter para enviar, Mayús+Enter para salto",
                (string?)composer.Attribute("PlaceholderText"));
        }

        [Fact]
        public void AccionKickOtrasEsDistintaDeSalirEnTodos()
        {
            string ruta = Path.Combine(RaizMonorepo, "client-dotnet", "Views", "ChatWindow.xaml");
            var documento = XDocument.Load(ruta);
            XNamespace ui = "http://schemas.lepo.co/wpfui/2022/xaml";
            var botones = documento.Descendants(ui + "Button").ToArray();

            var kick = Assert.Single(botones, boton =>
                (string?)boton.Attribute("Content") == "KICK otras");
            var salirTodos = Assert.Single(botones, boton =>
                (string?)boton.Attribute("Content") == "Salir en todos");

            Assert.Equal("BtnKickOtras_Click", (string?)kick.Attribute("Click"));
            Assert.Equal("BtnSalirTodos_Click", (string?)salirTodos.Attribute("Click"));
        }
    }
}
