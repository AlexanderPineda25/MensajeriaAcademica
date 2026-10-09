package universidad.mensajeria.cliente.transversal.excepciones;

/**
 * Estado de sesión inválido (operar sin sesión, directorio no disponible,
 * rechazo definitivo del servidor).
 * Subtipo de {@link IllegalStateException} para conservar compatibilidad.
 */
public final class ExcepcionSesion extends IllegalStateException {

    public ExcepcionSesion(String mensaje) {
        super(mensaje);
    }

    public ExcepcionSesion(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
