using System.Collections.Generic;

namespace Mensajeria.Componentes
{
    /// <summary>Contrato de historial local (queries HISTORIAL_LOCAL.md).</summary>
    public interface IHistorialLocal
    {
        void UpsertMensaje(string id, string origen, string destino, string tipo,
            string? contenido, string? hash, int? numCaracteres, int? numPalabras,
            string? nombreArchivo, string? rutaArchivo, long? tamanoArchivo,
            string fechaEnvio, int enviado, int descargado,
            string estado = "ENVIADO", string? archivoId = null,
            string? etapasFiltrado = null);
        List<Dictionary<string, object>> PaginaConversacion(string origen, string destino,
            int limit, int offset);
        /// <summary>Última fecha local por interlocutor, para ordenar conversaciones recientes.</summary>
        IReadOnlyDictionary<string, string> UltimosMensajesPorContacto(string yo);
        /// <summary>
        /// UIX-06: ids de mensajes con el patrón en texto o nombre de archivo
        /// (insensible a mayúsculas), de más nuevo a más viejo. La UI salta.
        /// </summary>
        List<string> BuscarEnConversacion(string origen, string destino, string patron,
            int limite);
        string? ContenidoPorId(string idMensaje);
        int ContarConversacion(string origen, string destino);
        void MarcarDescargado(string idMensaje, string rutaArchivo);
        void ActualizarArchivoId(string idMensaje, string archivoId);
        void MarcarEstado(string idMensaje, string estado);
        List<string> IdsNoLeidos(string yo, string otro);
        /// <summary>UIX-02: no leídos por contacto para la insignia (una sola query).</summary>
        IReadOnlyDictionary<string, int> ContarNoLeidosPorContacto(string yo);
        void MarcarLeidos(string yo, string otro);
        List<Dictionary<string, object?>> ListarPendientes();
        void RegistrarPendiente(string id, string tipo, string origen, string destino,
            string? contenido, string? nombreArchivo, byte[]? payload, string fechaCreado);
        void IncrementarIntento(string id, string ultimoError);
        void EliminarPendiente(string id);
    }
}
