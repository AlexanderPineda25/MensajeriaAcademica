package universidad.mensajeria.cliente.fachada;

import java.io.IOException;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;

/** Acceso de una conversación a su historial remoto y a los adjuntos. */
public interface InterfazArchivosConversacion {

    PaginaHistorialRemota traerHistorial(String otro, int pagina) throws ExcepcionCliente, IOException;

    byte[] descargarImagen(String idMensaje) throws ExcepcionCliente, IOException;

    byte[] descargarContenidoArchivo(String archivoId) throws ExcepcionCliente, IOException;
}
