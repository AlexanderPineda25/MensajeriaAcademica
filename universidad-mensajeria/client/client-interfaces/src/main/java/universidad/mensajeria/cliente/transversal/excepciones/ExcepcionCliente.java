package universidad.mensajeria.cliente.transversal.excepciones;

/**
 * Base de errores de aplicación del cliente Java.
 * Sustituye al {@code throws Exception} genérico en los puertos.
 */
public class ExcepcionCliente extends Exception {

    public ExcepcionCliente(String mensaje) {
        super(mensaje);
    }

    public ExcepcionCliente(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
