package universidad.mensajeria.cliente.fachada;

import java.util.List;
import java.util.Optional;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;

/**
 * Consultas de historial local (paginación, búsqueda, blobs).
 */
public interface InterfazCoordinadorHistorial {

    List<MensajeLocal> conversacion(String yo, String otro, int limite, int offset)
            throws ExcepcionCliente;

    int contarConversacion(String yo, String otro) throws ExcepcionCliente;

    List<String> buscarEnHistorial(String yo, String otro, String patron, int limite)
            throws ExcepcionCliente;

    Optional<String> blobMensaje(String idMensaje) throws ExcepcionCliente;
}
