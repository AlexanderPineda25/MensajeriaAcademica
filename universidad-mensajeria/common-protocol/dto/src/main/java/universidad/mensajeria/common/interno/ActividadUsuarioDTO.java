package universidad.mensajeria.common.interno;

/** Contadores agregados de mensajes y archivos asociados a una cuenta. */
public record ActividadUsuarioDTO(
        long totalMensajes,
        long totalArchivos,
        String contactoPrincipal,
        long mensajesCompartidos
) {
}
