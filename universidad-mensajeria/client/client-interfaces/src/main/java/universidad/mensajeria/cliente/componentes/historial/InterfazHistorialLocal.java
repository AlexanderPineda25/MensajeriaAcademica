package universidad.mensajeria.cliente.componentes.historial;

import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PendienteEnvio;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;

import java.util.List;
import java.util.Optional;

/**
 * Historial local (InterfazHistorialLocal → adaptador H2; §5.2/§5.4 en
 * miniatura: el puerto es neutro, el motor es intercambiable).
 */
public interface InterfazHistorialLocal {

    /** Selecciona/abre el almacenamiento de la cuenta antes de autenticarla. */
    default void prepararCuenta(String codigo) throws ExcepcionCliente {
    }

    void guardar(MensajeLocal mensaje) throws ExcepcionCliente;

    List<MensajeLocal> pagina(String remitente, String destinatario, int limite, int offset) throws ExcepcionCliente;

    int contar(String remitente, String destinatario) throws ExcepcionCliente;

    /**
     * UIX-06: mensajes de la conversación cuyo texto o nombre de archivo
     * contienen el patrón (insensible a mayúsculas). Devuelve hasta
     * {@code limite}, de más nuevo a más viejo. La UI salta entre ellos.
     */
    List<MensajeLocal> buscar(String remitente, String destinatario, String patron, int limite)
            throws ExcepcionCliente;

    void marcarDescargado(String idMensaje, String rutaArchivo) throws ExcepcionCliente;

    void marcarEstado(String idMensaje, String estado) throws ExcepcionCliente;

    void marcarLeidosDe(String yo, String otro) throws ExcepcionCliente;

    List<String> idsNoLeidos(String yo, String otro) throws ExcepcionCliente;

    java.util.List<RecienteChat> recientes(String yo) throws ExcepcionCliente;

    /**
     * FASE 13.9: blob bajo demanda. La lista (`pagina`) ya no trae `contenido`
     * (13.8/13.9); los bytes solo se leen para la miniatura visible.
     */
    Optional<String> blobPorId(String idMensaje) throws ExcepcionCliente;

    Optional<String> archivoIdPorId(String idMensaje) throws ExcepcionCliente;

    void actualizarArchivoId(String idMensaje, String archivoId) throws ExcepcionCliente;

    /** Conserva las etapas del pipeline de imagen devueltas por el servidor. */
    void actualizarEtapasFiltrado(String idMensaje, String etapasFiltrado) throws ExcepcionCliente;

    /**
     * Guarda los bytes descargados bajo demanda (Fase 13.13, §7 HISTORIAL_LOCAL.md).
     */
    void actualizarContenido(String idMensaje, String base64) throws ExcepcionCliente;

    List<PendienteEnvio> pendientes() throws ExcepcionCliente;

    void registrarPendiente(PendienteEnvio pendiente) throws ExcepcionCliente;

    void incrementarIntento(String id, String ultimoError) throws ExcepcionCliente;

    void eliminarPendiente(String id) throws ExcepcionCliente;

    Optional<PendienteEnvio> pendientePorId(String id) throws ExcepcionCliente;
}
