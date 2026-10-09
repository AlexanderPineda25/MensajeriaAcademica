using System;
using System.IO;
using Mensajeria.Componentes;
using Mensajeria.Fachada;
using Mensajeria.Transversal.Dtos;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>Reglas estructurales de la Fase 2.5 para el cliente WPF.</summary>
    public class ArquitecturaTests
    {
        [Fact]
        public void PuertosNoDependenDePersistenciaNiDeLaInterfazWpf()
        {
            string[] archivos =
            {
                "Componentes/IAutenticacion.cs",
                "Componentes/IConexionRed.cs",
                "Componentes/IHistorialLocal.cs",
                "Componentes/ICacheUsuarios.cs",
                "Componentes/IMapeadorDatos.cs",
                "Componentes/IReceptorMensajes.cs",
                "Fachada/IFachadaCliente.cs"
            };
            string[] dependenciasProhibidas =
            {
                "Microsoft.Data.Sqlite", "SqliteConnection", "Mensajeria.Store",
                "System.Windows", "TcpClient", "NetworkStream", "TramaCodec",
                "new LocalStore", "new HistorialLocal", "new CacheUsuarios",
                "new ConexionRed", "new Autenticacion"
            };

            foreach (string archivo in archivos)
            {
                string fuente = LeerFuenteCliente(archivo);
                foreach (string dependencia in dependenciasProhibidas)
                    Assert.DoesNotContain(dependencia, fuente, StringComparison.Ordinal);
            }
        }

        [Fact]
        public void PresentacionNoAbreSocketNiAccedeALaBaseLocal()
        {
            string carpeta = Path.Combine(RutaCliente(), "Views");
            string[] archivos = Directory.GetFiles(carpeta, "*.xaml.cs");
            Assert.NotEmpty(archivos);
            string[] dependenciasProhibidas =
            {
                "Microsoft.Data.Sqlite", "SqliteConnection", "Mensajeria.Store",
                "LocalStore", "HistorialLocal", "CacheUsuarios", "TcpClient",
                "NetworkStream", "System.Net.Sockets", "TramaCodec"
            };

            foreach (string archivo in archivos)
            {
                string fuente = File.ReadAllText(archivo);
                foreach (string dependencia in dependenciasProhibidas)
                    Assert.DoesNotContain(dependencia, fuente, StringComparison.Ordinal);
            }
        }

        [Fact]
        public void FachadaAceptaDtoClienteEnLaFronteraTipadaDeRed()
        {
            Type fachada = typeof(FachadaCliente);
            Assert.Contains(typeof(IReceptorMensajes), fachada.GetInterfaces());
            Assert.NotNull(fachada.GetMethod(nameof(IReceptorMensajes.AlRecibir),
                new[] { typeof(RespuestaRed) }));
            Assert.NotNull(fachada.GetMethod(nameof(IReceptorMensajes.AlCierre),
                new[] { typeof(RespuestaRed) }));
            Assert.Equal("Mensajeria.Transversal.Dtos",
                typeof(RespuestaRed).Namespace);

            string fuente = LeerFuenteCliente("Fachada/FachadaCliente.cs");
            Assert.DoesNotContain("Mensajeria.CommonProtocol", fuente, StringComparison.Ordinal);
            Assert.DoesNotContain("universidad.mensajeria.common", fuente, StringComparison.Ordinal);
        }

        [Fact]
        public void VistasUsanElPuertoYNoLaFachadaConcreta()
        {
            string chat = LeerFuenteCliente("Views/ChatWindow.xaml.cs");
            string login = LeerFuenteCliente("Views/LoginWindow.xaml.cs");

            Assert.Contains("private readonly IFachadaCliente _fachada", chat, StringComparison.Ordinal);
            Assert.Contains("ChatWindow(IFachadaCliente fachada", chat, StringComparison.Ordinal);
            Assert.Contains("private readonly IFachadaCliente _fachada", login, StringComparison.Ordinal);
            Assert.Contains("LoginWindow(IFachadaCliente fachada", login, StringComparison.Ordinal);

            string[] archivos = Directory.GetFiles(
                Path.Combine(RutaCliente(), "Views"), "*.xaml.cs");
            foreach (string archivo in archivos)
            {
                string fuente = File.ReadAllText(archivo);
                Assert.DoesNotMatch(@"\bnew\s+FachadaCliente\s*\(", fuente);
                Assert.DoesNotMatch(@"\b(?:private|protected|public|internal)\s+(?:readonly\s+)?FachadaCliente\b", fuente);
                Assert.DoesNotMatch(@"\bFachadaCliente\.", fuente);
            }
        }

        private static string LeerFuenteCliente(string rutaRelativa) =>
            File.ReadAllText(Path.Combine(RutaCliente(), rutaRelativa.Replace('/', Path.DirectorySeparatorChar)));

        private static string RutaCliente()
        {
            for (DirectoryInfo? carpeta = new(AppContext.BaseDirectory);
                 carpeta != null; carpeta = carpeta.Parent)
            {
                string directa = Path.Combine(carpeta.FullName, "client-dotnet");
                if (Directory.Exists(directa)
                    && Directory.Exists(Path.Combine(carpeta.FullName, "client-dotnet.Tests")))
                    return directa;

                string anidada = Path.Combine(carpeta.FullName,
                    "universidad-mensajeria", "client-dotnet");
                if (Directory.Exists(anidada)) return anidada;
            }

            throw new DirectoryNotFoundException(
                "No se encontró universidad-mensajeria/client-dotnet desde el directorio de pruebas.");
        }
    }
}
