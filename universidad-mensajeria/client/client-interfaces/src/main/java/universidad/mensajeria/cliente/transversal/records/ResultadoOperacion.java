package universidad.mensajeria.cliente.transversal.records;

/**
 * Resultado neutral para operaciones de red y casos de uso en el cliente,
 * desacoplado de DTOs y clases de transporte.
 */
public record ResultadoOperacion(boolean exito, String mensajeError, String id) {

    public static ResultadoOperacion exitoso(String id) {
        return new ResultadoOperacion(true, null, id);
    }

    public static ResultadoOperacion fallido(String mensajeError) {
        return new ResultadoOperacion(false, mensajeError, null);
    }
}
