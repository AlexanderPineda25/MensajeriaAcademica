package universidad.mensajeria.cliente.fachada;

import java.util.List;
import java.util.function.Consumer;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

/** Casos de uso que consume la pantalla de conversación. */
public interface InterfazChatCliente extends InterfazArchivosConversacion,
        InterfazEventosCliente {

    String codigoActual();

    EstadoConexion estadoConexion();

    void alCambiarEstado(Consumer<EstadoConexion> observador);

    void quitarObservadorEstado(Consumer<EstadoConexion> observador);

    boolean conectado();

    void desconectar();

    void cerrarSesionRemota();

    void logout() throws Exception;

    void expulsarOtrasSesiones() throws Exception;

    List<UsuarioLocal> directorioLocal() throws Exception;

    List<UsuarioLocal> sincronizarDirectorio() throws Exception;

    List<RecienteChat> recientes() throws Exception;

    List<String> refrescarPresencia() throws Exception;

    void marcarLeidos(String otro) throws Exception;

    void marcarLeidosFondo(String otro);

    boolean enviarTexto(String destinatario, String contenido) throws Exception;

    boolean enviarImagen(String destinatario, byte[] bytes, String nombreArchivo,
                         String mime) throws Exception;

    boolean enviarArchivo(String destinatario, byte[] bytes, String nombreArchivo,
                          String mime) throws Exception;

    List<MensajeLocal> conversacion(String otro, int limite, int offset) throws Exception;

    int contarConversacion(String otro) throws Exception;

    List<String> buscarEnHistorial(String otro, String patron) throws Exception;
}
