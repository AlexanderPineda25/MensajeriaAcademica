package universidad.mensajeria.cliente.presentacion;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import universidad.mensajeria.cliente.fachada.InterfazChatCliente;
import universidad.mensajeria.cliente.presentacion.celdas.MensajeCell;
import universidad.mensajeria.cliente.presentacion.celdas.UsuarioCell;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.EstadoConexion;
import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;
import universidad.mensajeria.cliente.presentacion.celdas.FilasChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;
import universidad.mensajeria.cliente.transversal.utilerias.FormatoRespuesta;
import universidad.mensajeria.cliente.transversal.utilerias.FormatoUsuario;
import universidad.mensajeria.cliente.transversal.records.EventoCliente;

/** Presentación JavaFX: no accede directamente a red ni a persistencia. */
public final class ChatController {

    private static final int LIMITE_PAGINA = 50;
    private static final Set<String> EXT_IMAGEN = Set.of("png", "jpg", "jpeg", "gif");
    private static final String PREF_ULTIMO = "ultimoChat";
    private static final int RESPALDO_SEGUNDOS = 30;
    private static final long INTERVALO_MINIMO_MS = 200;
    private static final Logger LOG = Logger.getLogger(ChatController.class.getName());

    @FXML private Label etiquetaSesion;
    @FXML private Label etiquetaChat;
    @FXML private Label etiquetaPresencia;
    @FXML private Label etiquetaEstado;
    @FXML private Label etiquetaRespuesta;
    @FXML private Label etiquetaAdjunto;
    @FXML private javafx.scene.layout.HBox barraRespuesta;
    @FXML private javafx.scene.layout.HBox barraAdjunto;
    @FXML private ComboBox<String> comboFiltro;
    @FXML private ComboBox<String> comboTema;
    @FXML private TextField campoBuscar;
    @FXML private javafx.scene.layout.HBox barraBuscar;
    @FXML private TextField campoBuscarHistorial;
    @FXML private Label etiquetaBuscarContador;
    @FXML private TextArea campoEntrada;
    @FXML private ListView<RecienteChat> listaUsuarios;
    @FXML private ListView<FilaChat> listaMensajes;
    @FXML private ImageView vistaPrevia;
    @FXML private Button botonMas;
    @FXML private Button botonNuevos;
    @FXML private javafx.scene.control.ProgressIndicator ruedaHistorial;
    @FXML private Button botonEnviar;
    @FXML private Label bannerOffline;

    private final Executor tareasFondo;
    private final InterfazNavegacionCliente navegacion;
    private GestorAdjuntosChat gestorAdjuntos;
    private Preferences prefs;
    private final BooleanProperty enviando;
    private final BooleanProperty adjuntoDisponible;
    private final BooleanProperty hayDestino;
    private final Map<String, UsuarioLocal> directorio;
    private final Consumer<EventoCliente> alEntrar = this::alMensajeEntrante;
    /** PAT-03: la vista se suscribe al estado; antes lo deducía sondeando. */
    private final Consumer<EstadoConexion> alCambiarEstado = this::pintarEstadoConexion;
    // UIX-06/UIX-07: búsqueda en el historial con debounce.
    private final List<String> idsBusqueda = new ArrayList<>();
    private int indiceBusqueda = -1;
    private String patronBusqueda = "";
    private String saltoPendiente = null;
    private int intentosSalto = 0;
    // UIX-16: mensajes que llegaron mientras se lee historial viejo.
    private int nuevosPendientes = 0;
    private PauseTransition pausaBuscarSidebar;
    private PauseTransition pausaBuscarHistorial;
    private int versionAdjunto;
    private Timeline respaldo;
    private boolean refrescoEnCurso;
    private boolean refrescoForzadoPendiente;
    private boolean cierreRemotoProcesado;
    private volatile boolean controladorDetenido;
    private volatile EstadoConexion ultimoEstadoConexion = EstadoConexion.SIN_RED;
    private long ultimoRefrescoMs;
    private final ObservableList<RecienteChat> todos;

    private final ChatViewModel viewModel;

    public ChatController(ChatViewModel viewModel, InterfazNavegacionCliente navegacion) {
        this.viewModel = viewModel;
        this.tareasFondo = viewModel != null ? viewModel.tareasFondo() : null;
        this.navegacion = navegacion;
        this.enviando = viewModel.enviandoProperty();
        this.adjuntoDisponible = viewModel.adjuntoDisponibleProperty();
        this.hayDestino = viewModel.hayDestinoProperty();
        this.directorio = viewModel.directorio();
        this.todos = viewModel.recientes();
    }

    public ChatController(InterfazChatCliente fachada, Executor tareasFondo,
                          InterfazNavegacionCliente navegacion) {
        this(new ChatViewModel(fachada, tareasFondo), navegacion);
    }

    public ChatViewModel viewModel() {
        return viewModel;
    }

    @FXML
    private void initialize() {
        prefs = Preferences.userNodeForPackage(ChatController.class);
        etiquetaSesion.setText("Sesión activa · " + nombreVisible(viewModel.codigoActual()));
        // UIX-07: debounce compartido para las dos búsquedas (300 ms).
        pausaBuscarSidebar = new PauseTransition(javafx.util.Duration.millis(300));
        pausaBuscarSidebar.setOnFinished(e -> aplicarFiltro());
        pausaBuscarHistorial = new PauseTransition(javafx.util.Duration.millis(300));
        pausaBuscarHistorial.setOnFinished(e -> buscarHistorialFondo());
        gestorAdjuntos = new GestorAdjuntosChat(viewModel, tareasFondo, listaMensajes,
                this::seleccionado, this::estado);
        listaUsuarios.setCellFactory(v -> new UsuarioCell(this::nombreVisible));
        listaMensajes.setCellFactory(v -> new MensajeCell(gestorAdjuntos::cargarImagenVisible,
                gestorAdjuntos::reintentarImagen, this::alResponder, this::nombreVisible,
                gestorAdjuntos::imagenFallida, gestorAdjuntos::descargarArchivo,
                () -> patronBusqueda));
        // UIX-10: al cambiar el ancho de la ventana las burbujas se reacomodan
        // (la celda calcula su ancho en cada pintado).
        listaMensajes.widthProperty().addListener((o, a, b) -> listaMensajes.refresh());
        // UIX-13: estados vacíos en vez de rectángulos en blanco.
        Label sinMensajes = new Label("Sin mensajes aún. Escribe el primero.");
        sinMensajes.getStyleClass().add("empty-placeholder");
        listaMensajes.setPlaceholder(sinMensajes);
        Label sinContactos = new Label("Sin contactos todavía.");
        sinContactos.getStyleClass().add("empty-placeholder");
        listaUsuarios.setPlaceholder(sinContactos);
        comboFiltro.setItems(FXCollections.observableArrayList("Todos", "Conectados", "No leídos"));
        comboFiltro.getSelectionModel().selectFirst();
        comboTema.setItems(FXCollections.observableArrayList("Oscuro", "Claro"));
        comboTema.getSelectionModel().select(
                Tema.CLARO.equals(Tema.cargar()) ? "Claro" : "Oscuro");
        comboTema.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            Tema.guardar("Claro".equals(b) ? Tema.CLARO : Tema.OSCURO);
            if (listaMensajes.getScene() != null) {
                navegacion.aplicarTema(listaMensajes.getScene(), false);
            }
        });
        campoBuscar.textProperty().addListener((o, a, b) -> {
            // UIX-07: sin debounce cada tecla reconstruía la lista de contactos.
            pausaBuscarSidebar.stop();
            pausaBuscarSidebar.playFromStart();
        });
        // UIX-06: Ctrl+F abre la búsqueda en el historial; Esc la cierra.
        campoBuscarHistorial.textProperty().addListener((o, a, b) -> {
            pausaBuscarHistorial.stop();
            pausaBuscarHistorial.playFromStart();
        });
        campoBuscarHistorial.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                alCerrarBuscar();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) alBuscarAnterior(); else alBuscarSiguiente();
                e.consume();
            }
        });
        listaMensajes.sceneProperty().addListener((o, anterior, escena) -> {
            if (escena != null) {
                escena.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                        this::mostrarBuscar);
            }
        });
        comboFiltro.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> aplicarFiltro());
        listaUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, anterior, actual) -> {
            hayDestino.set(actual != null);
            if (anterior != null && actual != null && !anterior.otro().equals(actual.otro())) {
                cancelarRespuesta();
            }
            if (actual != null) {
                prefs.put(PREF_ULTIMO + "_" + viewModel.codigoActual(), actual.otro());
                viewModel.setOffsetActual(0);
                // UIX-09: las descargas del chat anterior ya no pintan aquí.
                gestorAdjuntos.cambiarConversacion();
                // UIX-06: los resultados son por conversación; al cambiar, fuera.
                alCerrarBuscar();
                etiquetaChat.setText(nombreVisible(actual.otro()));
                etiquetaPresencia.setText(actual.conectado() ? "en línea" : "desconectado");
                cargarConversacionFondo();
                viewModel.marcarLeidosEnFondo(actual.otro());
            } else {
                etiquetaChat.setText("Selecciona una conversación");
                etiquetaPresencia.setText("");
            }
        });
        botonEnviar.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !puedeEnviar(campoEntrada.getText(), adjuntoDisponible.get(),
                        hayDestino.get(), enviando.get()),
                campoEntrada.textProperty(), enviando, adjuntoDisponible, hayDestino));
        viewModel.desuscribirEventos(alEntrar);
        viewModel.suscribirEventos(alEntrar);
        // PAT-03: entrega el estado actual de inmediato y avisa de cada cambio.
        viewModel.alCambiarEstado(alCambiarEstado);
        configurarDragDrop();
        actualizarBannerOffline();
        alActualizar();
        respaldo = new Timeline(new KeyFrame(Duration.seconds(RESPALDO_SEGUNDOS), e -> actualizarFondo()));
        respaldo.setCycleCount(Timeline.INDEFINITE);
        respaldo.play();
    }

    public void detener() {
        controladorDetenido = true;
        if (respaldo != null) respaldo.stop();
        refrescoEnCurso = false;
        if (gestorAdjuntos != null) {
            gestorAdjuntos.detener();
        }
        viewModel.desuscribirEventos(alEntrar);
        viewModel.quitarObservadorEstado(alCambiarEstado);
        viewModel.detener();
    }

    public static boolean puedeEnviar(String texto, boolean imagen, boolean hayDestino, boolean enviando) {
        return ChatViewModel.puedeEnviar(texto, imagen, hayDestino, enviando);
    }

    private void aplicarFiltro() {
        String filtro = comboFiltro.getSelectionModel().getSelectedItem();
        ObservableList<RecienteChat> vista = FXCollections.observableArrayList(
                viewModel.filtrar(todos, campoBuscar.getText(), filtro));
        RecienteChat sel = listaUsuarios.getSelectionModel().getSelectedItem();
        listaUsuarios.setItems(vista);
        // UIX-13: si el filtro no deja nada, decir por qué en vez de blanco.
        if (vista.isEmpty()) {
            Label aviso = new Label(todos.isEmpty()
                    ? "Sin contactos todavía."
                    : "Sin resultados para esta búsqueda.");
            aviso.getStyleClass().add("empty-placeholder");
            aviso.setWrapText(true);
            listaUsuarios.setPlaceholder(aviso);
        }
        if (sel != null) {
            for (RecienteChat r : vista) {
                if (r.otro().equals(sel.otro())) {
                    listaUsuarios.getSelectionModel().select(r);
                    return;
                }
            }
        }
    }

    private String seleccionado() {
        RecienteChat r = listaUsuarios.getSelectionModel().getSelectedItem();
        return r == null ? null : r.otro();
    }

    public String nombreVisible(String codigo) {
        return FormatoUsuario.visible(directorio.get(codigo), codigo);
    }

    private void actualizarEncabezado() {
        String codigo = seleccionado();
        if (codigo == null) return;
        etiquetaChat.setText(nombreVisible(codigo));
        etiquetaPresencia.setText(todos.stream().filter(r -> r.otro().equals(codigo))
                .findFirst().map(r -> r.conectado() ? "en línea" : "desconectado").orElse(""));
    }

    @FXML
    private void alEnviar() {
        String destino = seleccionado();
        String texto = campoEntrada.getText() == null ? "" : campoEntrada.getText().trim();
        byte[] adjunto = viewModel.adjuntoBytes();
        byte[] imagen = adjunto == null ? null : adjunto.clone();
        String nombre = viewModel.adjuntoNombre();
        if (!puedeEnviar(texto, imagen != null, destino != null, enviando.get())) return;
        MensajeLocal respuesta = viewModel.respuestaSeleccionada();
        final String contenido = texto;
        enviando.set(true);
        campoEntrada.clear();
        limpiarImagen();
        cancelarRespuesta();
        Task<Boolean> tarea = new Task<>() {
            @Override protected Boolean call() throws Exception {
                boolean enRed = true;
                if (!contenido.isBlank()) {
                    enRed = viewModel.enviarTexto(destino, contenido, respuesta);
                }
                if (imagen != null) {
                    String ext = extension(nombre);
                    if (EXT_IMAGEN.contains(ext)) {
                        enRed = viewModel.enviarImagen(destino, imagen, nombre,
                                "image/" + ext) && enRed;
                    } else {
                        enRed = viewModel.enviarArchivo(destino, imagen, nombre,
                                mimeDe(nombre)) && enRed;
                    }
                }
                return enRed;
            }
        };
        tarea.setOnSucceeded(e -> {
            enviando.set(false);
            estado(tarea.getValue() ? "Enviado" : "Sin red: guardado para reintentar");
            cargarConversacionFondo();
        });
        tarea.setOnFailed(e -> {
            enviando.set(false);
            estado("No se completó el envío: " + mensaje(tarea.getException()));
            cargarConversacionFondo();
        });
        tareasFondo.execute(tarea);
    }

    @FXML
    private void alTeclaEntrada(KeyEvent evento) {
        if (evento.getCode() == KeyCode.ESCAPE) {
            cancelarRespuesta();
            limpiarImagen();
            evento.consume();
            return;
        }
        if (evento.getCode() == KeyCode.ENTER && !evento.isShiftDown()
                && botonEnviar != null && !botonEnviar.isDisabled()) {
            evento.consume();
            alEnviar();
        }
    }

    private void alResponder(MensajeLocal mensaje) {
        viewModel.seleccionarRespuesta(mensaje);
        String quote = FormatoRespuesta.prefijo(mensaje, nombreVisible(mensaje.origen())).trim();
        etiquetaRespuesta.setText(quote.length() > 150 ? quote.substring(0, 147) + "…" : quote);
        barraRespuesta.setVisible(true);
        barraRespuesta.setManaged(true);
    }

    @FXML private void alCancelarRespuesta() { cancelarRespuesta(); }

    private void cancelarRespuesta() {
        viewModel.cancelarRespuesta();
        if (barraRespuesta != null) {
            barraRespuesta.setVisible(false);
            barraRespuesta.setManaged(false);
        }
    }

    @FXML
    private void alElegirImagen() {
        FileChooser elegidor = new FileChooser();
        elegidor.setTitle("Elegir archivo (imagen, PDF, documento...)");
        elegidor.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Todos", "*.*"),
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("Documentos", "*.pdf", "*.txt", "*.docx", "*.xlsx", "*.pptx", "*.zip"));
        cargarArchivo(elegidor.showOpenDialog(listaMensajes.getScene().getWindow()));
    }

    @FXML private void alQuitarImagen() { limpiarImagen(); }

    private void limpiarImagen() {
        versionAdjunto++;
        viewModel.limpiarAdjunto();
        vistaPrevia.setImage(null);
        barraAdjunto.setVisible(false);
        barraAdjunto.setManaged(false);
    }

    private void cargarArchivo(File archivo) {
        if (archivo == null) return;
        int version = ++versionAdjunto;
        Task<byte[]> tarea = new Task<>() {
            @Override protected byte[] call() throws Exception {
                byte[] bytes = Files.readAllBytes(archivo.toPath());
                if (bytes.length > 50 * 1024 * 1024) throw new IllegalStateException("Máximo 50 MiB");
                return bytes;
            }
        };
        tarea.setOnSucceeded(e -> {
            if (version != versionAdjunto) return;
            byte[] bytesArchivo = tarea.getValue();
            viewModel.prepararAdjunto(bytesArchivo, archivo.getName(), mimeDe(archivo.getName()));
            etiquetaAdjunto.setText(viewModel.adjuntoNombre());
            barraAdjunto.setVisible(true);
            barraAdjunto.setManaged(true);
            boolean esImagen = EXT_IMAGEN.contains(extension(archivo.getName()));
            Task<Image> preview = new Task<>() {
                @Override protected Image call() {
                    if (!esImagen) {
                        return null;
                    }
                    return new Image(new java.io.ByteArrayInputStream(bytesArchivo), 120, 0, true, true);
                }
            };
            preview.setOnSucceeded(done -> {
                if (version != versionAdjunto) return;
                vistaPrevia.setImage(preview.getValue());
                etiquetaAdjunto.setText(viewModel.adjuntoNombre()
                        + (esImagen ? "" : " (archivo)"));
            });
            tareasFondo.execute(preview);
        });
        tarea.setOnFailed(e -> estado("No se pudo leer el archivo: " + mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    private void configurarDragDrop() {
        listaMensajes.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        listaMensajes.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean ok = false;
            if (db.hasFiles()) {
                for (File f : db.getFiles()) {
                    if (f.isFile()) {
                        cargarArchivo(f);
                        ok = true;
                        break;
                    }
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    @FXML
    private void alActualizar() {
        estado("Actualizando directorio y presencia…");
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() {
                try { viewModel.sincronizarDirectorio(); }
                catch (Exception e) { LOG.fine(() -> "directorio remoto omitido: " + mensaje(e)); }
                try { viewModel.refrescarPresencia(); }
                catch (Exception e) { LOG.fine(() -> "presencia manual omitida: " + mensaje(e)); }
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            actualizarFondo();
            if (seleccionado() == null) restaurarUltimo();
        });
        tareasFondo.execute(tarea);
    }

    private record Carga(String sel, List<RecienteChat> recientes, List<UsuarioLocal> usuarios,
                         List<MensajeLocal> pagina, int total, int offset) { }

    private void actualizarFondo() {
        actualizarFondo(false);
    }

    /**
     * Los acuses (ENTREGADO/LEIDO) deben repintar el tick en vivo: el refresco
     * normal se estrangula a 200 ms y un acuse que llegue en rafaga se perdia.
     * El forzado salta el estrangulamiento y, si hay un refresco en curso,
     * encadena otro al terminar para no pintar estado anterior al acuse.
     */
    private void actualizarFondoForzado() {
        refrescoForzadoPendiente = true;
        actualizarFondo(true);
    }

    private void actualizarFondo(boolean forzado) {
        long ahora = System.currentTimeMillis();
        if (refrescoEnCurso) {
            return;
        }
        if (!forzado && ahora - ultimoRefrescoMs < INTERVALO_MINIMO_MS) return;
        refrescoEnCurso = true;
        ultimoRefrescoMs = ahora;
        String sel = seleccionado();
        int off = viewModel.offsetActual();
        long inicio = System.nanoTime();
        Task<Carga> tarea = new Task<>() {
            @Override protected Carga call() throws Exception {
                List<UsuarioLocal> usuarios = viewModel.directorioLocal();
                List<RecienteChat> rec = viewModel.cargarRecientes();
                if (sel == null) return new Carga(null, rec, usuarios, List.of(), 0, off);
                List<MensajeLocal> pag = viewModel.cargarConversacion(sel, LIMITE_PAGINA, off);
                return new Carga(sel, rec, usuarios, pag, viewModel.contarConversacion(sel), off);
            }
        };
        tarea.setOnSucceeded(e -> {
            refrescoEnCurso = false;
            if (refrescoForzadoPendiente) {
                refrescoForzadoPendiente = false;
                actualizarFondo(true);
                return;
            }
            Carga c = tarea.getValue();
            directorio.clear();
            for (UsuarioLocal u : c.usuarios()) directorio.put(u.codigo(), u);
            etiquetaSesion.setText("Sesión activa · " + nombreVisible(viewModel.codigoActual()));
            todos.setAll(c.recientes());
            aplicarFiltro();
            restaurarSeleccion(c.sel());
            actualizarEncabezado();
            pintarPagina(c.sel(), c.pagina(), c.total(), c.offset());
            listaMensajes.refresh();
            estado("Recientes: " + c.recientes().size());
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            if (ms > 200 || viewModel.cantidadObservadoresEventos() > 1) {
                LOG.info(() -> "[perf] refresco local=" + ms + "ms subs="
                        + viewModel.cantidadObservadoresEventos());
            }
        });
        tarea.setOnFailed(e -> {
            refrescoEnCurso = false;
            if (refrescoForzadoPendiente) {
                refrescoForzadoPendiente = false;
                actualizarFondo(true);
                return;
            }
            LOG.fine(() -> "refresco local omitido: " + mensaje(tarea.getException()));
        });
        tareasFondo.execute(tarea);
    }

    private void restaurarSeleccion(String sel) {
        if (sel == null) return;
        for (RecienteChat r : listaUsuarios.getItems()) {
            if (r.otro().equals(sel)) {
                listaUsuarios.getSelectionModel().select(r);
                return;
            }
        }
    }

    private void restaurarUltimo() {
        try {
            String ultimo = prefs.get(PREF_ULTIMO + "_" + viewModel.codigoActual(), null);
            if (ultimo == null) return;
            for (RecienteChat r : listaUsuarios.getItems()) {
                if (r.otro().equals(ultimo)) {
                    listaUsuarios.getSelectionModel().select(r);
                    return;
                }
            }
        } catch (Exception ignorada) { /* preferencia opcional */ }
    }

    @FXML private void alCargarMas() { cargarMas(); }

    /** UIX-16: baja a lo nuevo sin perder el punto de lectura anterior. */
    @FXML private void alIrANuevos() {
        nuevosPendientes = 0;
        botonNuevos.setVisible(false);
        botonNuevos.setManaged(false);
        viewModel.setOffsetActual(0);
        cargarConversacionFondo();
    }

    private void cargarMas() {
        String otro = seleccionado();
        if (otro == null) return;
        int mostrados = listaMensajes.getItems().size();
        Task<Boolean> tarea = new Task<>() {
            @Override protected Boolean call() throws Exception {
                int totalLocal = viewModel.contarConversacion(otro);
                if (viewModel.offsetActual() + LIMITE_PAGINA < totalLocal) {
                    viewModel.setOffsetActual(viewModel.offsetActual() + LIMITE_PAGINA);
                    return false;
                }
                int siguiente = gestorAdjuntos.paginaRemota(otro);
                Integer total = gestorAdjuntos.totalPaginasRemotas(otro);
                if (total != null && siguiente >= total) return null;
                PaginaHistorialRemota remota = viewModel.traerHistorial(otro, siguiente);
                gestorAdjuntos.registrarPaginaRemota(otro, siguiente + 1,
                        remota.totalPaginas());
                viewModel.setOffsetActual(mostrados);
                return true;
            }
        };
        tarea.setOnSucceeded(e -> {
            Boolean trajo = tarea.getValue();
            if (trajo == null) estado("No hay más historial"); else cargarConversacionFondo();
        });
        tarea.setOnFailed(e -> estado("Error al cargar historial: " + mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    // ------------------------------------------------- UIX-06: buscar en el historial

    private void mostrarBuscar() {
        if (seleccionado() == null) {
            estado("Elige una conversación para buscar en su historial.");
            return;
        }
        barraBuscar.setVisible(true);
        barraBuscar.setManaged(true);
        campoBuscarHistorial.requestFocus();
        campoBuscarHistorial.selectAll();
    }

    @FXML private void alCerrarBuscar() {
        barraBuscar.setVisible(false);
        barraBuscar.setManaged(false);
        campoBuscarHistorial.clear();
        patronBusqueda = "";
        idsBusqueda.clear();
        indiceBusqueda = -1;
        saltoPendiente = null;
        etiquetaBuscarContador.setText("");
        listaMensajes.refresh();
    }

    @FXML private void alBuscarSiguiente() {
        if (idsBusqueda.isEmpty()) return;
        indiceBusqueda = (indiceBusqueda + 1) % idsBusqueda.size();
        actualizarContadorBusqueda();
        saltarA(idsBusqueda.get(indiceBusqueda), 5);
    }

    @FXML private void alBuscarAnterior() {
        if (idsBusqueda.isEmpty()) return;
        indiceBusqueda = (indiceBusqueda - 1 + idsBusqueda.size()) % idsBusqueda.size();
        actualizarContadorBusqueda();
        saltarA(idsBusqueda.get(indiceBusqueda), 5);
    }

    private void actualizarContadorBusqueda() {
        if (idsBusqueda.isEmpty()) {
            etiquetaBuscarContador.setText(patronBusqueda.isBlank() ? "" : "Sin resultados");
        } else {
            etiquetaBuscarContador.setText((indiceBusqueda + 1) + "/" + idsBusqueda.size());
        }
    }

    private void buscarHistorialFondo() {
        String otro = seleccionado();
        String patron = campoBuscarHistorial.getText() == null
                ? "" : campoBuscarHistorial.getText().trim();
        if (otro == null || patron.isEmpty()) {
            patronBusqueda = "";
            idsBusqueda.clear();
            indiceBusqueda = -1;
            actualizarContadorBusqueda();
            listaMensajes.refresh();
            return;
        }
        Task<List<String>> tarea = new Task<>() {
            @Override protected List<String> call() throws Exception {
                return viewModel.buscarHistorial(otro, patron);
            }
        };
        tarea.setOnSucceeded(e -> {
            if (!patron.equals(campoBuscarHistorial.getText() == null
                    ? "" : campoBuscarHistorial.getText().trim())) {
                return;
            }
            patronBusqueda = patron;
            idsBusqueda.clear();
            idsBusqueda.addAll(tarea.getValue());
            indiceBusqueda = idsBusqueda.isEmpty() ? -1 : 0;
            actualizarContadorBusqueda();
            listaMensajes.refresh();
            if (!idsBusqueda.isEmpty()) {
                saltarA(idsBusqueda.get(0), 5);
            }
        });
        tarea.setOnFailed(e -> estado("No se pudo buscar: " + mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    /**
     * Salta al mensaje: si está cargado, scroll + selección; si no, carga una
     * página más y reintenta (acotado). El resaltado lo pinta la celda con el
     * patrón activo.
     */
    private void saltarA(String id, int intentos) {
        String otro = seleccionado();
        if (otro == null || id == null) return;
        for (int i = 0; i < listaMensajes.getItems().size(); i++) {
            FilaChat fila = listaMensajes.getItems().get(i);
            if (fila instanceof FilaChat.MensajeFila mf && id.equals(mf.mensaje().idMensaje())) {
                listaMensajes.scrollTo(i);
                listaMensajes.getSelectionModel().select(i);
                return;
            }
        }
        if (intentos <= 0 || !botonMas.isVisible()) {
            estado("La coincidencia no está en el historial cargado.");
            return;
        }
        saltoPendiente = id;
        intentosSalto = intentos - 1;
        alCargarMas();
    }

    private void pintarPagina(String otro, List<MensajeLocal> pagina, int total, int off) {
        if (otro == null) return;
        for (int i = 0; i < pagina.size(); i++) {
            MensajeLocal m = pagina.get(i);
            int rankDesdeMasNuevo = off + pagina.size() - 1 - i;
            gestorAdjuntos.registrarPaginaDeMensaje(
                    m.idMensaje(), rankDesdeMasNuevo / LIMITE_PAGINA);
        }
        // UIX-04/UIX-05: la lista muestra filas (mensajes + separadores de día),
        // no mensajes sueltos. Se reconstruye desde todos los mensajes cargados
        // para que las cabeceras y las rachas queden bien también al anteponer
        // páginas viejas (el anclaje fino del scroll es UIX-16, Lote D).
        List<MensajeLocal> mensajes = new ArrayList<>(pagina);
        int filasAntes = listaMensajes.getItems().size();
        if (off > 0) {
            Set<String> existentes = new HashSet<>();
            List<MensajeLocal> actuales = new ArrayList<>();
            for (FilaChat fila : listaMensajes.getItems()) {
                if (fila instanceof FilaChat.MensajeFila mf) {
                    existentes.add(mf.mensaje().idMensaje());
                    actuales.add(mf.mensaje());
                }
            }
            mensajes.removeIf(m -> existentes.contains(m.idMensaje()));
            mensajes.addAll(actuales);
        }
        listaMensajes.getItems().setAll(FilasChat.armar(mensajes));
        if (off == 0 && !listaMensajes.getItems().isEmpty()) {
            listaMensajes.scrollTo(listaMensajes.getItems().size() - 1);
            nuevosPendientes = 0;
            botonNuevos.setVisible(false);
            botonNuevos.setManaged(false);
        } else if (off > 0) {
            // UIX-16: ancla en el primer mensaje que ya se veía: las filas
            // nuevas van todas encima, así que su índice sube justo eso.
            int nuevas = listaMensajes.getItems().size() - filasAntes;
            if (nuevas > 0) {
                listaMensajes.scrollTo(Math.min(nuevas, listaMensajes.getItems().size() - 1));
            }
        }
        boolean mas = total > listaMensajes.getItems().size()
                || gestorAdjuntos.hayMasHistorialRemoto(otro);
        botonMas.setVisible(mas);
        botonMas.setManaged(mas);
    }

    private void cargarConversacionFondo() {
        String otro = seleccionado();
        if (otro == null) return;
        int off = viewModel.offsetActual();
        // UIX-14: la rueda gira mientras el historial se trae en fondo.
        ruedaHistorial.setVisible(true);
        ruedaHistorial.setManaged(true);
        Task<Carga> tarea = new Task<>() {
            @Override protected Carga call() throws Exception {
                List<MensajeLocal> pag = viewModel.cargarConversacion(otro, LIMITE_PAGINA, off);
                return new Carga(otro, List.of(), List.of(), pag,
                        viewModel.contarConversacion(otro), off);
            }
        };
        tarea.setOnSucceeded(e -> {
            ruedaHistorial.setVisible(false);
            ruedaHistorial.setManaged(false);
            pintarPagina(otro, tarea.getValue().pagina(), tarea.getValue().total(), off);
            // UIX-06: si hay un salto pendiente (búsqueda), se reintenta ahora
            // que hay más mensajes cargados.
            if (saltoPendiente != null) {
                String id = saltoPendiente;
                int intentos = intentosSalto;
                saltoPendiente = null;
                saltarA(id, intentos);
            }
        });
        tarea.setOnFailed(e -> {
            ruedaHistorial.setVisible(false);
            ruedaHistorial.setManaged(false);
            estado("Error: " + mensaje(tarea.getException()));
        });
        tareasFondo.execute(tarea);
    }

    private void marcarLeidosAsync(String otro) { viewModel.marcarLeidosEnFondo(otro); }

    private void alMensajeEntrante(EventoCliente evento) {
        if (evento.tipo() == EventoCliente.Tipo.PRESENCIA) {
            Platform.runLater(this::actualizarFondo);
            return;
        }
        Platform.runLater(() -> {
            if (evento.tipo() == EventoCliente.Tipo.SESION_CERRADA) {
                manejarCierreSesion();
                return;
            }
            String afectado = evento.remitente() != null
                    ? evento.remitente() : evento.destinatario();
            String sel = seleccionado();
            if (evento.esAcuse()) {
                actualizarFondoForzado();
            } else {
                actualizarFondo();
            }
            // UIX-16: si se lee historial viejo, lo nuevo no arrastra el scroll:
            // se avisa con el botón y se baja al pulsarlo.
            if (sel != null && sel.equals(afectado) && !evento.esAcuse()
                    && viewModel.offsetActual() > 0) {
                nuevosPendientes++;
                botonNuevos.setText("↓ " + nuevosPendientes + " nuevos");
                botonNuevos.setVisible(true);
                botonNuevos.setManaged(true);
            }
            if (sel != null && sel.equals(afectado) && !evento.esAcuse()) {
                marcarLeidosAsync(sel);
            }
        });
    }

    @FXML
    private void alKick() {
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception {
                viewModel.expulsarOtrasSesiones();
                return null;
            }
        };
        tarea.setOnSucceeded(e -> estado("Se solicitó cerrar tus otras sesiones"));
        tarea.setOnFailed(e -> estado("Error: " + mensaje(tarea.getException())));
        tareasFondo.execute(tarea);
    }

    @FXML private void alSalir() {
        if (navegacion.confirmar("¿Deseas cerrar esta sesión y volver al inicio?")) {
            salirAlInicio(false);
        }
    }

    @FXML
    private void alSalirTodos() {
        if (navegacion.confirmar("¿Deseas cerrar esta sesión y cerrar también las otras sesiones de tu cuenta?")) {
            salirAlInicio(true);
        }
    }

    void salirAlInicio() {
        salirAlInicio(false);
    }

    private void salirAlInicio(boolean todasLasSesiones) {
        detener();
        Task<Void> tarea = new Task<>() {
            @Override protected Void call() throws Exception {
                if (todasLasSesiones) viewModel.expulsarOtrasSesiones();
                try { viewModel.logout(); } catch (Exception ignorada) { viewModel.desconectar(); }
                return null;
            }
        };
        tarea.setOnSucceeded(e -> { try { navegacion.mostrarLogin(); } catch (Exception err) { estado(mensaje(err)); } });
        tareasFondo.execute(tarea);
    }

    private void estado(String texto) {
        etiquetaEstado.setText(texto);
        actualizarBannerOffline();
    }

    private void actualizarBannerOffline() {
        if (bannerOffline == null || viewModel == null) {
            return;
        }
        boolean sinRed = !viewModel.conectado();
        bannerOffline.setVisible(sinRed);
        bannerOffline.setManaged(sinRed);
    }

    /**
     * PAT-03/UIX-00c: pinta el estado que publica la fachada. Llega en el hilo
     * de red, así que el salto a JavaFX es obligatorio. El banner ámbar aparece
     * en cuanto cae el socket, sin esperar al sondeo de 30 s.
     */
    private void pintarEstadoConexion(EstadoConexion estado) {
        boolean restaurada = ultimoEstadoConexion == EstadoConexion.SIN_RED
                && estado == EstadoConexion.CONECTADO;
        ultimoEstadoConexion = estado;
        Platform.runLater(() -> {
            if (estado == EstadoConexion.SESION_CERRADA && !controladorDetenido) {
                manejarCierreSesion();
                return;
            }
            if (bannerOffline != null) {
                bannerOffline.setVisible(estado.muestraBanner());
                bannerOffline.setManaged(estado.muestraBanner());
                if (estado.muestraBanner()) {
                    bannerOffline.setText(estado.texto());
                }
            }
            if (etiquetaEstado != null && !estado.equals(EstadoConexion.CONECTADO)) {
                etiquetaEstado.setText(estado.texto());
            }
            if (restaurada && !controladorDetenido) alActualizar();
        });
    }

    private void manejarCierreSesion() {
        if (cierreRemotoProcesado || controladorDetenido) return;
        cierreRemotoProcesado = true;
        detener();
        viewModel.cerrarSesionRemota();
        navegacion.avisarProximoLogin("Tu sesión se cerró. Inicia sesión nuevamente.");
        try {
            navegacion.mostrarLogin();
        } catch (Exception e) {
            LOG.warning("No se pudo volver al inicio de sesión.");
        }
    }

    private static String mensaje(Throwable e) {
        return FormatoErrorCliente.mensaje(e);
    }

    private static String extension(String nombre) {
        int punto = nombre == null ? -1 : nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static String mimeDe(String nombre) {
        String ext = extension(nombre);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "zip" -> "application/zip";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }
}
