package universidad.mensajeria.cliente.transversal.excepciones;

/**
 * Entrada inválida del usuario (código, contenido, destinatario, imagen).
 * Subtipo de {@link IllegalArgumentException} para no romper a quienes ya
 * capturan la excepción estándar.
 */
public final class ExcepcionValidacion extends IllegalArgumentException {

    public ExcepcionValidacion(String mensaje) {
        super(mensaje);
    }

    public ExcepcionValidacion(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
