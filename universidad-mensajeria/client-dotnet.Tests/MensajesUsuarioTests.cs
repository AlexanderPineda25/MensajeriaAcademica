using Mensajeria.Transversal;
using System.IO;
using Xunit;

namespace Mensajeria.Tests;

public class MensajesUsuarioTests
{
    [Fact]
    public void ErrorDeConexionNoExponeTipoNiMensajeTecnico()
    {
        string texto = MensajesUsuario.Conexion(new IOException("SocketException: connection refused"));
        Assert.Contains("conectar con el servidor", texto);
        Assert.DoesNotContain("IOException", texto);
        Assert.DoesNotContain("SocketException", texto);
        Assert.DoesNotContain("connection refused", texto);
    }

    [Fact]
    public void CredencialesInvalidasUsanUnaRespuestaGenerica()
    {
        Assert.Equal("Código o contraseña incorrectos.", MensajesUsuario.CredencialesInvalidas);
    }
}
