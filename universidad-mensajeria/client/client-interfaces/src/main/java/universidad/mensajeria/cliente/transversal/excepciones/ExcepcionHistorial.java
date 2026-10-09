package universidad.mensajeria.cliente.transversal.excepciones;

/**
 * Fallo del historial local (H2, consultas, pendientes).
 */
public final class ExcepcionHistorial extends ExcepcionCliente {

    public ExcepcionHistorial(String mensaje) {
        super(mensaje);
    }

    public ExcepcionHistorial(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
