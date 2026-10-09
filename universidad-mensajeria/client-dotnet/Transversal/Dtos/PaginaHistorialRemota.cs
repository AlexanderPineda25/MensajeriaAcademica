namespace Mensajeria.Transversal.Dtos
{
    /// <summary>
    /// Página de historial remoto (3 componentes). Espejo de
    /// <c>transversal/records/PaginaHistorialRemota.java</c>.
    ///
    /// <para><b><c>Recibidos == 0</c> no es una página vacía por error:</b> significa
    /// "no hay más historial en el servidor". <c>Pagina</c> es 1-based y
    /// <c>TotalPaginas == 0</c> cuando no hay ninguna.</para>
    /// </summary>
    public sealed record PaginaHistorialRemota(
        int Recibidos,
        int Pagina,
        int TotalPaginas);
}