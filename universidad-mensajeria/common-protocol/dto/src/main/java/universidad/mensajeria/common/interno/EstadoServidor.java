package universidad.mensajeria.common.interno;

/** Estado vivo del servidor TCP reportado a las vistas (DTO-Record). */
public record EstadoServidor(
        boolean activo,
        int puerto,
        int usuariosConectados,
        int sesionesActivas,
        int maxConexiones,
        int mensajesEnCola,
        int trabajadoresOcupados,
        int trabajadoresDisponibles,
        int trabajadoresTotal,
        long mensajesProcesados) {
}
