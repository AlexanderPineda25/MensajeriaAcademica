package universidad.mensajeria.server.mensajes;

import universidad.mensajeria.common.interno.InformeFiltroDTO;
import universidad.mensajeria.common.interno.ActividadUsuarioDTO;
import universidad.mensajeria.common.interno.MensajeResumenDTO;
import universidad.mensajeria.common.interno.PaginaDTO;
import universidad.mensajeria.common.interno.ResultadoMensajeDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * API publica del paquete (PLAN2 §2.5): UNICO que invoca las tuberias.
 * Devuelve futuros (la Fachada encadena el envio al terminar); nunca conoce
 * a LogDeEventos (los eventos salen por oyente).
 * FASE 9 (§8): detalle() alimenta el Informe 3 (historico con JOIN FETCH).
 */
public interface InterfazMensajes {

    CompletableFuture<ResultadoMensajeDTO> procesarTexto(String codigoRemitente,
                                                         String codigoDestinatario,
                                                         String contenido,
                                                         String ipRemitente);

    default CompletableFuture<ResultadoMensajeDTO> procesarTexto(String codigoRemitente,
            String codigoDestinatario, String contenido, String ipRemitente, String idCliente) {
        return procesarTexto(codigoRemitente, codigoDestinatario, contenido, ipRemitente, null,
                idCliente);
    }

    default CompletableFuture<ResultadoMensajeDTO> procesarTexto(String codigoRemitente,
            String codigoDestinatario, String contenido, String ipRemitente,
            String ipDestinatario, String idCliente) {
        return procesarTexto(codigoRemitente, codigoDestinatario, contenido, ipRemitente, idCliente);
    }

    CompletableFuture<ResultadoMensajeDTO> procesarImagen(String codigoRemitente,
                                                          String codigoDestinatario,
                                                          byte[] bytesImagen,
                                                          String nombreOriginal,
                                                          String mime,
                                                          String ipRemitente);

    default CompletableFuture<ResultadoMensajeDTO> procesarImagen(String codigoRemitente,
            String codigoDestinatario, byte[] bytesImagen, String nombreOriginal,
            String mime, String ipRemitente, String idCliente) {
        return procesarImagen(codigoRemitente, codigoDestinatario, bytesImagen,
                nombreOriginal, mime, ipRemitente, null, idCliente);
    }

    default CompletableFuture<ResultadoMensajeDTO> procesarImagen(String codigoRemitente,
            String codigoDestinatario, byte[] bytesImagen, String nombreOriginal,
            String mime, String ipRemitente, String ipDestinatario, String idCliente) {
        return procesarImagen(codigoRemitente, codigoDestinatario, bytesImagen,
                nombreOriginal, mime, ipRemitente, idCliente);
    }

    CompletableFuture<ResultadoMensajeDTO> procesarArchivo(String codigoRemitente,
                                                           String codigoDestinatario,
                                                           byte[] bytesArchivo,
                                                           String nombreOriginal,
                                                           String mime,
                                                           String ipRemitente);

    default CompletableFuture<ResultadoMensajeDTO> procesarArchivo(String codigoRemitente,
            String codigoDestinatario, byte[] bytesArchivo, String nombreOriginal,
            String mime, String ipRemitente, String idCliente) {
        return procesarArchivo(codigoRemitente, codigoDestinatario, bytesArchivo,
                nombreOriginal, mime, ipRemitente, null, idCliente);
    }

    default CompletableFuture<ResultadoMensajeDTO> procesarArchivo(String codigoRemitente,
            String codigoDestinatario, byte[] bytesArchivo, String nombreOriginal,
            String mime, String ipRemitente, String ipDestinatario, String idCliente) {
        return procesarArchivo(codigoRemitente, codigoDestinatario, bytesArchivo,
                nombreOriginal, mime, ipRemitente, idCliente);
    }

    /** Informe 3: historico cronologico de mensajes con detalle (§8). */
    List<MensajeResumenDTO> detalle(InformeFiltroDTO filtro);

    PaginaDTO<MensajeResumenDTO> paginaDetalleUsuario(String codigo, boolean soloArchivos,
                                                      int pagina, int tamano);

    ActividadUsuarioDTO actividadUsuario(String codigo);
}
