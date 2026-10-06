package universidad.mensajeria.common.interno;

/**
 * Resumen plano de un mensaje (historial, SYNC, entregas): sin entidades,
 * sin lazy, fecha en ISO-8601. Lo construye AlmacenarInformacion.
 */
public record MensajeResumenDTO(
        long id,
        String remitente,
        String destinatario,
        String tipo,
        String contenido,
        String hashSha256,
        Integer numCaracteres,
        Integer numPalabras,
        String nombreArchivo,
        Long tamanoArchivo,
        Long archivoId,
        String mime,
        String etapasFiltrado,
        String fechaEnvio,
        String remitenteNombres,
        String remitenteApellidos,
        String destinatarioNombres,
        String destinatarioApellidos,
        String ipRemitente,
        String ipDestinatario,
        String idCliente
) {
    public MensajeResumenDTO(long id, String remitente, String destinatario, String tipo,
                             String contenido, String hashSha256, Integer numCaracteres,
                             Integer numPalabras, String nombreArchivo, Long tamanoArchivo,
                             Long archivoId, String mime, String etapasFiltrado,
                             String fechaEnvio, String remitenteNombres, String remitenteApellidos,
                             String destinatarioNombres, String destinatarioApellidos) {
        this(id, remitente, destinatario, tipo, contenido, hashSha256, numCaracteres,
                numPalabras, nombreArchivo, tamanoArchivo, archivoId, mime, etapasFiltrado,
                fechaEnvio, remitenteNombres, remitenteApellidos, destinatarioNombres,
                destinatarioApellidos, null, null, null);
    }

    public MensajeResumenDTO(long id, String remitente, String destinatario, String tipo,
                             String contenido, String hashSha256, Integer numCaracteres,
                             Integer numPalabras, String nombreArchivo, Long tamanoArchivo,
                             Long archivoId, String mime, String etapasFiltrado,
                             String fechaEnvio, String remitenteNombres, String remitenteApellidos,
                             String destinatarioNombres, String destinatarioApellidos,
                             String ipRemitente) {
        this(id, remitente, destinatario, tipo, contenido, hashSha256, numCaracteres,
                numPalabras, nombreArchivo, tamanoArchivo, archivoId, mime, etapasFiltrado,
                fechaEnvio, remitenteNombres, remitenteApellidos, destinatarioNombres,
                destinatarioApellidos, ipRemitente, null, null);
    }

    public MensajeResumenDTO(long id, String remitente, String destinatario, String tipo,
                             String contenido, String hashSha256, Integer numCaracteres,
                             Integer numPalabras, String nombreArchivo, Long tamanoArchivo,
                             Long archivoId, String mime, String etapasFiltrado,
                             String fechaEnvio, String remitenteNombres, String remitenteApellidos,
                             String destinatarioNombres, String destinatarioApellidos,
                             String ipRemitente, String idCliente) {
        this(id, remitente, destinatario, tipo, contenido, hashSha256, numCaracteres,
                numPalabras, nombreArchivo, tamanoArchivo, archivoId, mime, etapasFiltrado,
                fechaEnvio, remitenteNombres, remitenteApellidos, destinatarioNombres,
                destinatarioApellidos, ipRemitente, null, idCliente);
    }

    public MensajeResumenDTO(long id, String remitente, String destinatario, String tipo,
                             String contenido, String hashSha256, Integer numCaracteres,
                             Integer numPalabras, String nombreArchivo, Long tamanoArchivo,
                             Long archivoId, String mime, String etapasFiltrado,
                             String fechaEnvio) {
        this(id, remitente, destinatario, tipo, contenido, hashSha256, numCaracteres,
                numPalabras, nombreArchivo, tamanoArchivo, archivoId, mime, etapasFiltrado,
                fechaEnvio, null, null, null, null, null, null, null);
    }
}
