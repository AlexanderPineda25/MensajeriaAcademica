package universidad.mensajeria.cliente.presentacion;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import universidad.mensajeria.cliente.fachada.InterfazArchivosConversacion;
import universidad.mensajeria.cliente.fachada.InterfazChatCliente;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;
import universidad.mensajeria.cliente.transversal.utilerias.FormatoRespuesta;
import universidad.mensajeria.cliente.transversal.utilerias.FormatoUsuario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * ViewModel del Chat (Fase 3 del Plan de Arquitectura):
 * Encapsula el estado observable de la vista, la lógica de presentación,
 * filtros y coordinación de casos de uso sin acceso directo a socket ni persistencia.
 */
public class ChatViewModel implements InterfazArchivosConversacion {

    private final InterfazChatCliente fachada;
    private final Executor tareasFondo;

    private final BooleanProperty enviando = new SimpleBooleanProperty(false);
    private final BooleanProperty adjuntoDisponible = new SimpleBooleanProperty(false);
    private final BooleanProperty hayDestino = new SimpleBooleanProperty(false);
    private final ObjectProperty<EstadoConexion> estadoConexion =
            new SimpleObjectProperty<>(EstadoConexion.SIN_RED);
    private final ObjectProperty<MensajeLocal> respuestaSeleccionada =
            new SimpleObjectProperty<>(null);
    private final Consumer<EstadoConexion> alCambiarEstado = estadoConexion::set;

    private final Map<String, UsuarioLocal> directorio = new HashMap<>();
    private final ObservableList<RecienteChat> recientes = FXCollections.observableArrayList();

    private byte[] adjuntoBytes;
    private String adjuntoNombre;
    private String adjuntoMime;
    private int offsetActual;

    public ChatViewModel(InterfazChatCliente fachada, Executor tareasFondo) {
        this.fachada = fachada;
        this.tareasFondo = tareasFondo;
        if (fachada != null) {
            this.estadoConexion.set(fachada.estadoConexion());
            fachada.alCambiarEstado(alCambiarEstado);
        }
    }

    public InterfazChatCliente fachada() {
        return fachada;
    }

    public Executor tareasFondo() {
        return tareasFondo;
    }

    public String codigoActual() {
        return fachada != null ? fachada.codigoActual() : null;
    }

    public BooleanProperty enviandoProperty() {
        return enviando;
    }

    public BooleanProperty adjuntoDisponibleProperty() {
        return adjuntoDisponible;
    }

    public BooleanProperty hayDestinoProperty() {
        return hayDestino;
    }

    public ObjectProperty<EstadoConexion> estadoConexionProperty() {
        return estadoConexion;
    }

    public ObjectProperty<MensajeLocal> respuestaSeleccionadaProperty() {
        return respuestaSeleccionada;
    }

    public Map<String, UsuarioLocal> directorio() {
        return directorio;
    }

    public ObservableList<RecienteChat> recientes() {
        return recientes;
    }

    public byte[] adjuntoBytes() {
        return adjuntoBytes;
    }

    public String adjuntoNombre() {
        return adjuntoNombre;
    }

    public String adjuntoMime() {
        return adjuntoMime;
    }

    public int offsetActual() {
        return offsetActual;
    }

    public void setOffsetActual(int offset) {
        this.offsetActual = Math.max(0, offset);
    }

    public void prepararAdjunto(byte[] bytes, String nombre, String mime) {
        this.adjuntoBytes = bytes;
        this.adjuntoNombre = nombre;
        this.adjuntoMime = mime;
        this.adjuntoDisponible.set(bytes != null && bytes.length > 0);
    }

    public void limpiarAdjunto() {
        prepararAdjunto(null, null, null);
    }

    public void seleccionarRespuesta(MensajeLocal mensaje) {
        this.respuestaSeleccionada.set(mensaje);
    }

    public MensajeLocal respuestaSeleccionada() {
        return respuestaSeleccionada.get();
    }

    public void cancelarRespuesta() {
        this.respuestaSeleccionada.set(null);
    }

    public static boolean puedeEnviar(String texto, boolean adjunto, boolean hayDestino, boolean enviando) {
        return hayDestino && !enviando && ((texto != null && !texto.isBlank()) || adjunto);
    }

    public String nombreVisible(String codigo) {
        return FormatoUsuario.visible(directorio.get(codigo), codigo);
    }

    public List<RecienteChat> filtrar(List<RecienteChat> lista, String query, String filtro) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<RecienteChat> resultado = new ArrayList<>();
        for (RecienteChat r : lista) {
            UsuarioLocal u = directorio.get(r.otro());
            String nombre = u == null ? "" : (u.nombres() + " " + u.apellidos());
            String preview = r.ultimoContenido() == null ? "" : r.ultimoContenido();
            if (!q.isEmpty() && !r.otro().toLowerCase(Locale.ROOT).contains(q)
                    && !nombre.toLowerCase(Locale.ROOT).contains(q)
                    && !preview.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            if ("Conectados".equals(filtro) && !r.conectado()) {
                continue;
            }
            if ("No leídos".equals(filtro) && r.noLeidos() <= 0) {
                continue;
            }
            resultado.add(r);
        }
        return resultado;
    }

    public boolean enviarTexto(String destinatario, String texto) throws Exception {
        return enviarTexto(destinatario, texto, respuestaSeleccionada.get());
    }

    public boolean enviarTexto(String destinatario, String texto, MensajeLocal respuesta)
            throws Exception {
        if (destinatario == null || destinatario.isBlank()) {
            return false;
        }
        String cuerpo = texto;
        if (respuesta != null) {
            cuerpo = FormatoRespuesta.prefijo(respuesta, nombreVisible(respuesta.origen())) + texto;
        }
        return fachada.enviarTexto(destinatario, cuerpo);
    }

    public boolean enviarImagen(String destinatario, byte[] bytes, String nombre, String mime)
            throws Exception {
        return fachada.enviarImagen(destinatario, bytes, nombre, mime);
    }

    public boolean enviarArchivo(String destinatario, byte[] bytes, String nombre, String mime)
            throws Exception {
        return fachada.enviarArchivo(destinatario, bytes, nombre, mime);
    }

    public boolean enviarAdjunto(String destinatario, boolean esImagen) throws Exception {
        if (destinatario == null || adjuntoBytes == null) {
            return false;
        }
        if (esImagen) {
            return fachada.enviarImagen(destinatario, adjuntoBytes, adjuntoNombre, adjuntoMime);
        } else {
            return fachada.enviarArchivo(destinatario, adjuntoBytes, adjuntoNombre, adjuntoMime);
        }
    }

    public List<MensajeLocal> cargarConversacion(String otro, int limite, int offset) throws Exception {
        return fachada.conversacion(otro, limite, offset);
    }

    public int contarConversacion(String otro) throws Exception {
        return fachada.contarConversacion(otro);
    }

    public List<String> buscarHistorial(String otro, String patron) throws Exception {
        return fachada.buscarEnHistorial(otro, patron);
    }

    public void marcarLeidos(String otro) throws Exception {
        fachada.marcarLeidos(otro);
    }

    public void suscribirEventos(Consumer<EventoCliente> observador) {
        fachada.suscribirEventos(observador);
    }

    public void sincronizarDirectorio() throws Exception {
        fachada.sincronizarDirectorio();
    }

    public void refrescarPresencia() throws Exception {
        fachada.refrescarPresencia();
    }

    public List<UsuarioLocal> directorioLocal() throws Exception {
        return fachada.directorioLocal();
    }

    public List<RecienteChat> cargarRecientes() throws Exception {
        return fachada.recientes();
    }

    @Override
    public byte[] descargarImagen(String idMensaje) throws Exception {
        return fachada.descargarImagen(idMensaje);
    }

    @Override
    public PaginaHistorialRemota traerHistorial(String otro, int pagina) throws Exception {
        return fachada.traerHistorial(otro, pagina);
    }

    @Override
    public byte[] descargarContenidoArchivo(String archivoId) throws Exception {
        return fachada.descargarContenidoArchivo(archivoId);
    }

    public int cantidadObservadoresEventos() {
        return fachada.cantidadObservadoresEventos();
    }

    public boolean conectado() {
        return fachada.conectado();
    }

    public void desuscribirEventos(Consumer<EventoCliente> observador) {
        fachada.desuscribirEventos(observador);
    }

    public void alCambiarEstado(Consumer<EstadoConexion> observador) {
        fachada.alCambiarEstado(observador);
    }

    public void quitarObservadorEstado(Consumer<EstadoConexion> observador) {
        fachada.quitarObservadorEstado(observador);
    }

    public void detener() {
        if (fachada != null) {
            fachada.quitarObservadorEstado(alCambiarEstado);
        }
    }

    public void marcarLeidosEnFondo(String otro) {
        fachada.marcarLeidosFondo(otro);
    }

    public void desconectar() {
        fachada.desconectar();
    }

    public void logout() throws Exception {
        fachada.logout();
    }

    public void expulsarOtrasSesiones() throws Exception {
        fachada.expulsarOtrasSesiones();
    }

    public void cerrarSesionRemota() {
        fachada.cerrarSesionRemota();
    }
}
