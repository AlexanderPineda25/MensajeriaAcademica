package universidad.mensajeria.cliente.presentacion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import universidad.mensajeria.cliente.fachada.InterfazArchivosConversacion;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

/**
 * Contrato del ViewModel del chat (MVVM).
 * El controlador depende de esta abstracción, nunca del concreto (DIP).
 */
public interface InterfazChatViewModel extends InterfazArchivosConversacion {

    static boolean puedeEnviar(String texto, boolean adjunto, boolean hayDestino, boolean enviando) {
        return hayDestino && !enviando && ((texto != null && !texto.isBlank()) || adjunto);
    }

    Executor tareasFondo();

    BooleanProperty enviandoProperty();

    BooleanProperty adjuntoDisponibleProperty();

    BooleanProperty hayDestinoProperty();

    ObjectProperty<MensajeLocal> respuestaSeleccionadaProperty();

    javafx.beans.property.ObjectProperty<EstadoConexion> estadoConexionProperty();

    Map<String, UsuarioLocal> directorio();

    ObservableList<RecienteChat> recientes();

    byte[] adjuntoBytes();

    String adjuntoNombre();

    String adjuntoMime();

    int offsetActual();

    void setOffsetActual(int offset);

    void prepararAdjunto(byte[] bytes, String nombre, String mime);

    void limpiarAdjunto();

    void seleccionarRespuesta(MensajeLocal mensaje);

    MensajeLocal respuestaSeleccionada();

    void cancelarRespuesta();

    String nombreVisible(String codigo);

    List<RecienteChat> filtrar(List<RecienteChat> lista, String query, String filtro);

    boolean enviarTexto(String destinatario, String texto, MensajeLocal respuesta)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    boolean enviarAdjunto(String destinatario, boolean esImagen)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    List<MensajeLocal> cargarConversacion(String otro, int limite, int offset)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    int contarConversacion(String otro)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    List<String> buscarHistorial(String otro, String patron)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    List<UsuarioLocal> directorioLocal()
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    List<RecienteChat> cargarRecientes()
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    String codigoActual();

    void suscribirEventos(Consumer<EventoCliente> observador);

    void desuscribirEventos(Consumer<EventoCliente> observador);

    void alCambiarEstado(Consumer<EstadoConexion> observador);

    void quitarObservadorEstado(Consumer<EstadoConexion> observador);

    int cantidadObservadoresEventos();

    boolean conectado();

    void desconectar();

    void logout()
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    void expulsarOtrasSesiones()
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    void cerrarSesionRemota();

    void sincronizarDirectorio()
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    void refrescarPresencia()
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    boolean enviarTexto(String destinatario, String texto)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    boolean enviarImagen(String destinatario, byte[] bytes, String nombre, String mime)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    boolean enviarArchivo(String destinatario, byte[] bytes, String nombre, String mime)
            throws universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente,
            java.io.IOException;

    void marcarLeidosEnFondo(String otro);

    void detener();
}
