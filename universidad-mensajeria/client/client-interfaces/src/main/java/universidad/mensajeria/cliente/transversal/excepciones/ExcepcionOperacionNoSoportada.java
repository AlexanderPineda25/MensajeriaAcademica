package universidad.mensajeria.cliente.transversal.excepciones;

import java.io.IOException;

/**
 * Operación opcional del puerto de red no soportada por el adaptador.
 * Subtipo de {@link IOException} documentado en el contrato para no romper
 * la sustitución (LSP): el llamante ya maneja fallos de red.
 */
public final class ExcepcionOperacionNoSoportada extends IOException {

    public ExcepcionOperacionNoSoportada(String operacion) {
        super(operacion + " no soportado por este adaptador de red");
    }
}
