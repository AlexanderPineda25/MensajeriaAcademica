package universidad.mensajeria.cliente.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.RecienteChat;
import universidad.mensajeria.cliente.transversal.records.UsuarioLocal;

/**
 * Los contactos y su refresco viven en su propio colaborador (SRP): filtra,
 * selecciona con última conversación recordada y refresca sin sondear de más.
 */
class RefrescadorContactosTest {

    private Label etiquetaSesion;
    private Label etiquetaChat;
    private Label etiquetaPresencia;
    private ComboBox<String> comboFiltro;
    private TextField campoBuscar;
    private ListView<RecienteChat> listaUsuarios;
    private Map<String, UsuarioLocal> directorio;
    private ObservableList<RecienteChat> todos;
    private BooleanProperty hayDestino;
    private List<String> estados;
    private List<RecienteChat> recientes;
    private List<UsuarioLocal> usuarios;
    private FlujoFalso flujo;
    private final Executor fondo = Runnable::run;

    @BeforeEach
    void montar() throws Exception {
        iniciarJavaFx();
        ejecutarEnJavaFx(() -> {
            etiquetaSesion = new Label();
            etiquetaChat = new Label();
            etiquetaPresencia = new Label();
            comboFiltro = new ComboBox<>();
            campoBuscar = new TextField();
            listaUsuarios = new ListView<>();
            todos = FXCollections.observableArrayList();
            hayDestino = new SimpleBooleanProperty(false);
            return null;
        });
        directorio = new HashMap<>();
        estados = new ArrayList<>();
        recientes = new ArrayList<>(List.of(
                new RecienteChat("B002", "hola", "MENSAJE_TEXTO",
                        "2026-10-07T10:00:00", 0, true)));
        usuarios = new ArrayList<>(List.of(
                new UsuarioLocal("B002", "Luis", "Díaz", "Sistemas",
                        true, "2026-01-01", "2026-01-01")));
        flujo = new FlujoFalso();
    }

    @Test
    void configurarPintaSesionYCargaRecientes() throws Exception {
        ejecutarEnJavaFx(() -> {
            refrescador().configurar();
            return null;
        });
        vaciarColaFx();

        assertEquals("Sesión activa · Ana [A001]", etiquetaSesion.getText());
        assertEquals(1, todos.size());
        assertTrue(estados.contains("Recientes: 1"));
    }

    @Test
    void aplicarFiltroMuestraLaVistaFiltrada() throws Exception {
        todos.addAll(recientes);

        ejecutarEnJavaFx(() -> {
            RefrescadorContactosChat r = refrescador();
            r.configurar();
            campoBuscar.setText("luis");
            r.aplicarFiltro();
            return null;
        });

        assertEquals(1, listaUsuarios.getItems().size());
    }

    @Test
    void seleccionarConversacionAvisaAlFlujo() throws Exception {
        todos.addAll(recientes);

        ejecutarEnJavaFx(() -> {
            RefrescadorContactosChat r = refrescador();
            r.configurar();
            r.aplicarFiltro();
            listaUsuarios.getSelectionModel().select(0);
            return null;
        });
        vaciarColaFx();

        assertEquals("B002", flujo.seleccionados.get(0));
        assertTrue(hayDestino.get());
        assertTrue(flujo.pintados.contains("B002"));
    }

    @Test
    void sinSeleccionMuestraElTextoVacio() throws Exception {
        ejecutarEnJavaFx(() -> {
            RefrescadorContactosChat r = refrescador();
            r.configurar();
            return null;
        });
        vaciarColaFx();
        ejecutarEnJavaFx(() -> {
            listaUsuarios.getSelectionModel().select(0);
            listaUsuarios.getSelectionModel().clearSelection();
            return null;
        });

        assertEquals("Selecciona una conversación", etiquetaChat.getText());
    }

    private RefrescadorContactosChat refrescador() {
        Preferences prefs = Preferences.userRoot().node(
                "prueba" + UUID.randomUUID().toString().replace("-", ""));
        return new RefrescadorContactosChat(etiquetaSesion, etiquetaChat, etiquetaPresencia,
                comboFiltro, campoBuscar, listaUsuarios, prefs, vista(), fondo,
                directorio, todos, hayDestino, this::seleccionado,
                codigo -> "Ana [A001]", estados::add, flujo, 300, 50,
                new RefrescadorContactosChat.TimelineRespaldo() {
                    @Override
                    public void iniciar(Runnable refresco) {
                    }

                    @Override
                    public void detener() {
                    }
                });
    }

    private String seleccionado() {
        RecienteChat actual = listaUsuarios.getSelectionModel().getSelectedItem();
        return actual == null ? null : actual.otro();
    }

    @SuppressWarnings("unchecked")
    private InterfazChatViewModel vista() {
        return (InterfazChatViewModel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{InterfazChatViewModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "codigoActual" -> "A001";
                    case "filtrar" -> new ArrayList<>((List<RecienteChat>) args[0]);
                    case "directorioLocal" -> new ArrayList<>(usuarios);
                    case "cargarRecientes" -> new ArrayList<>(recientes);
                    case "cargarConversacion" -> List.of();
                    case "contarConversacion" -> 0;
                    case "cantidadObservadoresEventos" -> 1;
                    case "offsetActual" -> 0;
                    default -> null;
                });
    }

    private static final class FlujoFalso implements FlujoConversacion {
        private final List<String> seleccionados = new ArrayList<>();
        private final List<String> pintados = new ArrayList<>();

        @Override
        public void alSeleccionar(RecienteChat actual) {
            seleccionados.add(actual.otro());
        }

        @Override
        public void pintar(String conversacion, List<MensajeLocal> pagina,
                           int total, int offset) {
            pintados.add(conversacion);
        }
    }

    private static void iniciarJavaFx() throws InterruptedException {
        CountDownLatch iniciado = new CountDownLatch(1);
        try {
            Platform.startup(iniciado::countDown);
            if (!iniciado.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX no inició dentro del tiempo esperado.");
            }
        } catch (IllegalStateException yaIniciado) {
            if (!"Toolkit already initialized".equals(yaIniciado.getMessage())) {
                throw yaIniciado;
            }
        }
    }

    private static <T> T ejecutarEnJavaFx(Callable<T> accion) throws Exception {
        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea);
        return tarea.get(20, TimeUnit.SECONDS);
    }

    private static void vaciarColaFx() throws Exception {
        ejecutarEnJavaFx(() -> null);
        ejecutarEnJavaFx(() -> null);
    }
}
