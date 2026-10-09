package universidad.mensajeria.cliente.presentacion;

import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Contactos y refresco del directorio con presencia (Fase 3).
 * <p>
 * Extraído de {@code ChatController} por responsabilidad única: filtro y
 * búsqueda de contactos, selección con última conversación recordada y
 * refresco local estrangulado (los acuses fuerzan repintado en vivo).
 * La conversación visible se entrega por {@link FlujoConversacion} (DIP).
 * </p>
 */
public final class RefrescadorContactosChat {

    private static final String PREF_ULTIMO = "ultimoChat";
    private static final long INTERVALO_MINIMO_MS = 200;

    private static final Logger LOG = Logger.getLogger(RefrescadorContactosChat.class.getName());

    private final Label etiquetaSesion;
    private final Label etiquetaChat;
    private final Label etiquetaPresencia;
    private final ComboBox<String> comboFiltro;
    private final TextField campoBuscar;
    private final ListView<RecienteChat> listaUsuarios;
    private final Preferences prefs;
    private final InterfazChatViewModel viewModel;
    private final Executor tareasFondo;
    private final Map<String, UsuarioLocal> directorio;
    private final ObservableList<RecienteChat> todos;
    private final BooleanProperty hayDestino;
    private final Supplier<String> seleccionado;
    private final Function<String, String> nombreVisible;
    private final Consumer<String> estado;
    private final FlujoConversacion flujo;
    private final PauseTransition pausaBuscar;
    private final TimelineRespaldo respaldo;
    private final int limitePagina;
    private boolean refrescoEnCurso;
    private boolean refrescoForzadoPendiente;
    private long ultimoRefrescoMs;

    public RefrescadorContactosChat(Label etiquetaSesion,
                                    Label etiquetaChat,
                                    Label etiquetaPresencia,
                                    ComboBox<String> comboFiltro,
                                    TextField campoBuscar,
                                    ListView<RecienteChat> listaUsuarios,
                                    Preferences prefs,
                                    InterfazChatViewModel viewModel,
                                    Executor tareasFondo,
                                    Map<String, UsuarioLocal> directorio,
                                    ObservableList<RecienteChat> todos,
                                    BooleanProperty hayDestino,
                                    Supplier<String> seleccionado,
                                    Function<String, String> nombreVisible,
                                    Consumer<String> estado,
                                    FlujoConversacion flujo,
                                    long debounceMs,
                                    int limitePagina,
                                    TimelineRespaldo respaldo) {
        this.etiquetaSesion = etiquetaSesion;
        this.etiquetaChat = etiquetaChat;
        this.etiquetaPresencia = etiquetaPresencia;
        this.comboFiltro = comboFiltro;
        this.campoBuscar = campoBuscar;
        this.listaUsuarios = listaUsuarios;
        this.prefs = prefs;
        this.viewModel = viewModel;
        this.tareasFondo = tareasFondo;
        this.directorio = directorio;
        this.todos = todos;
        this.hayDestino = hayDestino;
        this.seleccionado = seleccionado;
        this.nombreVisible = nombreVisible;
        this.estado = estado;
        this.flujo = flujo;
        this.pausaBuscar = new PauseTransition(javafx.util.Duration.millis(debounceMs));
        this.pausaBuscar.setOnFinished(e -> aplicarFiltro());
        this.limitePagina = limitePagina;
        this.respaldo = respaldo;
    }

    /** Filtros, debounce de búsqueda y selección de conversación. */
    public void configurar() {
        etiquetaSesion.setText("Sesión activa · " + nombreVisible.apply(viewModel.codigoActual()));
        comboFiltro.setItems(FXCollections.observableArrayList("Todos", "Conectados", "No leídos"));
        comboFiltro.getSelectionModel().selectFirst();
        comboFiltro.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> aplicarFiltro());
        campoBuscar.textProperty().addListener((o, a, b) -> {
            pausaBuscar.stop();
            pausaBuscar.playFromStart();
        });
        listaUsuarios.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, actual) -> {
                    hayDestino.set(actual != null);
                    if (anterior != null && actual != null
                            && !anterior.otro().equals(actual.otro())) {
                        viewModel.cancelarRespuesta();
                    }
                    if (actual != null) {
                        flujo.alSeleccionar(actual);
                    } else {
                        etiquetaChat.setText("Selecciona una conversación");
                        etiquetaPresencia.setText("");
                    }
                });
        respaldo.iniciar(this::actualizarFondo);
        actualizar();
    }

    public void detenerRespaldo() {
        respaldo.detener();
        refrescoEnCurso = false;
    }

    void aplicarFiltro() {
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

    void actualizarEncabezado() {
        String codigo = seleccionado.get();
        if (codigo == null) {
            return;
        }
        etiquetaChat.setText(nombreVisible.apply(codigo));
        etiquetaPresencia.setText(todos.stream().filter(r -> r.otro().equals(codigo))
                .findFirst().map(r -> r.conectado() ? "en línea" : "desconectado").orElse(""));
    }

    void actualizar() {
        estado.accept("Actualizando directorio y presencia…");
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() {
                try {
                    viewModel.sincronizarDirectorio();
                } catch (universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente
                        | java.io.IOException e) {
                    LOG.fine(() -> "directorio remoto omitido: "
                            + FormatoErrorCliente.mensaje(e));
                }
                try {
                    viewModel.refrescarPresencia();
                } catch (universidad.mensajeria.cliente.transversal.excepciones.ExcepcionCliente
                        | java.io.IOException e) {
                    LOG.fine(() -> "presencia manual omitida: "
                            + FormatoErrorCliente.mensaje(e));
                }
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            actualizarFondo();
            if (seleccionado.get() == null) {
                restaurarUltimo();
            }
        });
        tareasFondo.execute(tarea);
    }

    /**
     * Los acuses (ENTREGADO/LEIDO) deben repintar el tick en vivo: el refresco
     * normal se estrangula a 200 ms y un acuse que llegue en ráfaga se perdía.
     * El forzado salta el estrangulamiento y, si hay un refresco en curso,
     * encadena otro al terminar para no pintar estado anterior al acuse.
     */
    void actualizarFondoForzado() {
        refrescoForzadoPendiente = true;
        actualizarFondo(true);
    }

    void actualizarFondo() {
        actualizarFondo(false);
    }

    private void actualizarFondo(boolean forzado) {
        long ahora = System.currentTimeMillis();
        if (refrescoEnCurso) {
            return;
        }
        if (!forzado && ahora - ultimoRefrescoMs < INTERVALO_MINIMO_MS) {
            return;
        }
        refrescoEnCurso = true;
        ultimoRefrescoMs = ahora;
        String sel = seleccionado.get();
        int off = viewModel.offsetActual();
        long inicio = System.nanoTime();
        Task<Carga> tarea = new Task<>() {
            @Override
            protected Carga call() throws Exception {
                List<UsuarioLocal> usuarios = viewModel.directorioLocal();
                List<RecienteChat> rec = viewModel.cargarRecientes();
                if (sel == null) {
                    return new Carga(null, rec, usuarios, List.of(), 0, off);
                }
                List<MensajeLocal> pag = viewModel.cargarConversacion(sel, limitePagina, off);
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
            for (UsuarioLocal u : c.usuarios()) {
                directorio.put(u.codigo(), u);
            }
            etiquetaSesion.setText("Sesión activa · " + nombreVisible.apply(viewModel.codigoActual()));
            todos.setAll(c.recientes());
            aplicarFiltro();
            restaurarSeleccion(c.sel());
            actualizarEncabezado();
            flujo.pintar(c.sel(), c.pagina(), c.total(), c.offset());
            estado.accept("Recientes: " + c.recientes().size());
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
            LOG.fine(() -> "refresco local omitido: "
                    + FormatoErrorCliente.mensaje(tarea.getException()));
        });
        tareasFondo.execute(tarea);
    }

    private void restaurarSeleccion(String sel) {
        if (sel == null) {
            return;
        }
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
            if (ultimo == null) {
                return;
            }
            for (RecienteChat r : listaUsuarios.getItems()) {
                if (r.otro().equals(ultimo)) {
                    listaUsuarios.getSelectionModel().select(r);
                    return;
                }
            }
        } catch (SecurityException | IllegalStateException preferenciaOpcional) {
            LOG.fine(() -> "preferencia de último chat omitida: "
                    + preferenciaOpcional.getMessage());
        }
    }

    private record Carga(String sel, List<RecienteChat> recientes, List<UsuarioLocal> usuarios,
                         List<MensajeLocal> pagina, int total, int offset) {
    }

    /** Respaldo periódico del refresco (antes `Timeline` directo en el controlador). */
    public interface TimelineRespaldo {
        void iniciar(Runnable refresco);

        void detener();
    }
}
