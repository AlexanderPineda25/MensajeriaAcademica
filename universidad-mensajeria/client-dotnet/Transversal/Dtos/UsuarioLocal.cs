namespace Mensajeria.Transversal.Dtos
{
    /// <summary>
    /// Ficha del directorio de usuarios (7 componentes). Espejo de
    /// <c>transversal/records/UsuarioLocal.java</c>. El directorio real lo persiste
    /// el servidor; esto es solo la copia en memoria del cliente.
    /// </summary>
    public sealed record UsuarioLocal(
        string? Codigo,
        string? Nombres,
        string? Apellidos,
        string? Programa,
        bool Conectado,
        string? FechaRegistro,
        string? Actualizado);
}