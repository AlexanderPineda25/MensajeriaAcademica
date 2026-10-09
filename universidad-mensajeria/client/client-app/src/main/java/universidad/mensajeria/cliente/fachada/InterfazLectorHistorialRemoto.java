package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;
import universidad.mensajeria.cliente.transversal.records.RespuestaRed;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Lectura de historial remoto y descarga bajo demanda (DIP).
 */
public interface InterfazLectorHistorialRemoto {

    /** Historial offline paginado (más reciente primero, como el contrato). */
    List<MensajeLocal> conversacion(String otro, int limite, int offset)
            throws ExcepcionCliente, IOException;

    int contarConversacion(String otro) throws ExcepcionCliente, IOException;

    /** Ids de mensajes con el patrón (de más nuevo a más viejo, tope 200). */
    List<String> buscarEnHistorial(String otro, String patron) throws ExcepcionCliente, IOException;

    /** Trae UNA página remota, la persiste y registra `archivoId` para descargas. */
    PaginaHistorialRemota traerHistorial(String otro, int pagina) throws ExcepcionCliente, IOException;

    /** Bytes de UN mensaje (miniatura visible), nunca la lista. */
    Optional<String> blobMensaje(String idMensaje) throws ExcepcionCliente;

    /** Descarga los bytes de una imagen y los guarda en H2. */
    byte[] descargarImagen(String idMensaje) throws ExcepcionCliente, IOException;

    /** Descarga un adjunto sin exponer el DTO de red a la presentación. */
    byte[] descargarContenidoArchivo(String archivoId) throws ExcepcionCliente, IOException;

    /** Descarga de archivo del servidor bajo demanda. */
    RespuestaRed descargarArchivo(String archivoId) throws ExcepcionCliente, IOException;
}
