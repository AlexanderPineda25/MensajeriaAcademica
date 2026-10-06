using System.IO;
using Mensajeria.Transversal;
using Xunit;

namespace Mensajeria.Tests
{
    /// <summary>
    /// Equivalencia de client.properties entre los 3 clientes (MUERTE-5).
    ///
    /// El mismo fichero canonico lo leen los tests de Java y Python (esta
    /// carpeta lo copia al output). Cada cliente comprueba que su analizador
    /// resuelve los mismos valores; si uno se aparta del formato compartido
    /// (PROTOCOLO.md 5.2), su test falla.
    /// </summary>
    public class ClienteConfigTests
    {
        private static readonly string Fixture =
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "client-properties-fixture.properties");

        [Fact]
        public void FixtureCanonicoCoincideConLosOtherClientes()
        {
            Assert.True(File.Exists(Fixture), "falta el fixture canonico: " + Fixture);
            ClienteConfig.RutaForzadoParaTest = Fixture;
            try
            {
                var props = ClienteConfig.Leer();
                Assert.Equal("10.0.0.25", ClienteConfig.Valor("host", "localhost"));
                Assert.Equal(5012, ClienteConfig.Entero("puerto", 5000, 1, 65535));
                Assert.Equal("jdbc:h2:mem:prueba;OPTIONS=x=y",
                    ClienteConfig.Valor("db.url", ""));
                Assert.True(ClienteConfig.Booleano("tls.habilitado", false));
                Assert.Equal(5443, ClienteConfig.Entero("tls.puerto", 5001, 1, 65535));
                Assert.Equal("C:/confianza/prueba.jks", ClienteConfig.Valor("tls.confianza", ""));
                Assert.Equal("es", ClienteConfig.Valor("idioma", ""));
                // clave repetida: gana la ultima linea
                Assert.Equal("oscuro", ClienteConfig.Valor("tema", ""));
                // Ningun comentario ni linea vacia se cuela como clave.
                Assert.DoesNotContain("#", props.Keys);
                foreach (var clave in props.Keys)
                {
                    Assert.Equal(clave, clave.Trim());
                }
            }
            finally
            {
                ClienteConfig.RutaForzadoParaTest = null;
            }
        }

        [Fact]
        public void RangoDePuertoYClaveAusenteCaeAlDefecto()
        {
            ClienteConfig.RutaForzadoParaTest = Fixture;
            try
            {
                // El fixture trae puerto=5012, dentro de rango.
                Assert.Equal(5012, ClienteConfig.Entero("puerto", 5000, 1, 65535));
                // Clave ausente -> defecto del parametro.
                Assert.Equal(5001, ClienteConfig.Entero("inexistente", 5001, 1, 65535));
                // Rango que excluye el valor del fixture -> cae al defecto.
                Assert.Equal(5000, ClienteConfig.Entero("puerto", 5000, 5020, 6000));
                // Booleano ausente -> defecto.
                Assert.False(ClienteConfig.Booleano("inexistente", false));
            }
            finally
            {
                ClienteConfig.RutaForzadoParaTest = null;
            }
        }

        [Fact]
        public void GuardarConservaElRestoDelFichero()
        {
            string ruta = Path.Combine(Path.GetTempPath(),
                "cc-" + Guid.NewGuid().ToString("N") + ".properties");
            File.Copy(Fixture, ruta);
            try
            {
                ClienteConfig.RutaForzadoParaTest = ruta;
                ClienteConfig.Guardar("tema", "claro");

                var lineas = File.ReadAllLines(ruta);
                Assert.Contains("tema=claro", lineas);
                Assert.DoesNotContain("tema=oscuro", lineas);
                // El resto del fichero sobrevive (comentarios y claves ajenas).
                Assert.Contains(lineas, l => l.Trim() == "host = 10.0.0.25");
                Assert.Contains(lineas, l => l.StartsWith("# comentario al principio"));
            }
            finally
            {
                ClienteConfig.RutaForzadoParaTest = null;
                File.Delete(ruta);
            }
        }
    }
}