package universidad.mensajeria.cliente.fachada;

import java.util.List;
import java.util.function.Consumer;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import java.io.IOException;
import universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente;
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

    void logout() throws ExcepcionCliente, IOException;

    void expulsarOtrasSesiones() throws ExcepcionCliente, IOException;

    List<UsuarioLocal> directorioLocal() throws ExcepcionCliente, IOException;

    List<UsuarioLocal> sincronizarDirectorio() throws ExcepcionCliente, IOException;

    List<RecienteChat> recientes() throws ExcepcionCliente, IOException;

    List<String> refrescarPresencia() throws ExcepcionCliente, IOException;

    void marcarLeidos(String otro) throws ExcepcionCliente, IOException;

    void marcarLeidosFondo(String otro);

    boolean enviarTexto(String destinatario, String contenido) throws ExcepcionCliente, IOException;

    boolean enviarImagen(String destinatario, byte[] bytes, String nombreArchivo,
                         String mime) throws ExcepcionCliente, IOException;

    boolean enviarArchivo(String destinatario, byte[] bytes, String nombreArchivo,
                          String mime) throws ExcepcionCliente, IOException;

    List<MensajeLocal> conversacion(String otro, int limite, int offset) throws ExcepcionCliente, IOException;

    int contarConversacion(String otro) throws ExcepcionCliente, IOException;

    List<String> buscarEnHistorial(String otro, String patron) throws ExcepcionCliente, IOException;
}
