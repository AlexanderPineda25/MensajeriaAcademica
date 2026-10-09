using System.Collections.Generic;

namespace Mensajeria.Transversal.Dtos
{
    /// <summary>
    /// Respuesta neutral del adaptador de red (25 componentes del contrato).
    /// Las vistas y casos de uso no necesitan inspeccionar <c>JsonElement</c>.
    /// </summary>
    public sealed record RespuestaRed(
        TipoMensajeCliente Tipo,
        string? Id,
        string? FechaHora,
        string? Codigo,
        string? Nombres,
        string? Apellidos,
        string? Programa,
        string? Remitente,
        string? Destinatario,
        string? Contenido,
        string? HashSha256,
        int? NumCaracteres,
        int? NumPalabras,
        string? EtapasFiltrado,
        string? NombreArchivo,
        long? TamanoArchivo,
        string? ArchivoId,
        string? Mime,
        string? ContenidoImagen,
        int? Pagina,
        int? TotalPaginas,
        bool? Exito,
        string? MensajeError,
        List<string>? UsuariosConectados,
        string? TipoOriginal);
}
