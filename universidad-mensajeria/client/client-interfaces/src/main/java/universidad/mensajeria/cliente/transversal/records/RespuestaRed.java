package universidad.mensajeria.cliente.transversal.records;

import java.util.List;

/** Respuesta o notificación de aplicación adaptada desde el transporte TCP. */
public record RespuestaRed(
        TipoMensajeCliente tipo,
        String id,
        String fechaHora,
        String codigo,
        String nombres,
        String apellidos,
        String programa,
        String remitente,
        String destinatario,
        String contenido,
        String hashSha256,
        Integer numCaracteres,
        Integer numPalabras,
        String etapasFiltrado,
        String nombreArchivo,
        Long tamanoArchivo,
        String archivoId,
        String mime,
        String contenidoImagen,
        Integer pagina,
        Integer totalPaginas,
        Boolean exito,
        String mensajeError,
        List<String> usuariosConectados,
        String tipoOriginal
) {
}
