using Mensajeria.Componentes;
using Mensajeria.Fachada;

namespace Mensajeria.Composicion
{
    /// <summary>
    /// Composition root del cliente .NET. La UI solo recibe puertos; la
    /// fachada conserva temporalmente la composición diferida de red y SQLite
    /// hasta que el login determine la cuenta local que debe abrir.
    /// </summary>
    public static class FabricaCliente
    {
        public static IFachadaCliente CrearFachada() =>
            new FachadaCliente(new MapeadorDatosServidor());
    }
}
