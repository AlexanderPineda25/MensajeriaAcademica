package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;

import java.io.IOException;

/**
 * Reenvío ordenado de la cola local de pendientes (DIP).
 */
public interface InterfazReintentadorPendientes {

    /**
     * Reenvía pendientes en orden; devuelve cuántos salieron.
     * Sin sesión o sin red no intenta nada y devuelve 0.
     */
    int reintentarPendientes() throws ExcepcionCliente, IOException;
}
