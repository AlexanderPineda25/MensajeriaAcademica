namespace Mensajeria.Transversal.Dtos
{
    /// <summary>
    /// Una fila de la lista de contactos: quién es, qué dijo, cuántos sin leer y si
    /// está en línea (6 componentes). Espejo de
    /// <c>transversal/records/RecienteChat.java</c>.
    /// </summary>
    public sealed record RecienteChat(
        string? Otro,
        string? UltimoContenido,
        string? UltimoTipo,
        string? FechaUltimo,
        int NoLeidos,
        bool Conectado);
}