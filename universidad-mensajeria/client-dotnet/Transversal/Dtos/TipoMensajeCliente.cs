namespace Mensajeria.Transversal.Dtos
{
    /// <summary>
    /// Tipos que admite el contrato TCP/JSON. Se emparejan por nombre con
    /// <c>contrato/puertos.json</c>; no se envía el ordinal por la red.
    /// </summary>
    public enum TipoMensajeCliente
    {
        LOGIN,
        LOGIN_RESPUESTA,
        REGISTRO,
        LOGOUT,
        CLOSE_NOTICE,
        KICK,
        MENSAJE_TEXTO,
        MENSAJE_IMAGEN,
        MENSAJE_ARCHIVO,
        ARCHIVO_INICIO,
        ARCHIVO_PARTE,
        ARCHIVO_FIN,
        IMAGE_FILTERED_RESULT,
        BROADCAST,
        MENSAJE_ENTREGADO,
        MENSAJE_LEIDO,
        PRESENCIA,
        LISTAR_CONECTADOS,
        LISTAR_CONECTADOS_RESPUESTA,
        LISTAR_USUARIOS,
        LISTAR_USUARIOS_RESPUESTA,
        HISTORIAL_REQ,
        HISTORIAL_PAGE,
        DESCARGAR_ARCHIVO,
        DESCARGAR_ARCHIVO_RESPUESTA,
        SYNC_LOGIN,
        ACK,
        ERROR
    }
}
