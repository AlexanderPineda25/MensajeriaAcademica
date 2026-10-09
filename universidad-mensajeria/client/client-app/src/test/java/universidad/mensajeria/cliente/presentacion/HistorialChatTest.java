package universidad.mensajeria.cliente.presentacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import universidad.mensajeria.cliente.presentacion.celdas.FilaChat;
import universidad.mensajeria.cliente.transversal.records.EstadoMensaje;
import universidad.mensajeria.cliente.transversal.records.MensajeLocal;
import universidad.mensajeria.cliente.transversal.records.PaginaHistorialRemota;
import universidad.mensajeria.cliente.transversal.records.TipoMensajeCliente;

/**
 * Paginación y búsqueda del historial en sus propios colaboradores (SRP):
 * pintan filas, anclan el scroll, avisan de lo nuevo y saltan a coincidencias.
 */
class HistorialChatTest {

    private ListView<FilaChat> lista;
    private Button botonMas;
    private Button botonNuevos;
    private ProgressIndicator rueda;
    private HBox barraBuscar;
    private TextField campo;
    private Label contador;
    private List<String> estados;
    private AtomicInteger offset;
    private List<MensajeLocal> conversacion;
    private int totalLocal = 1;
    private List<String> busqueda = List.of("m1");
    private GestorFalso gestor;
    private SaltoBusquedaPendiente salto;
    private List<String> saltos;
    private String seleccionado = "B002";
    private BusquedaHistorialChat activa;

    private final Executor fondo = Runnable::run;

    @BeforeEach
    void montar() throws Exception {
        iniciarJavaFx();
        ejecutarEnJavaFx(() -> {
            lista = new ListView<>();
            botonMas = new Button();
            botonNuevos = new Button();
            rueda = new ProgressIndicator();
            barraBuscar = new HBox();
            campo = new TextField();
            contador = new Label();
            // Estado inicial del FXML: barras y avisos ocultos hasta usarse.
            botonMas.setVisible(false);
            botonMas.setManaged(false);
            botonNuevos.setVisible(false);
            botonNuevos.setManaged(false);
            rueda.setVisible(false);
            rueda.setManaged(false);
            barraBuscar.setVisible(false);
            barraBuscar.setManaged(false);
            return null;
        });
        estados = new ArrayList<>();
        offset = new AtomicInteger();
        conversacion = new ArrayList<>(List.of(mensaje("m1", "hola")));
        gestor = new GestorFalso();
        salto = new SaltoBusquedaPendiente();
        saltos = new ArrayList<>();
    }

    @Test
    void cargarConversacionPintaFilasYOcultaLaRueda() throws Exception {
        ejecutarEnJavaFx(() -> {
            paginador().cargarConversacion();
            return null;
        });
        vaciarColaFx();

        assertFalse(lista.getItems().isEmpty());
        assertFalse(botonMas.isVisible());
        assertFalse(rueda.isVisible());
    }

    @Test
    void cargarMasConLocalDisponibleAvanzaElOffset() throws Exception {
        totalLocal = 100;

        ejecutarEnJavaFx(() -> {
            paginador().cargarMas();
            return null;
        });
        vaciarColaFx();

        assertEquals(50, offset.get());
        assertFalse(lista.getItems().isEmpty());
    }

    @Test
    void irANuevosBajaAlFinalYOcultaElAviso() throws Exception {
        offset.set(50);

        ejecutarEnJavaFx(() -> {
            PaginadorHistorialChat p = paginador();
            p.notificarEntrante("B002", false);
            assertTrue(botonNuevos.isVisible());
            p.irANuevos();
            return null;
        });

        assertEquals(0, offset.get());
        assertFalse(botonNuevos.isVisible());
    }

    @Test
    void entranteConHistorialViejoNoArrastraElScroll() throws Exception {
        offset.set(50);

        ejecutarEnJavaFx(() -> {
            paginador().notificarEntrante("B002", false);
            return null;
        });

        assertTrue(botonNuevos.isVisible());
        assertEquals("↓ 1 nuevos", botonNuevos.getText());
    }

    @Test
    void saltoPendienteSeConsumeAlCargar() throws Exception {
        salto.programar("m1", 5);

        ejecutarEnJavaFx(() -> {
            paginador().cargarConversacion();
            return null;
        });
        vaciarColaFx();

        assertEquals(List.of("m1:5"), saltos);
        assertFalse(salto.haySalto());
    }

    @Test
    void mostrarSinConversacionAvisa() throws Exception {
        seleccionado = null;

        ejecutarEnJavaFx(() -> {
            busqueda().mostrar();
            return null;
        });

        assertEquals(1, estados.size());
        assertFalse(barraBuscar.isVisible());
    }

    @Test
    void buscarEncuentraCoincidenciaYCuenta() throws Exception {
        ejecutarEnJavaFx(() -> {
            activa = busqueda();
            campo.setText("hola");
            activa.buscarHistorialFondo();
            return null;
        });
        vaciarColaFx();
        String patron = ejecutarEnJavaFx(() -> activa.patronActual());

        assertEquals("hola", patron);
        assertEquals("1/1", contador.getText());
    }

    @Test
    void cerrarLimpiaYOculta() throws Exception {
        ejecutarEnJavaFx(() -> {
            activa = busqueda();
            campo.setText("hola");
            activa.buscarHistorialFondo();
            return null;
        });
        vaciarColaFx();
        String patron = ejecutarEnJavaFx(() -> {
            activa.cerrar();
            return activa.patronActual();
        });

        assertEquals("", patron);
        assertFalse(barraBuscar.isVisible());
        assertEquals("", contador.getText());
    }

    private PaginadorHistorialChat paginador() {
        return new PaginadorHistorialChat(lista, botonMas, botonNuevos, rueda,
                gestor, vista(), fondo, () -> seleccionado, estados::add,
                salto, (id, intentos) -> saltos.add(id + ":" + intentos), 50);
    }

    private BusquedaHistorialChat busqueda() {
        return new BusquedaHistorialChat(lista, botonMas, barraBuscar, campo, contador,
                vista(), fondo, () -> seleccionado, estados::add,
                () -> paginador().cargarMas(), salto, 300);
    }

    private InterfazChatViewModel vista() {
        return (InterfazChatViewModel) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{InterfazChatViewModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "offsetActual" -> offset.get();
                    case "setOffsetActual" -> { offset.set((int) args[0]); yield null; }
                    case "cargarConversacion" -> new ArrayList<>(conversacion);
                    case "contarConversacion" -> totalLocal;
                    case "buscarHistorial" -> new ArrayList<>(busqueda);
                    case "traerHistorial" -> new PaginaHistorialRemota(0, 0, 1);
                    default -> null;
                });
    }

    private static MensajeLocal mensaje(String id, String contenido) {
        return new MensajeLocal(id, "B002", "A001",
                TipoMensajeCliente.MENSAJE_TEXTO.name(), contenido, null,
                contenido.length(), 1, null, null, null,
                LocalDateTime.now().toString(), false, true,
                EstadoMensaje.ENTREGADO.name());
    }

    private static final class GestorFalso implements InterfazGestorAdjuntos {
        @Override
        public boolean imagenFallida(String idMensaje) {
            return false;
        }

        @Override
        public int paginaRemota(String conversacion) {
            return 0;
        }

        @Override
        public Integer totalPaginasRemotas(String conversacion) {
            return null;
        }

        @Override
        public void registrarPaginaRemota(String conversacion, int siguiente, int total) {
        }

        @Override
        public boolean hayMasHistorialRemoto(String conversacion) {
            return false;
        }

        @Override
        public void registrarPaginaDeMensaje(String idMensaje, int pagina) {
        }

        @Override
        public void cambiarConversacion() {
        }

        @Override
        public void detener() {
        }

        @Override
        public void cargarImagenVisible(MensajeLocal item) {
        }

        @Override
        public void reintentarImagen(MensajeLocal item) {
        }

        @Override
        public void descargarArchivo(MensajeLocal item) {
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

    /** Deja correr los `onSucceeded` que los `Task` publican en la cola FX. */
    private static void vaciarColaFx() throws Exception {
        ejecutarEnJavaFx(() -> null);
        ejecutarEnJavaFx(() -> null);
    }
}
