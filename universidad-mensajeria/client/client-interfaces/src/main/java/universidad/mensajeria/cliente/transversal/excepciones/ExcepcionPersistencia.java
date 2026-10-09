package universidad.mensajeria.cliente.transversal.excepciones;

/**
 * Fallo de la base local (apertura, esquema, migración por cuenta).
 */
public final class ExcepcionPersistencia extends ExcepcionCliente {

    public ExcepcionPersistencia(String mensaje) {
        super(mensaje);
    }

    public ExcepcionPersistencia(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
