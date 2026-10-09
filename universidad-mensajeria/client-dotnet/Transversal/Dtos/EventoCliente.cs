namespace Mensajeria.Transversal.Dtos
{
    /// <summary>
    /// Evento de dominio ya adaptado (3 componentes): no se filtra un
    /// <c>JsonElement</c> hasta la presentación. Espejo de
    /// <c>transversal/records/EventoCliente.java</c>.
    ///
    /// <para><b>POR QUÉ el enum se llama <see cref="TipoEvento"/> y no
    /// <c>Tipo</c>:</b> el contrato lo llama <c>Tipo</c>, pero en C# el componente del
    /// record ya ocupa ese nombre y un tipo anidado homónimo no se puede declarar.
    /// Los cinco valores son los del contrato y se emparejan por nombre exacto, no por
    /// ordinal.</para>
    /// </summary>
    public sealed record EventoCliente(
        EventoCliente.TipoEvento Tipo,
        string? Remitente,
        string? Destinatario)
    {
        /// <summary>Los cinco eventos de dominio que la presentación distingue.</summary>
        public enum TipoEvento
        {
            PRESENCIA,
            SESION_CERRADA,
            ACUSE_ENTREGA,
            ACUSE_LECTURA,
            MENSAJE
        }
    }
}