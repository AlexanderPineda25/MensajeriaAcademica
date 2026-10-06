package universidad.mensajeria.cliente.transversal.records;

/** Evento de aplicación entregado a la interfaz, independiente del protocolo de red. */
public record EventoCliente(Tipo tipo, String remitente, String destinatario) {

    public enum Tipo {
        PRESENCIA,
        SESION_CERRADA,
        ACUSE_ENTREGA,
        ACUSE_LECTURA,
        MENSAJE
    }

    public boolean esAcuse() {
        return tipo == Tipo.ACUSE_ENTREGA || tipo == Tipo.ACUSE_LECTURA;
    }
}
