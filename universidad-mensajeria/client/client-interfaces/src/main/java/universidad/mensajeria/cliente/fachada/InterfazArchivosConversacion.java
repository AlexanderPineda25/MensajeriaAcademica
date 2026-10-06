package universidad.mensajeria.cliente.fachada;

import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;

/** Acceso de una conversación a su historial remoto y a los adjuntos. */
public interface InterfazArchivosConversacion {

    PaginaHistorialRemota traerHistorial(String otro, int pagina) throws Exception;

    byte[] descargarImagen(String idMensaje) throws Exception;

    byte[] descargarContenidoArchivo(String archivoId) throws Exception;
}
