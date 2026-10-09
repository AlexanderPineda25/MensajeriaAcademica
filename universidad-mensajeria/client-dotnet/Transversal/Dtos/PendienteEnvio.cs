namespace Mensajeria.Transversal.Dtos
{
    /// <summary>
    /// La cola offline (10 componentes). Espejo de
    /// <c>transversal/records/PendienteEnvio.java</c>.
    ///
    /// <para><b><c>Payload</c> son bytes CRUDOS, nunca Base64 en <c>string</c></b>:
    /// al reintentar se vuelve a fragmentar el archivo, y el corte en trozos solo
    /// tiene sentido sobre los bytes (AGENTS.md invariante 2). Guardarlo en Base64
    /// obligaría a decodificar un archivo entero de 20 MiB para poder cortarlo.</para>
    ///
    /// <para><b>Ojo con la igualdad:</b> como <c>record</c>, <c>Payload</c> se compara
    /// por referencia (igual que en Java con <c>byte[]</c>). Para comparar contenido
    /// hay que usar el overload de colección de <c>Assert.Equal</c> o
    /// <c>SequenceEqual</c>. No se "arregla" con un <c>Equals</c> propio: el contrato
    /// pide un record y las otras dos capas deben seguir viéndose iguales.</para>
    /// </summary>
    public sealed record PendienteEnvio(
        string? Id,
        string? Tipo,
        string? Origen,
        string? Destino,
        string? Contenido,
        string? NombreArchivo,
        byte[]? Payload,
        string? FechaCreado,
        int Intentos,
        string? UltimoError);
}