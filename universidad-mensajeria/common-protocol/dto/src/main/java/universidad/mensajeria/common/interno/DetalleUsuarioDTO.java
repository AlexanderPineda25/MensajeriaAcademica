package universidad.mensajeria.common.interno;

/** Ficha administrativa de lectura para la vista de detalle del servidor. */
public record DetalleUsuarioDTO(
        String codigo,
        String nombres,
        String apellidos,
        String programa,
        boolean conectado,
        String fechaRegistro,
        String ultimaConexion,
        long conexionesRegistradas,
        int sesionesActivas,
        long totalMensajes,
        long totalArchivos,
        String contactoPrincipal,
        long mensajesCompartidos
) {
}
