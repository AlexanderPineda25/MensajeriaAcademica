using System.Collections.Generic;
using Mensajeria.Transversal.Dtos;

namespace Mensajeria.Componentes
{
    /// <summary>
    /// Único puerto autorizado a convertir JSON de directorio/historial en
    /// records neutrales de la aplicación.
    /// </summary>
    public interface IMapeadorDatos
    {
        List<UsuarioLocal> MapearDirectorio(string json);
        List<MensajeLocal> MapearPaginaHistorial(string json, string codigoSesion);
        string NormalizarTipoMensaje(string tipo);
    }
}
