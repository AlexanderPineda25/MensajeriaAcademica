package universidad.mensajeria.cliente.presentacion;

import org.junit.jupiter.api.Test;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import universidad.mensajeria.cliente.fachada.InterfazChatCliente;
import universidad.mensajeria.cliente.fachada.InterfazInicioSesion;
import universidad.mensajeria.cliente.transversal.config.ClienteConfig;

import java.lang.reflect.Proxy;
import java.util.Properties;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión para la fábrica de controladores y la inyección de dependencias.
 */
class FabricaControladoresTest {

    private final InterfazInicioSesion inicioSesion = (InterfazInicioSesion) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{InterfazInicioSesion.class},
            (proxy, method, args) -> null);

    private final InterfazChatCliente chat = (InterfazChatCliente) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{InterfazChatCliente.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "codigoActual" -> "160004726";
                case "estadoConexion" -> universidad.mensajeria.cliente.transversal.EstadoConexion.CONECTADO;
                case "conectado" -> true;
                case "directorioLocal", "sincronizarDirectorio", "recientes",
                        "refrescarPresencia", "conversacion", "buscarEnHistorial" -> List.of();
                case "contarConversacion", "cantidadObservadoresEventos" -> 0;
                default -> null;
            });

    private final Executor tareasFondo = Runnable::run;
    private final ClienteConfig config = ClienteConfig.desde(new Properties());
    private final InterfazNavegacionCliente navegacion = (InterfazNavegacionCliente) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{InterfazNavegacionCliente.class},
            (proxy, method, args) -> null);

    @Test
    void creaLoginControllerConDependencias() {
        FabricaControladores fabrica = new FabricaControladores(
                inicioSesion, chat, tareasFondo, config, navegacion);

        Object controller = fabrica.crear(LoginController.class);
        assertNotNull(controller);
        assertTrue(controller instanceof LoginController);
    }

    @Test
    void creaChatControllerConDependencias() {
        FabricaControladores fabrica = new FabricaControladores(
                inicioSesion, chat, tareasFondo, config, navegacion);

        Object controller = fabrica.crear(ChatController.class);
        assertNotNull(controller);
        assertTrue(controller instanceof ChatController);
    }

    @Test
    void claseDesconocidaLanzaIllegalArgumentException() {
        FabricaControladores fabrica = new FabricaControladores(
                inicioSesion, chat, tareasFondo, config, navegacion);

        assertThrows(IllegalArgumentException.class, () -> fabrica.crear(String.class));
    }

    @Test
    void cargaVistasConLaFabricaDeControladores() throws Exception {
        iniciarJavaFx();
        ejecutarEnJavaFx(() -> {
            FabricaControladores fabrica = new FabricaControladores(
                    inicioSesion, chat, tareasFondo, config, navegacion);
            FXMLLoader loginLoader = crearLoader("/vistas/login.fxml", fabrica);
            Parent login = loginLoader.load();
            assertNotNull(login);
            assertTrue(loginLoader.getController() instanceof LoginController);

            FXMLLoader chatLoader = crearLoader("/vistas/chat.fxml", fabrica);
            Parent chatView = chatLoader.load();
            assertNotNull(chatView);
            assertTrue(chatLoader.getController() instanceof ChatController);
            ((ChatController) chatLoader.getController()).detener();
            return null;
        });
    }

    private static FXMLLoader crearLoader(String recurso, FabricaControladores fabrica) {
        FXMLLoader loader = new FXMLLoader(FabricaControladoresTest.class.getResource(recurso));
        loader.setControllerFactory(fabrica::crear);
        return loader;
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
}
